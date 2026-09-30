import { Injectable, UnauthorizedException } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { OAuth2Client } from 'google-auth-library';
import { ConfigService } from '@nestjs/config';
import { User } from './user.entity';
import { GoogleSignInDto } from './dto';

@Injectable()
export class AuthService {
  private client: OAuth2Client;
  constructor(
    @InjectRepository(User) private readonly users: Repository<User>,
    private readonly jwt: JwtService,
    private readonly config: ConfigService,
  ) {
    this.client = new OAuth2Client(this.config.get('GOOGLE_CLIENT_ID'));
  }

  async googleLogin({ idToken }: GoogleSignInDto) {
    let ticket;
    try {
      ticket = await this.client.verifyIdToken({
        idToken,
        audience: this.config.get('GOOGLE_CLIENT_ID'),
      });
    } catch {
      throw new UnauthorizedException('Invalid Google token');
    }
    const payload = ticket.getPayload();
    if (!payload?.email) throw new UnauthorizedException();
    let user = await this.users.findOne({ where: { email: payload.email } });
    if (!user) {
      user = this.users.create({
        email: payload.email,
        displayName: payload.name ?? payload.email,
        photoUrl: payload.picture,
        googleId: payload.sub,
      });
      user = await this.users.save(user);
    } else {
      if (
        (user.displayName !== (payload.name ?? user.displayName) || !user.photoUrl) &&
        payload.name
      ) {
        user.displayName = payload.name ?? user.displayName;
        user.photoUrl = payload.picture ?? user.photoUrl;
        user = await this.users.save(user);
      }
    }
    const accessToken = this.jwt.sign({ sub: user.id });
    return {
      user: {
        id: user.id,
        email: user.email,
        displayName: user.displayName,
        photoUrl: user.photoUrl,
        createdAt: user.createdAt.getTime(),
      },
      accessToken,
    };
  }
}
