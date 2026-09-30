import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AuthModule } from './modules/auth/auth.module';
import { PersonsModule } from './modules/persons/persons.module';
import { TransactionsModule } from './modules/transactions/transactions.module';
import { SyncModule } from './modules/sync/sync.module';
import { ReportsModule } from './modules/reports/reports.module';
import { User } from './modules/auth/user.entity';
import { Person } from './modules/persons/person.entity';
import { Transaction } from './modules/transactions/transaction.entity';

@Module({
  imports: [
    ConfigModule.forRoot({ isGlobal: true, envFilePath: '.env' }),
    TypeOrmModule.forRoot({
      type: 'postgres',
      host: process.env.DB_HOST ?? 'localhost',
      port: Number(process.env.DB_PORT ?? 5432),
      username: process.env.DB_USER ?? 'postgres',
      password: process.env.DB_PASS ?? 'postgres',
      database: process.env.DB_NAME ?? 'netije',
      entities: [User, Person, Transaction],
      synchronize: true,
      ssl: process.env.DB_SSL === 'true' ? { rejectUnauthorized: false } : undefined,
    }),
    AuthModule,
    PersonsModule,
    TransactionsModule,
    SyncModule,
    ReportsModule,
  ],
})
export class AppModule {}
