import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { Transaction } from './transaction.entity';
import { TransactionsService } from './transactions.service';
import { TransactionsController } from './transactions.controller';
import { PersonsModule } from '../persons/persons.module';

@Module({
  imports: [TypeOrmModule.forFeature([Transaction]), PersonsModule],
  controllers: [TransactionsController],
  providers: [TransactionsService],
  exports: [TransactionsService, TypeOrmModule],
})
export class TransactionsModule {}
