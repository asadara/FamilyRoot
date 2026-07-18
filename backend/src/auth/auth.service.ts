import {
  ConflictException,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { JwtService } from '@nestjs/jwt';
import { Repository } from 'typeorm';
import { compare, hash } from 'bcryptjs';
import { createHash, randomBytes, randomUUID } from 'node:crypto';
import { UserEntity } from '../users/user.entity';
import { RegisterDto } from './dto/register.dto';
import { RefreshSessionEntity } from './refresh-session.entity';

const REFRESH_TOKEN_TTL_MS = 30 * 24 * 60 * 60 * 1000;

type SessionResponse = {
  accessToken: string;
  refreshToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  refreshExpiresIn: number;
  user: { userId: string; email: string | null; displayName: string };
};

type SessionOutcome = { response: SessionResponse } | { error: string };

@Injectable()
export class AuthService {
  constructor(
    @InjectRepository(UserEntity)
    private readonly usersRepo: Repository<UserEntity>,
    private readonly jwtService: JwtService,
  ) {}

  async register(dto: RegisterDto) {
    const email = dto.email.trim().toLowerCase();
    const existing = await this.usersRepo.findOne({ where: { email } });
    if (existing) throw new ConflictException('Email is already registered');

    const user = await this.usersRepo.save(
      this.usersRepo.create({
        email,
        phone: null,
        displayName: dto.displayName.trim(),
        passwordHash: await hash(dto.password, 12),
      }),
    );
    return this.issueToken(user);
  }

  async login(emailInput: string, password: string) {
    const email = emailInput.trim().toLowerCase();
    const user = await this.usersRepo
      .createQueryBuilder('user')
      .addSelect('user.passwordHash')
      .where('user.email = :email', { email })
      .getOne();
    if (!user?.passwordHash || !(await compare(password, user.passwordHash))) {
      throw new UnauthorizedException('Email or password is incorrect');
    }
    return this.issueToken(user);
  }

  private refreshTokenHash(token: string) {
    return createHash('sha256').update(token).digest('hex');
  }

  private async issueAccessToken(user: UserEntity) {
    return this.jwtService.signAsync({
      sub: user.userId,
      email: user.email,
      displayName: user.displayName,
    });
  }

  private async createSessionResponse(
    manager: Repository<UserEntity>['manager'],
    user: UserEntity,
    familyId: string = randomUUID(),
  ): Promise<SessionResponse> {
    const refreshToken = randomBytes(48).toString('base64url');
    await manager.save(
      manager.create(RefreshSessionEntity, {
        userId: user.userId,
        familyId,
        tokenHash: this.refreshTokenHash(refreshToken),
        expiresAt: new Date(Date.now() + REFRESH_TOKEN_TTL_MS),
        revokedAt: null,
        replacedBySessionId: null,
      }),
    );
    return {
      accessToken: await this.issueAccessToken(user),
      refreshToken,
      tokenType: 'Bearer' as const,
      expiresIn: 3600,
      refreshExpiresIn: Math.floor(REFRESH_TOKEN_TTL_MS / 1000),
      user: {
        userId: user.userId,
        email: user.email,
        displayName: user.displayName,
      },
    };
  }

  private async issueToken(user: UserEntity) {
    return this.usersRepo.manager.transaction((manager) =>
      this.createSessionResponse(manager, user),
    );
  }

  async refresh(refreshToken: string) {
    const tokenHash = this.refreshTokenHash(refreshToken);
    const outcome: SessionOutcome = await this.usersRepo.manager.transaction(
      async (manager) => {
        const session = await manager
          .createQueryBuilder(RefreshSessionEntity, 'session')
          .addSelect('session.tokenHash')
          .where('session.tokenHash = :tokenHash', { tokenHash })
          .getOne();
        if (!session) return { error: 'Refresh token is invalid' };

        const now = new Date();
        if (session.revokedAt) {
          await manager
            .createQueryBuilder()
            .update(RefreshSessionEntity)
            .set({ revokedAt: now })
            .where('familyId = :familyId', { familyId: session.familyId })
            .andWhere('revokedAt IS NULL')
            .execute();
          return { error: 'Refresh token reuse detected' };
        }
        if (session.expiresAt <= now) {
          await manager.update(
            RefreshSessionEntity,
            { sessionId: session.sessionId },
            { revokedAt: now },
          );
          return { error: 'Refresh token has expired' };
        }

        const revokeResult = await manager
          .createQueryBuilder()
          .update(RefreshSessionEntity)
          .set({ revokedAt: now })
          .where('sessionId = :sessionId', { sessionId: session.sessionId })
          .andWhere('revokedAt IS NULL')
          .execute();
        if (revokeResult.affected !== 1) {
          await manager
            .createQueryBuilder()
            .update(RefreshSessionEntity)
            .set({ revokedAt: now })
            .where('familyId = :familyId', { familyId: session.familyId })
            .andWhere('revokedAt IS NULL')
            .execute();
          return { error: 'Refresh token reuse detected' };
        }

        const user = await manager.findOneBy(UserEntity, {
          userId: session.userId,
        });
        if (!user) return { error: 'Refresh token user no longer exists' };

        const response = await this.createSessionResponse(
          manager,
          user,
          session.familyId,
        );
        const replacement = await manager
          .createQueryBuilder(RefreshSessionEntity, 'session')
          .where('session.tokenHash = :tokenHash', {
            tokenHash: this.refreshTokenHash(response.refreshToken),
          })
          .getOneOrFail();
        await manager.update(
          RefreshSessionEntity,
          { sessionId: session.sessionId },
          { replacedBySessionId: replacement.sessionId },
        );
        return { response };
      },
    );
    if ('error' in outcome) {
      throw new UnauthorizedException(outcome.error);
    }
    return outcome.response;
  }

  async logout(refreshToken: string) {
    const tokenHash = this.refreshTokenHash(refreshToken);
    await this.usersRepo.manager.transaction(async (manager) => {
      const session = await manager
        .createQueryBuilder(RefreshSessionEntity, 'session')
        .addSelect('session.tokenHash')
        .where('session.tokenHash = :tokenHash', { tokenHash })
        .getOne();
      if (!session) return;
      await manager
        .createQueryBuilder()
        .update(RefreshSessionEntity)
        .set({ revokedAt: new Date() })
        .where('familyId = :familyId', { familyId: session.familyId })
        .andWhere('revokedAt IS NULL')
        .execute();
    });
  }
}
