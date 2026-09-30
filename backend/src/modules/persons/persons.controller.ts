import {
  Body,
  Controller,
  Delete,
  Get,
  Param,
  Post,
  Put,
} from '@nestjs/common';
import { PersonsService } from './persons.service';
import { PersonDto } from './dto';
import { CurrentUser } from '../../common/guards/jwt-auth.guard';
import { User } from '../auth/user.entity';

@Controller('persons')
export class PersonsController {
  constructor(private readonly service: PersonsService) {}

  @Get()
  list(@CurrentUser() u: User) {
    return this.service.findAll(u);
  }

  @Get(':id')
  one(@CurrentUser() u: User, @Param('id') id: string) {
    return this.service.findOne(u, id);
  }

  @Post()
  create(@CurrentUser() u: User, @Body() dto: PersonDto) {
    return this.service.create(u, dto);
  }

  @Put(':id')
  update(@CurrentUser() u: User, @Param('id') id: string, @Body() dto: PersonDto) {
    return this.service.update(u, id, dto);
  }

  @Delete(':id')
  remove(@CurrentUser() u: User, @Param('id') id: string) {
    return this.service.remove(u, id);
  }
}
