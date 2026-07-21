import { Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { JwtModule } from '@nestjs/jwt';
import { TypeOrmModule } from '@nestjs/typeorm';
import { UserEntity } from '../users/user.entity';
import { AuthController } from './auth.controller';
import { AuthService } from './auth.service';
import { RefreshSessionEntity } from './refresh-session.entity';
import {
  GOOGLE_ID_TOKEN_VERIFIER,
  GoogleIdTokenVerifier,
} from './google-id-token-verifier';
import { GoogleIdentityEntity } from './google-identity.entity';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      UserEntity,
      RefreshSessionEntity,
      GoogleIdentityEntity,
    ]),
    JwtModule.registerAsync({
      inject: [ConfigService],
      useFactory: (config: ConfigService) => {
        const configured = config.get<string>('JWT_SECRET');
        if (config.get<string>('NODE_ENV') === 'production' && !configured) {
          throw new Error('JWT_SECRET is required in production');
        }
        return {
          secret: configured ?? 'development-only-change-me',
          signOptions: { expiresIn: '1h' },
        };
      },
    }),
  ],
  controllers: [AuthController],
  providers: [
    AuthService,
    GoogleIdTokenVerifier,
    {
      provide: GOOGLE_ID_TOKEN_VERIFIER,
      useExisting: GoogleIdTokenVerifier,
    },
  ],
  exports: [JwtModule],
})
export class AuthModule {}
