import {
  BadRequestException,
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { Person } from './person.entity';
import { PersonDto } from './dto';
import { User } from '../auth/user.entity';
import { v4 as uuid } from 'uuid';

@Injectable()
export class PersonsService {
  constructor(
    @InjectRepository(Person) private readonly repo: Repository<Person>,
  ) {}

  findAll(owner: User) {
    return this.repo.find({
      where: { ownerId: owner.id },
      order: { name: 'ASC' },
    });
  }

  async findOne(owner: User, id: string) {
    const p = await this.repo.findOne({ where: { id, ownerId: owner.id } });
    if (!p) throw new NotFoundException();
    return p;
  }

  async create(owner: User, dto: PersonDto) {
    const id = dto.id && dto.id.length > 0 ? dto.id : uuid();
    const exists = await this.repo.findOne({ where: { id } });
    if (exists && exists.ownerId !== owner.id) throw new ForbiddenException();
    if (exists && exists.ownerId === owner.id) {
      return this.update(owner, id, dto);
    }
    const p = this.repo.create({
      id,
      ownerId: owner.id,
      name: dto.name,
      phones: dto.phones ?? [],
      emails: dto.emails ?? [],
      city: dto.city,
      address: dto.address,
      avatarUrl: dto.avatarUrl,
      contactLookupUri: dto.contactLookupUri,
      isImportant: dto.isImportant ?? false,
      status: dto.status ?? 'ACTIVE',
    });
    return this.repo.save(p);
  }

  async update(owner: User, id: string, dto: PersonDto) {
    const p = await this.findOne(owner, id);
    Object.assign(p, {
      name: dto.name ?? p.name,
      phones: dto.phones ?? p.phones,
      emails: dto.emails ?? p.emails,
      city: dto.city ?? p.city,
      address: dto.address ?? p.address,
      avatarUrl: dto.avatarUrl ?? p.avatarUrl,
      contactLookupUri: dto.contactLookupUri ?? p.contactLookupUri,
      isImportant: dto.isImportant ?? p.isImportant,
      status: dto.status ?? p.status,
    });
    return this.repo.save(p);
  }

  async remove(owner: User, id: string) {
    const p = await this.findOne(owner, id);
    await this.repo.remove(p);
    return { ok: true };
  }

  async upsertMany(owner: User, dtos: PersonDto[]) {
    const results: Person[] = [];
    for (const d of dtos) {
      if (!d.name) continue;
      const id = d.id && d.id.length ? d.id : uuid();
      let existing = await this.repo.findOne({ where: { id } });
      if (existing && existing.ownerId !== owner.id) continue;
      if (existing) {
        Object.assign(existing, {
          name: d.name ?? existing.name,
          phones: d.phones ?? existing.phones,
          emails: d.emails ?? existing.emails,
          city: d.city ?? existing.city,
          address: d.address ?? existing.address,
          avatarUrl: d.avatarUrl ?? existing.avatarUrl,
          contactLookupUri: d.contactLookupUri ?? existing.contactLookupUri,
          isImportant: d.isImportant ?? existing.isImportant,
          status: d.status ?? existing.status,
        });
        results.push(await this.repo.save(existing));
      } else {
        results.push(
          await this.repo.save(
            this.repo.create({
              id,
              ownerId: owner.id,
              name: d.name,
              phones: d.phones ?? [],
              emails: d.emails ?? [],
              city: d.city,
              address: d.address,
              avatarUrl: d.avatarUrl,
              contactLookupUri: d.contactLookupUri,
              isImportant: d.isImportant ?? false,
              status: d.status ?? 'ACTIVE',
            }),
          ),
        );
      }
    }
    return results;
  }
}
