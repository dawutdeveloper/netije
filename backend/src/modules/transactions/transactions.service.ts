import { Injectable, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Between, Repository } from 'typeorm';
import { Transaction } from './transaction.entity';
import { ListFilters, TransactionDto } from './dto';
import { User } from '../auth/user.entity';
import { PersonsService } from '../persons/persons.service';
import { v4 as uuid } from 'uuid';

@Injectable()
export class TransactionsService {
  constructor(
    @InjectRepository(Transaction) private readonly repo: Repository<Transaction>,
    private readonly persons: PersonsService,
  ) {}

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
    const t = this.repo.create({
      id,
      ownerId: owner.id,
      personId: dto.personId,
      type: dto.type,
      amount: dto.amount,
      currency: dto.currency ?? 'TMT',
      purpose: dto.purpose,
      note: dto.note,
      attachments: dto.attachments ?? [],
      date: dto.date,
      time: dto.time,
    });
    return this.repo.save(t);
  }

  async update(owner: User, id: string, dto: TransactionDto) {
    const t = await this.one(owner, id);
    Object.assign(t, {
      personId: dto.personId ?? t.personId,
      type: dto.type ?? t.type,
      amount: dto.amount ?? t.amount,
      currency: dto.currency ?? t.currency,
      purpose: dto.purpose ?? t.purpose,
      note: dto.note ?? t.note,
      attachments: dto.attachments ?? t.attachments,
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
    for (const d of dtos) {
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
      if (existing) {
        Object.assign(existing, {
          personId: d.personId ?? existing.personId,
          type: d.type ?? existing.type,
          amount: d.amount ?? existing.amount,
          currency: d.currency ?? existing.currency,
          purpose: d.purpose ?? existing.purpose,
          note: d.note ?? existing.note,
          attachments: d.attachments ?? existing.attachments,
          date: d.date ?? existing.date,
          time: d.time ?? existing.time,
        });
        results.push(await this.repo.save(existing));
      } else {
        results.push(
          await this.repo.save(
            this.repo.create({
              id,
              ownerId: owner.id,
              personId: d.personId,
              type: d.type,
              amount: d.amount,
              currency: d.currency ?? 'TMT',
              purpose: d.purpose,
              note: d.note,
              attachments: d.attachments ?? [],
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
