import { Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { JwtModule } from '@nestjs/jwt';
import { TypeOrmModule } from '@nestjs/typeorm';
import { UserEntity } from '../users/user.entity';
import { AuthController } from './auth.controller';
import { AuthService } from './auth.service';
import { RefreshSessionEntity } from './refresh-session.entity';

@Module({
  imports: [
    TypeOrmModule.forFeature([UserEntity, RefreshSessionEntity]),
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
  providers: [AuthService],
  exports: [JwtModule],
})
export class AuthModule {}
