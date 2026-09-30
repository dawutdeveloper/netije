import { Body, Controller, Post } from '@nestjs/common';
import { CurrentUser } from '../../common/guards/jwt-auth.guard';
import { User } from '../auth/user.entity';
import { PersonsService } from '../persons/persons.service';
import { TransactionsService } from '../transactions/transactions.service';
import { SyncPayload } from './dto';

@Controller('sync')
export class SyncController {
  constructor(
    private readonly persons: PersonsService,
    private readonly transactions: TransactionsService,
  ) {}

  @Post()
  async sync(@CurrentUser() u: User, @Body() body: SyncPayload) {
    const serverPersons = await this.persons.findAll(u);
    const serverTransactions = await this.transactions.list(u, {});
    const incomingPersonIds = new Set(body.persons.map((p) => p.id).filter(Boolean));
    const incomingTxIds = new Set(body.transactions.map((t) => t.id).filter(Boolean));
    const mergedPersons = new Map<string, any>();
    serverPersons.forEach((p) => mergedPersons.set(p.id, p));
    const upsertedPersons = await this.persons.upsertMany(u, body.persons);
    upsertedPersons.forEach((p) => mergedPersons.set(p.id, p));
    const mergedTransactions = new Map<string, any>();
    serverTransactions.forEach((t) => mergedTransactions.set(t.id, t));
    const upsertedTxs = await this.transactions.upsertMany(u, body.transactions);
    upsertedTxs.forEach((t) => mergedTransactions.set(t.id, t));
    return {
      persons: Array.from(mergedPersons.values()),
      transactions: Array.from(mergedTransactions.values()),
    };
  }
}
