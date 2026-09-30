import { IsArray, ValidateNested } from 'class-validator';
import { Type } from 'class-transformer';
import { PersonDto } from '../persons/dto';
import { TransactionDto } from '../transactions/dto';

export class SyncPayload {
  @IsArray() @ValidateNested({ each: true }) @Type(() => PersonDto)
  persons: PersonDto[];
  @IsArray() @ValidateNested({ each: true }) @Type(() => TransactionDto)
  transactions: TransactionDto[];
}
