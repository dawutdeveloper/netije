import {
  IsArray,
  IsEnum,
  IsInt,
  IsNumber,
  IsOptional,
  IsString,
  Max,
  Min,
} from 'class-validator';

export class TransactionDto {
  @IsOptional() @IsString() id?: string;
  @IsString() personId: string;
  @IsEnum(['RECEIVED', 'ISSUED']) type: 'RECEIVED' | 'ISSUED';
  @IsNumber() @Min(0) amount: number;
  @IsOptional() @IsString() currency?: string;
  @IsOptional() @IsString() purpose?: string;
  @IsOptional() @IsString() note?: string;
  @IsOptional() @IsArray() attachments?: string[];
  @IsInt() @Min(0) date: number;
  @IsInt() @Min(0) time: number;
}

export class ListFilters {
  @IsOptional() @IsString() personId?: string;
  @IsOptional() @IsInt() fromDate?: number;
  @IsOptional() @IsInt() toDate?: number;
}
