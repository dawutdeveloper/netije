require('reflect-metadata');
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { normalizeTags } = require('../dist/modules/transactions/tags');
const { TransactionsService } = require('../dist/modules/transactions/transactions.service');
const { TransactionDto } = require('../dist/modules/transactions/dto');
const { plainToInstance } = require('class-transformer');
const { validate } = require('class-validator');

test('tags normalize hashes, Unicode, duplicates and separators', () => {
  assert.deepEqual(normalizeTags(['#Work, rent; ##work #Ýol #Работа']), ['#work', '#rent', '#ýol', '#работа']);
  assert.deepEqual(normalizeTags(['#cafe\u0301']), ['#café']);
  assert.deepEqual(normalizeTags([]), []);
});

test('saved tags survive old clients and can be explicitly cleared during sync', async () => {
  const current = { id: 'transaction', ownerId: 'owner', personId: 'person', amount: 300, type: 'RECEIVED',
    note: 'Rent', tags: ['#rent'], attachments: [], changes: [], date: 1, time: 1 };
  const repo = {
    findOne: async () => current, create: (row) => row, save: async (row) => row,
    manager: { query: async () => [{ id: 'person' }] },
  };
  const service = new TransactionsService(repo, { findOne: async () => ({ id: 'person' }) });
  await service.update({ id: 'owner' }, current.id, { ...current, tags: undefined });
  assert.deepEqual(current.tags, ['#rent']);
  await service.update({ id: 'owner' }, current.id, { ...current, tags: ['#Work', 'work'] });
  assert.deepEqual(current.tags, ['#work']);
  await service.upsertMany({ id: 'owner' }, [{ ...current, tags: undefined }]);
  assert.deepEqual(current.tags, ['#work']);
  await service.upsertMany({ id: 'owner' }, [{ ...current, tags: [] }]);
  assert.deepEqual(current.tags, []);
  assert.equal(current.amount, 300);
  assert.equal(current.note, 'Rent');
});

test('API rejects non-string tags instead of corrupting saved entries', async () => {
  const dto = plainToInstance(TransactionDto, { personId: 'person', type: 'RECEIVED', amount: 300, date: 1, time: 1, tags: [42] });
  assert.equal((await validate(dto)).some((error) => error.property === 'tags'), true);
});
