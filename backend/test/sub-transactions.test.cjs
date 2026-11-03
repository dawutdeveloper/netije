require('reflect-metadata');
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { TransactionsService } = require('../dist/modules/transactions/transactions.service');

function fixture() {
  const rows = new Map();
  const repo = {
    findOne: async ({ where }) => {
      const row = rows.get(where.id);
      return row && (!where.ownerId || row.ownerId === where.ownerId) ? row : null;
    },
    create: (row) => ({ ...row }),
    count: async ({ where }) => [...rows.values()].filter((row) => row.parentTransactionId === where.parentTransactionId && row.ownerId === where.ownerId).length,
    save: async (row) => { rows.set(row.id, row); return row; },
    manager: { query: async () => [{ id: 'person' }] },
  };
  return { rows, service: new TransactionsService(repo, { findOne: async () => ({ id: 'person' }) }) };
}
const owner = { id: 'owner' };
const original = { id: 'original', personId: 'person', amount: 100, type: 'RECEIVED', date: 10, time: 10 };
const payment = { id: 'payment', parentTransactionId: 'original', personId: 'person', amount: 50, type: 'ISSUED', note: 'Groceries', date: 20, time: 20 };

test('saving a payment preserves the original and its own reason', async () => {
  const { service, rows } = fixture();
  await service.create(owner, original);
  await service.create(owner, payment);
  assert.equal(rows.get('original').amount, 100);
  assert.equal(rows.get('original').type, 'RECEIVED');
  assert.equal(rows.get('payment').parentTransactionId, 'original');
  assert.equal(rows.get('payment').note, 'Groceries');
  assert.equal([...rows.values()].reduce((sum, row) => sum + (row.type === 'RECEIVED' ? row.amount : -row.amount), 0), 50);
});

test('offline sync saves parents first and resending the payload does not duplicate entries', async () => {
  const { service, rows } = fixture();
  await service.upsertMany(owner, [payment, original]);
  await service.upsertMany(owner, [payment, original]);
  assert.equal(rows.size, 2);
  assert.equal(rows.get('payment').parentTransactionId, 'original');
});

test('cross-owner, cross-person and nested links are rejected', async () => {
  const { service } = fixture();
  await service.create(owner, original);
  await assert.rejects(service.create({ id: 'other' }, payment));
  await assert.rejects(service.create(owner, { ...payment, personId: 'other-person' }));
  await assert.rejects(service.create(owner, { ...payment, parentTransactionId: 'payment' }));
  await service.create(owner, payment);
  await assert.rejects(service.update(owner, 'original', { ...original, personId: 'other-person' }));
  await assert.rejects(service.create(owner, { ...payment, id: 'nested', parentTransactionId: 'payment' }));
});

test('parent relationship uses one nullable column and cascades deletion to children', async () => {
  const { DataSource } = require('typeorm');
  const { Transaction } = require('../dist/modules/transactions/transaction.entity');
  const { Person } = require('../dist/modules/persons/person.entity');
  const { User } = require('../dist/modules/auth/user.entity');
  const source = new DataSource({ type: 'postgres', entities: [Transaction, Person, User] });
  await source.buildMetadatas();
  const metadata = source.getMetadata(Transaction);
  assert.equal(metadata.columns.filter((column) => column.databaseName === 'parentTransactionId').length, 1);
  assert.equal(metadata.findColumnWithPropertyName('parentTransactionId').isNullable, true);
  const key = metadata.foreignKeys.find((key) => key.columns.some((column) => column.databaseName === 'parentTransactionId'));
  assert.equal(key.onDelete, 'CASCADE');
});
