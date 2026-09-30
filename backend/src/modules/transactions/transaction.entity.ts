import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  ManyToOne,
  PrimaryColumn,
  UpdateDateColumn,
} from 'typeorm';
import { Person } from '../persons/person.entity';
import { User } from '../auth/user.entity';

@Entity('transactions')
@Index(['ownerId', 'personId'])
@Index(['ownerId', 'date'])
export class Transaction {
  @PrimaryColumn() id: string;

  @ManyToOne(() => Person, (p) => p.transactions, { onDelete: 'CASCADE' })
  person: Person;
  @Index() @Column() personId: string;

  @Index() @Column() ownerId: string;
  @ManyToOne(() => User, { onDelete: 'CASCADE' }) owner: User;

  @Column({ type: 'varchar', length: 16 }) type: 'RECEIVED' | 'ISSUED';
  @Column({ type: 'double precision' }) amount: number;
  @Column({ default: 'TMT' }) currency: string;

  @Column({ nullable: true }) purpose?: string;
  @Column({ nullable: true, type: 'text' }) note?: string;
  @Column({ type: 'text', array: true, default: '{}' }) attachments: string[];

  @Column({ type: 'bigint' }) date: number;
  @Column({ type: 'bigint' }) time: number;

  @CreateDateColumn() createdAt: Date;
  @UpdateDateColumn() updatedAt: Date;
}
