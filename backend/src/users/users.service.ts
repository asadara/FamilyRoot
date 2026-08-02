import {
  ConflictException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { DataSource, In, IsNull, Not, Repository } from 'typeorm';
import { ChangeLogEntity } from '../changes/change-log.entity';
import { UserPersonClaimEntity } from '../claims/user-person-claim.entity';
import { GoogleIdentityEntity } from '../auth/google-identity.entity';
import { RefreshSessionEntity } from '../auth/refresh-session.entity';
import { FamilySpaceEntity } from '../spaces/family-space.entity';
import { SpaceInvitationEntity } from '../spaces/space-invitation.entity';
import { SpaceMemberEntity } from '../spaces/space-member.entity';
import { AccountLifecycleAuditEntity } from './account-lifecycle-audit.entity';
import { UserEntity } from './user.entity';
import { UserNotificationEntity } from '../notifications/user-notification.entity';
import { HistoryAccessRequestEntity } from '../changes/history-access-request.entity';

@Injectable()
export class UsersService {
  constructor(
    @InjectRepository(UserEntity)
    private readonly usersRepo: Repository<UserEntity>,
    private readonly dataSource: DataSource,
  ) {}

  findById(userId: string) {
    return this.usersRepo.findOneBy({ userId });
  }

  findActiveById(userId: string) {
    return this.usersRepo.findOneBy({ userId, accountStatus: 'ACTIVE' });
  }

  async accountDeletionImpact(userId: string) {
    return this.dataSource.transaction((manager) =>
      this.accountDeletionImpactWithManager(userId, manager),
    );
  }

  async deleteAccount(userId: string, confirmation: 'HAPUS AKUN') {
    if (confirmation !== 'HAPUS AKUN') {
      throw new ConflictException('Account deletion confirmation is invalid');
    }
    return this.dataSource.transaction(async (manager) => {
      const impact = await this.accountDeletionImpactWithManager(
        userId,
        manager,
      );
      if (!impact.canDeleteAccount) {
        throw new ConflictException({
          message:
            'Transfer ownership of every Family Space before deleting the account',
          impact,
        });
      }

      const user = await manager.findOneBy(UserEntity, {
        userId,
        accountStatus: 'ACTIVE',
      });
      if (!user) throw new NotFoundException('Active account not found');
      const memberships = await manager.findBy(SpaceMemberEntity, { userId });
      const now = new Date();

      await manager.update(
        SpaceInvitationEntity,
        {
          createdBy: userId,
          acceptedAt: IsNull(),
          revokedAt: IsNull(),
        },
        { revokedAt: now },
      );
      if (memberships.length) {
        await manager.save(
          memberships.map((membership) =>
            manager.create(ChangeLogEntity, {
              spaceId: membership.spaceId,
              actorUserId: userId,
              entityType: 'MEMBERSHIP',
              entityId: membership.memberId,
              operation: 'DELETE',
              note: `Account deletion removed membership with role ${membership.role}`,
              beforeJson: JSON.stringify(membership),
            }),
          ),
        );
        await manager.remove(memberships);
      }
      await manager.delete(UserPersonClaimEntity, { userId });
      await manager.delete(GoogleIdentityEntity, { userId });
      await manager.delete(RefreshSessionEntity, { userId });
      await manager.delete(UserNotificationEntity, { userId });
      await manager.delete(HistoryAccessRequestEntity, { userId });

      user.email = null;
      user.phone = null;
      user.passwordHash = null;
      user.displayName = 'Kontributor terdahulu';
      user.accountStatus = 'DELETED';
      user.deletedAt = now;
      await manager.save(user);
      await manager.save(
        manager.create(AccountLifecycleAuditEntity, {
          userId,
          operation: 'DELETE',
          impactJson: JSON.stringify(impact),
        }),
      );
      return { userId, deleted: true };
    });
  }

  private async accountDeletionImpactWithManager(
    userId: string,
    manager: DataSource['manager'],
  ) {
    const user = await manager.findOneBy(UserEntity, {
      userId,
      accountStatus: 'ACTIVE',
    });
    if (!user) throw new NotFoundException('Active account not found');
    const [memberships, claimCount, activeSessionCount, activeInvitationCount] =
      await Promise.all([
        manager.findBy(SpaceMemberEntity, { userId }),
        manager.countBy(UserPersonClaimEntity, { userId }),
        manager.countBy(RefreshSessionEntity, { userId, revokedAt: IsNull() }),
        manager.countBy(SpaceInvitationEntity, {
          createdBy: userId,
          acceptedAt: IsNull(),
          revokedAt: IsNull(),
        }),
      ]);
    const spaces = memberships.length
      ? await manager.findBy(FamilySpaceEntity, {
          spaceId: In(memberships.map((membership) => membership.spaceId)),
          status: Not('DELETED'),
        })
      : [];
    const spacesById = new Map(spaces.map((space) => [space.spaceId, space]));
    const activeMemberships = memberships.filter((membership) =>
      spacesById.has(membership.spaceId),
    );
    const ownedMemberships = activeMemberships.filter(
      (membership) => membership.role === 'OWNER',
    );
    return {
      canDeleteAccount: ownedMemberships.length === 0,
      blockers:
        ownedMemberships.length > 0 ? ['TRANSFER_OWNERSHIP'] : ([] as string[]),
      membershipCount: activeMemberships.length,
      claimCount,
      activeSessionCount,
      activeInvitationCount,
      ownedSpaces: ownedMemberships.map((membership) => ({
        spaceId: membership.spaceId,
        name: spacesById.get(membership.spaceId)?.name ?? 'Silsilah',
      })),
      exportableSpaces: activeMemberships
        .filter(
          (membership) =>
            membership.role === 'OWNER' || membership.role === 'ADMIN',
        )
        .map((membership) => ({
          spaceId: membership.spaceId,
          name: spacesById.get(membership.spaceId)?.name ?? 'Silsilah',
          role: membership.role,
        })),
    };
  }
}
