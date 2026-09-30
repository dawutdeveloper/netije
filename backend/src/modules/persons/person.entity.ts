import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  ManyToOne,
  OneToMany,
  PrimaryColumn,
  UpdateDateColumn,
} from 'typeorm';
import { User } from '../auth/user.entity';
import { Transaction } from '../transactions/transaction.entity';

@Entity('persons')
@Index(['ownerId', 'status'])
export class Person {
  @PrimaryColumn() id: string;

  @ManyToOne(() => User, (u) => u.persons, { onDelete: 'CASCADE' })
  owner: User;
  @Index() @Column() ownerId: string;

  @Column() name: string;

  @Column({ type: 'text', array: true, default: '{}' }) phones: string[];
  @Column({ type: 'text', array: true, default: '{}' }) emails: string[];

  @Column({ nullable: true }) city?: string;
  @Column({ nullable: true }) address?: string;
  @Column({ nullable: true }) avatarUrl?: string;
  @Column({ nullable: true }) contactLookupUri?: string;

  @Column({ default: false }) isImportant: boolean;
  @Column({ default: 'ACTIVE' }) status: 'ACTIVE' | 'ARCHIVED' | 'SETTLED';

  @OneToMany(() => Transaction, (t) => t.person, { onDelete: 'CASCADE' })
  transactions: Transaction[];

  @CreateDateColumn() createdAt: Date;
  @UpdateDateColumn() updatedAt: Date;
}
