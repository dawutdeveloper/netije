import { Controller, Post, Body } from '@nestjs/common';
import { AuthService } from './auth.service';
import { GoogleSignInDto } from './dto';
import { Public } from '../../common/guards/jwt-auth.guard';

@Controller('auth')
export class AuthController {
  constructor(private readonly auth: AuthService) {}

  @Public()
  @Post('google')
  async google(@Body() body: GoogleSignInDto) {
    return this.auth.googleLogin(body);
  }

  @Post('signout')
  signOut() {
    return { ok: true };
  }
}
