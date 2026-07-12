import {
  BadRequestException,
  CanActivate,
  ExecutionContext,
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { InjectRepository } from '@nestjs/typeorm';
import { isUUID } from 'class-validator';
import { Repository } from 'typeorm';
import { AuthUser } from '../auth/auth-user.interface';
import { UserPersonClaimEntity } from '../claims/user-person-claim.entity';
import { SpaceMemberEntity } from '../spaces/space-member.entity';
import { SPACE_ROLES_KEY, SpaceRole } from './space-roles.decorator';

@Injectable()
export class SpaceMemberGuard implements CanActivate {
  constructor(
    private readonly reflector: Reflector,
    @InjectRepository(SpaceMemberEntity)
    private readonly membersRepo: Repository<SpaceMemberEntity>,
    @InjectRepository(UserPersonClaimEntity)
    private readonly claimsRepo: Repository<UserPersonClaimEntity>,
  ) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const allowed = this.reflector.getAllAndOverride<SpaceRole[]>(
      SPACE_ROLES_KEY,
      [context.getHandler(), context.getClass()],
    );
    if (!allowed) return true;

    const request = context.switchToHttp().getRequest<{
      user: AuthUser;
      body?: Record<string, unknown>;
      query?: Record<string, unknown>;
      params?: Record<string, unknown>;
      spaceMembership?: SpaceMemberEntity;
    }>();
    let spaceId =
      request.body?.spaceId ??
      request.query?.spaceId ??
      request.params?.spaceId;
    const claimId = request.body?.claimId;
    if (!spaceId && typeof claimId === 'string') {
      const claim = await this.claimsRepo.findOne({
        where: { claimId },
        select: ['spaceId'],
      });
      if (!claim) throw new NotFoundException('Claim not found');
      spaceId = claim.spaceId;
    }
    if (typeof spaceId !== 'string' || !isUUID(spaceId)) {
      throw new BadRequestException('spaceId must be a UUID');
    }

    const member = await this.membersRepo.findOneBy({
      spaceId,
      userId: request.user.userId,
    });
    if (!member)
      throw new ForbiddenException('User is not a member of this space');
    if (!allowed.includes(member.role)) {
      throw new ForbiddenException(
        `Role ${member.role} is not allowed for this operation`,
      );
    }
    request.spaceMembership = member;
    return true;
  }
}
