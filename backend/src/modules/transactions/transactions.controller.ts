import {
  Body,
  Controller,
  Delete,
  Get,
  Param,
  Post,
  Put,
  Query,
} from '@nestjs/common';
import { TransactionsService } from './transactions.service';
import { ListFilters, TransactionDto } from './dto';
import { CurrentUser } from '../../common/guards/jwt-auth.guard';
import { User } from '../auth/user.entity';

@Controller('transactions')
export class TransactionsController {
  constructor(private readonly service: TransactionsService) {}

  @Get()
  list(@CurrentUser() u: User, @Query() f: ListFilters) {
    return this.service.list(u, f);
  }

  @Get(':id')
  one(@CurrentUser() u: User, @Param('id') id: string) {
    return this.service.one(u, id);
  }

  @Post()
  create(@CurrentUser() u: User, @Body() dto: TransactionDto) {
    return this.service.create(u, dto);
  }

  @Put(':id')
  update(
    @CurrentUser() u: User,
    @Param('id') id: string,
    @Body() dto: TransactionDto,
  ) {
    return this.service.update(u, id, dto);
  }

  @Delete(':id')
  remove(@CurrentUser() u: User, @Param('id') id: string) {
    return this.service.remove(u, id);
  }
}
