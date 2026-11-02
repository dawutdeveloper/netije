require('reflect-metadata');
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { TransactionsService } = require('../dist/modules/transactions/transactions.service');

function fixture() {
  const current = {
    id: 'transaction', ownerId: 'owner', personId: 'person', type: 'RECEIVED',
    amount: 100, currency: 'TMT', attachments: [], changes: [], date: 1, time: 1,
  };
  const repo = {
    findOne: async () => current,
    save: async (value) => value,
    create: (value) => value,
    manager: { query: async () => [{ id: 'person' }] },
  };
  return { current, service: new TransactionsService(repo, { findOne: async () => ({ id: 'person' }) }) };
}

test('updates from older clients record signed increases and decreases', async () => {
  const { current, service } = fixture();
  await service.update({ id: 'owner' }, current.id, { ...current, changes: undefined, amount: 140 });
  await service.update({ id: 'owner' }, current.id, { ...current, changes: undefined, amount: 80 });
  assert.deepEqual(current.changes.map((c) => c.amount - c.previousAmount), [40, -60]);
  assert.equal(current.amount, 80);
});

test('resending client history through sync does not duplicate changes', async () => {
  const { current, service } = fixture();
  const change = { id: 'change', previousAmount: 100, amount: 130,
    previousType: 'RECEIVED', type: 'RECEIVED', changedAt: 10 };
  const dto = { ...current, amount: 130, currency: 'USD', changes: [change] };
  await service.upsertMany({ id: 'owner' }, [dto]);
  await service.upsertMany({ id: 'owner' }, [dto]);
  assert.deepEqual(current.changes, [change]);
  assert.equal(current.currency, 'TMT');
});

test('metadata edits and old clients preserve existing history', async () => {
  const { current, service } = fixture();
  await service.update({ id: 'owner' }, current.id, { ...current, changes: undefined, amount: 120 });
  const previous = [...current.changes];
  await service.update({ id: 'owner' }, current.id, { ...current, changes: undefined, note: 'Updated' });
  assert.deepEqual(current.changes, previous);
  assert.equal(current.note, 'Updated');
});

test('transaction status round-trips through editing and sync without changing amounts or people', async () => {
  const { current, service } = fixture();
  for (const status of ['ARCHIVED', 'SETTLED', 'ACTIVE']) {
    await service.update({ id: 'owner' }, current.id, { ...current, status });
    assert.equal(current.status, status);
    assert.equal(current.amount, 100);
    assert.equal(current.personId, 'person');
    assert.deepEqual(current.changes, []);
    await service.upsertMany({ id: 'owner' }, [{ ...current, status }]);
    assert.equal(current.status, status);
  }
  current.status = 'ARCHIVED';
  await service.update({ id: 'owner' }, current.id, { ...current, status: undefined });
  assert.equal(current.status, 'ARCHIVED');
});
