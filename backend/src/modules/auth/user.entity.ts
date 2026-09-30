import {
  Column,
  CreateDateColumn,
  Entity,
  OneToMany,
  PrimaryGeneratedColumn,
  UpdateDateColumn,
} from 'typeorm';
import { Person } from '../persons/person.entity';

@Entity('users')
export class User {
  @PrimaryGeneratedColumn('uuid') id: string;

  @Column({ unique: true }) email: string;

  @Column() displayName: string;

  @Column({ nullable: true }) googleId?: string;

  @Column({ nullable: true }) photoUrl?: string;

  @OneToMany(() => Person, (p) => p.owner, { onDelete: 'CASCADE' })
  persons: Person[];

  @CreateDateColumn() createdAt: Date;
  @UpdateDateColumn() updatedAt: Date;
}
