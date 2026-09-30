import {
  IsArray,
  IsBoolean,
  IsEnum,
  IsOptional,
  IsString,
  MaxLength,
} from 'class-validator';

export class PersonDto {
  @IsOptional() @IsString() id?: string;
  @IsString() @MaxLength(120) name: string;
  @IsOptional() @IsArray() phones?: string[];
  @IsOptional() @IsArray() emails?: string[];
  @IsOptional() @IsString() city?: string;
  @IsOptional() @IsString() address?: string;
  @IsOptional() @IsString() avatarUrl?: string;
  @IsOptional() @IsString() contactLookupUri?: string;
  @IsOptional() @IsBoolean() isImportant?: boolean;
  @IsOptional()
  @IsEnum(['ACTIVE', 'ARCHIVED', 'SETTLED'])
  status?: 'ACTIVE' | 'ARCHIVED' | 'SETTLED';
}
