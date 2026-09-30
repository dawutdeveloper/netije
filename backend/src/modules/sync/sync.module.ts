import { Module } from '@nestjs/common';
import { SyncController } from './sync.controller';
import { PersonsModule } from '../persons/persons.module';
import { TransactionsModule } from '../transactions/transactions.module';

@Module({
  imports: [PersonsModule, TransactionsModule],
  controllers: [SyncController],
})
export class SyncModule {}
