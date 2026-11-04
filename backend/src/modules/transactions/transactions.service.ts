import { BadRequestException, Injectable, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Between, Repository } from 'typeorm';
import { Transaction } from './transaction.entity';
import { ListFilters, TransactionDto } from './dto';
import { User } from '../auth/user.entity';
import { PersonsService } from '../persons/persons.service';
import { v4 as uuid } from 'uuid';
import { normalizeTags } from './tags';

@Injectable()
export class TransactionsService {
  constructor(
    @InjectRepository(Transaction) private readonly repo: Repository<Transaction>,
    private readonly persons: PersonsService,
  ) {}

  private async validateParent(owner: User, id: string, personId: string, parentId?: string) {
    if (!parentId) return;
    if (id === parentId) throw new BadRequestException('A transaction cannot be its own parent');
    const parent = await this.one(owner, parentId);
    if (parent.parentTransactionId || parent.personId !== personId) {
      throw new BadRequestException('Invalid original transaction');
    }
  }

  private mergeChanges(current: Transaction, dto: TransactionDto) {
    const changes = new Map((current.changes ?? []).map((change) => [change.id, change]));
    for (const change of dto.changes ?? []) changes.set(change.id, change);
    if (dto.changes == null && (current.amount !== dto.amount || current.type !== dto.type)) {
      const change = {
        id: uuid(), previousAmount: current.amount, amount: dto.amount,
        previousType: current.type, type: dto.type,
      status: dto.status ?? 'ACTIVE', changedAt: Date.now(),
      };
      changes.set(change.id, change);
    }
    return [...changes.values()].sort((a, b) => a.changedAt - b.changedAt);
  }

  async list(owner: User, f: ListFilters) {
    const where: any = { ownerId: owner.id };
    if (f.personId) where.personId = f.personId;
    if (f.fromDate && f.toDate) where.date = Between(f.fromDate, f.toDate);
    return this.repo.find({ where, order: { date: 'DESC', time: 'DESC' } });
  }

  async one(owner: User, id: string) {
    const t = await this.repo.findOne({ where: { id, ownerId: owner.id } });
    if (!t) throw new NotFoundException();
    return t;
  }

  async create(owner: User, dto: TransactionDto) {
    await this.persons.findOne(owner, dto.personId);
    const id = dto.id && dto.id.length ? dto.id : uuid();
    const exists = await this.repo.findOne({ where: { id } });
    if (exists) return this.update(owner, id, dto);
    await this.validateParent(owner, id, dto.personId, dto.parentTransactionId);
    const t = this.repo.create({
      id,
      parentTransactionId: dto.parentTransactionId,
      ownerId: owner.id,
      personId: dto.personId,
      type: dto.type,
      status: dto.status ?? 'ACTIVE',
      amount: dto.amount,
      currency: 'TMT',
      purpose: dto.purpose,
      note: dto.note,
      attachments: dto.attachments ?? [],
      tags: normalizeTags(dto.tags),
      changes: dto.changes ?? [],
      date: dto.date,
      time: dto.time,
    });
    return this.repo.save(t);
  }

  async update(owner: User, id: string, dto: TransactionDto) {
    const t = await this.one(owner, id);
    await this.validateParent(owner, id, dto.personId ?? t.personId, dto.parentTransactionId ?? t.parentTransactionId);
    if (dto.personId && dto.personId !== t.personId && await this.repo.count({ where: { parentTransactionId: id, ownerId: owner.id } })) {
      throw new BadRequestException('An original transaction with linked payments cannot change person');
    }
    Object.assign(t, {
      parentTransactionId: dto.parentTransactionId ?? t.parentTransactionId,
      personId: dto.personId ?? t.personId,
      type: dto.type ?? t.type,
      status: dto.status ?? t.status,
      amount: dto.amount ?? t.amount,
      currency: 'TMT',
      purpose: dto.purpose ?? t.purpose,
      note: dto.note ?? t.note,
      attachments: dto.attachments ?? t.attachments,
      tags: dto.tags == null ? (t.tags ?? []) : normalizeTags(dto.tags),
      changes: this.mergeChanges(t, dto),
      date: dto.date ?? t.date,
      time: dto.time ?? t.time,
    });
    return this.repo.save(t);
  }

  async remove(owner: User, id: string) {
    const t = await this.one(owner, id);
    await this.repo.remove(t);
    return { ok: true };
  }

  async upsertMany(owner: User, dtos: TransactionDto[]) {
    const results: Transaction[] = [];
    // Newly created parents must be saved before their offline sub-transactions.
    for (const d of [...dtos].sort((a, b) => Number(!!a.parentTransactionId) - Number(!!b.parentTransactionId))) {
      if (!d.personId || !d.type || !(d.amount > 0)) continue;
      const personOk = await this.repo.manager
        .query('SELECT id FROM persons WHERE id = $1 AND "ownerId" = $2 LIMIT 1', [
          d.personId,
          owner.id,
        ])
        .then((rows) => rows.length > 0)
        .catch(async () => {
          try {
            await this.persons.findOne(owner, d.personId);
            return true;
          } catch {
            return false;
          }
        });
      if (!personOk) continue;
      const id = d.id && d.id.length ? d.id : uuid();
      let existing = await this.repo.findOne({ where: { id } });
      if (existing && existing.ownerId !== owner.id) continue;
      try {
        await this.validateParent(owner, id, d.personId, d.parentTransactionId ?? existing?.parentTransactionId);
      } catch (e) {
        if (e instanceof BadRequestException || e instanceof NotFoundException) continue;
        throw e;
      }
      if (existing) {
        if (d.personId !== existing.personId && await this.repo.count({ where: { parentTransactionId: id, ownerId: owner.id } })) continue;
        Object.assign(existing, {
          parentTransactionId: d.parentTransactionId ?? existing.parentTransactionId,
          personId: d.personId ?? existing.personId,
          type: d.type ?? existing.type,
          status: d.status ?? existing.status,
          amount: d.amount ?? existing.amount,
          currency: 'TMT',
          purpose: d.purpose ?? existing.purpose,
          note: d.note ?? existing.note,
          attachments: d.attachments ?? existing.attachments,
          tags: d.tags == null ? (existing.tags ?? []) : normalizeTags(d.tags),
          changes: this.mergeChanges(existing, d),
          date: d.date ?? existing.date,
          time: d.time ?? existing.time,
        });
        results.push(await this.repo.save(existing));
      } else {
        results.push(
          await this.repo.save(
            this.repo.create({
              id,
              parentTransactionId: d.parentTransactionId,
              ownerId: owner.id,
              personId: d.personId,
              type: d.type,
              status: d.status ?? 'ACTIVE',
              amount: d.amount,
              currency: 'TMT',
              purpose: d.purpose,
              note: d.note,
              attachments: d.attachments ?? [],
              tags: normalizeTags(d.tags),
              changes: d.changes ?? [],
              date: d.date,
              time: d.time,
            }),
          ),
        );
      }
    }
    return results;
  }
}
