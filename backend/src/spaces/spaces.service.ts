import {
  BadRequestException,
  ConflictException,
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { randomBytes } from 'crypto';
import { DataSource, In, IsNull, MoreThan, Not, Repository } from 'typeorm';
import { UserEntity } from '../users/user.entity';
import { FamilySpaceEntity } from './family-space.entity';
import { SpaceInvitationEntity } from './space-invitation.entity';
import { SpaceMemberEntity } from './space-member.entity';
import { ChangeLogEntity } from '../changes/change-log.entity';
import { databaseErrorMessage } from '../common/database-error';
import { InvitationStatus } from './dto/list-invitations-query.dto';
import { PersonEntity } from '../persons/person.entity';
import { RelationshipEntity } from '../persons/relationship.entity';
import { UserPersonClaimEntity } from '../claims/user-person-claim.entity';
import { MediaItemEntity } from '../archive/media-item.entity';
import { FactSourceEntity } from '../archive/fact-source.entity';
import { EditProposalEntity } from '../archive/edit-proposal.entity';

@Injectable()
export class SpacesService {
  constructor(
    @InjectRepository(FamilySpaceEntity)
    private readonly spacesRepo: Repository<FamilySpaceEntity>,
    @InjectRepository(SpaceMemberEntity)
    private readonly membersRepo: Repository<SpaceMemberEntity>,
    @InjectRepository(SpaceInvitationEntity)
    private readonly invitationsRepo: Repository<SpaceInvitationEntity>,
    @InjectRepository(UserEntity)
    private readonly usersRepo: Repository<UserEntity>,
    private readonly dataSource: DataSource,
  ) {}

  async create(name: string, createdBy: string) {
    return this.dataSource.transaction(async (manager) => {
      const space = await manager.save(
        manager.create(FamilySpaceEntity, {
          name: name.trim(),
          createdBy,
          status: 'ACTIVE',
          archivedAt: null,
          deletedAt: null,
        }),
      );
      await manager.save(
        manager.create(SpaceMemberEntity, {
          spaceId: space.spaceId,
          userId: createdBy,
          role: 'OWNER',
        }),
      );
      await manager.save(
        manager.create(ChangeLogEntity, {
          spaceId: space.spaceId,
          actorUserId: createdBy,
          entityType: 'SPACE',
          entityId: space.spaceId,
          operation: 'CREATE',
          note: 'Create Family Space and OWNER membership',
          afterJson: JSON.stringify(space),
        }),
      );
      return space;
    });
  }

  async findForUser(userId: string) {
    const memberships = await this.membersRepo.find({
      where: { userId },
      order: { joinedAt: 'ASC' },
    });
    if (!memberships.length) return [];
    const spaces = await this.spacesRepo.findBy({
      spaceId: In(memberships.map((item) => item.spaceId)),
      status: Not('DELETED'),
    });
    const byId = new Map(spaces.map((space) => [space.spaceId, space]));
    return memberships.flatMap((membership) => {
      const space = byId.get(membership.spaceId);
      return space ? [{ ...space, role: membership.role }] : [];
    });
  }

  async lifecycleImpact(spaceId: string) {
    const space = await this.spacesRepo.findOneBy({ spaceId });
    if (!space || space.status === 'DELETED') {
      throw new NotFoundException('Family Space not found');
    }
    const [
      personCount,
      relationshipCount,
      memberCount,
      claimCount,
      mediaCount,
      sourceCount,
      pendingProposalCount,
      activeInvitationCount,
    ] = await Promise.all([
      this.dataSource.manager.countBy(PersonEntity, {
        spaceId,
        isDeleted: false,
      }),
      this.dataSource.manager.countBy(RelationshipEntity, { spaceId }),
      this.dataSource.manager.countBy(SpaceMemberEntity, { spaceId }),
      this.dataSource.manager.countBy(UserPersonClaimEntity, { spaceId }),
      this.dataSource.manager.countBy(MediaItemEntity, { spaceId }),
      this.dataSource.manager.countBy(FactSourceEntity, { spaceId }),
      this.dataSource.manager.countBy(EditProposalEntity, {
        spaceId,
        status: 'PENDING',
      }),
      this.dataSource.manager.countBy(SpaceInvitationEntity, {
        spaceId,
        acceptedAt: IsNull(),
        revokedAt: IsNull(),
        expiresAt: MoreThan(new Date()),
      }),
    ]);
    return {
      spaceId,
      name: space.name,
      status: space.status,
      canArchive: space.status === 'ACTIVE',
      canRestore: space.status === 'ARCHIVED',
      canDelete: space.status === 'ARCHIVED',
      personCount,
      relationshipCount,
      memberCount,
      claimCount,
      mediaCount,
      sourceCount,
      pendingProposalCount,
      activeInvitationCount,
    };
  }

  async archiveSpace(spaceId: string, actorUserId: string) {
    return this.dataSource.transaction(async (manager) => {
      const space = await manager.findOneBy(FamilySpaceEntity, { spaceId });
      if (!space || space.status === 'DELETED') {
        throw new NotFoundException('Family Space not found');
      }
      if (space.status === 'ARCHIVED') return space;
      const before = { ...space };
      space.status = 'ARCHIVED';
      space.archivedAt = new Date();
      const saved = await manager.save(space);
      await manager.update(
        SpaceInvitationEntity,
        {
          spaceId,
          acceptedAt: IsNull(),
          revokedAt: IsNull(),
        },
        { revokedAt: space.archivedAt },
      );
      await manager.save(
        manager.create(ChangeLogEntity, {
          spaceId,
          actorUserId,
          entityType: 'SPACE',
          entityId: spaceId,
          operation: 'UPDATE',
          note: 'Archive Family Space',
          beforeJson: JSON.stringify(before),
          afterJson: JSON.stringify(saved),
        }),
      );
      return saved;
    });
  }

  async restoreSpace(spaceId: string, actorUserId: string) {
    return this.dataSource.transaction(async (manager) => {
      const space = await manager.findOneBy(FamilySpaceEntity, { spaceId });
      if (!space || space.status === 'DELETED') {
        throw new NotFoundException('Family Space not found');
      }
      if (space.status === 'ACTIVE') return space;
      const before = { ...space };
      space.status = 'ACTIVE';
      space.archivedAt = null;
      const saved = await manager.save(space);
      await manager.save(
        manager.create(ChangeLogEntity, {
          spaceId,
          actorUserId,
          entityType: 'SPACE',
          entityId: spaceId,
          operation: 'UPDATE',
          note: 'Restore archived Family Space',
          beforeJson: JSON.stringify(before),
          afterJson: JSON.stringify(saved),
        }),
      );
      return saved;
    });
  }

  async deleteSpace(
    spaceId: string,
    actorUserId: string,
    confirmation: string,
    acknowledgeExport: true,
  ) {
    return this.dataSource.transaction(async (manager) => {
      const space = await manager.findOneBy(FamilySpaceEntity, { spaceId });
      if (!space || space.status === 'DELETED') {
        throw new NotFoundException('Family Space not found');
      }
      if (space.status !== 'ARCHIVED') {
        throw new ConflictException(
          'Archive the Family Space before deleting it',
        );
      }
      if (!acknowledgeExport || confirmation.trim() !== space.name) {
        throw new BadRequestException(
          'Confirm the exact Family Space name and acknowledge export',
        );
      }
      const before = { ...space };
      const deletedAt = new Date();
      space.status = 'DELETED';
      space.deletedAt = deletedAt;
      const saved = await manager.save(space);
      await manager.update(
        SpaceInvitationEntity,
        {
          spaceId,
          acceptedAt: IsNull(),
          revokedAt: IsNull(),
        },
        { revokedAt: deletedAt },
      );
      await manager.save(
        manager.create(ChangeLogEntity, {
          spaceId,
          actorUserId,
          entityType: 'SPACE',
          entityId: spaceId,
          operation: 'DELETE',
          note: 'Soft delete archived Family Space',
          beforeJson: JSON.stringify(before),
          afterJson: JSON.stringify(saved),
        }),
      );
      return { spaceId, deleted: true, deletedAt };
    });
  }

  async listMembers(spaceId: string, actorUserId: string) {
    const memberships = await this.membersRepo.find({
      where: { spaceId },
      order: { joinedAt: 'ASC' },
    });
    const users = memberships.length
      ? await this.usersRepo.findBy({
          userId: In(memberships.map((membership) => membership.userId)),
        })
      : [];
    const usersById = new Map(users.map((user) => [user.userId, user]));
    return memberships.map((membership) => ({
      memberId: membership.memberId,
      userId: membership.userId,
      displayName:
        usersById.get(membership.userId)?.displayName ?? 'Anggota keluarga',
      role: membership.role,
      joinedAt: membership.joinedAt,
      isCurrentUser: membership.userId === actorUserId,
    }));
  }

  async updateMemberRole(
    spaceId: string,
    memberId: string,
    role: 'ADMIN' | 'EDITOR' | 'VIEWER',
    actorUserId: string,
  ) {
    return this.dataSource.transaction(async (manager) => {
      const [actor, target] = await Promise.all([
        manager.findOneBy(SpaceMemberEntity, {
          spaceId,
          userId: actorUserId,
        }),
        manager.findOneBy(SpaceMemberEntity, { spaceId, memberId }),
      ]);
      this.assertMembershipManager(actor, target, role);
      if (target.userId === actorUserId) {
        throw new BadRequestException(
          'Use leave or ownership transfer for your own membership',
        );
      }
      if (target.role === role) return this.memberResult(target);

      const before = { ...target };
      target.role = role;
      const saved = await manager.save(target);
      await manager.save(
        manager.create(ChangeLogEntity, {
          spaceId,
          actorUserId,
          entityType: 'MEMBERSHIP',
          entityId: saved.memberId,
          operation: 'UPDATE',
          note: `Change membership role from ${before.role} to ${role}`,
          beforeJson: JSON.stringify(before),
          afterJson: JSON.stringify(saved),
        }),
      );
      return this.memberResult(saved);
    });
  }

  async removeMember(spaceId: string, memberId: string, actorUserId: string) {
    return this.dataSource.transaction(async (manager) => {
      const [actor, target] = await Promise.all([
        manager.findOneBy(SpaceMemberEntity, {
          spaceId,
          userId: actorUserId,
        }),
        manager.findOneBy(SpaceMemberEntity, { spaceId, memberId }),
      ]);
      this.assertMembershipManager(actor, target);
      if (target.userId === actorUserId) {
        throw new BadRequestException(
          'Use the leave action for your own membership',
        );
      }

      const removed = { ...target };
      await manager.remove(target);
      await manager.save(
        manager.create(ChangeLogEntity, {
          spaceId,
          actorUserId,
          entityType: 'MEMBERSHIP',
          entityId: removed.memberId,
          operation: 'DELETE',
          note: `Remove member with role ${removed.role}`,
          beforeJson: JSON.stringify(removed),
        }),
      );
      return { spaceId, memberId: removed.memberId, removed: true };
    });
  }

  async transferOwnership(
    spaceId: string,
    targetMemberId: string,
    actorUserId: string,
  ) {
    return this.dataSource.transaction(async (manager) => {
      const [actor, target, ownerCount] = await Promise.all([
        manager.findOneBy(SpaceMemberEntity, {
          spaceId,
          userId: actorUserId,
        }),
        manager.findOneBy(SpaceMemberEntity, {
          spaceId,
          memberId: targetMemberId,
        }),
        manager.countBy(SpaceMemberEntity, { spaceId, role: 'OWNER' }),
      ]);
      if (!actor || actor.role !== 'OWNER') {
        throw new ForbiddenException(
          'Only the current OWNER can transfer ownership',
        );
      }
      if (!target) throw new NotFoundException('Target member not found');
      if (target.userId === actorUserId) {
        throw new BadRequestException(
          'Ownership is already assigned to this member',
        );
      }
      if (ownerCount !== 1) {
        throw new ConflictException(
          'Family Space ownership is inconsistent and requires review',
        );
      }

      const actorBefore = { ...actor };
      const targetBefore = { ...target };
      actor.role = 'ADMIN';
      await manager.save(actor);
      target.role = 'OWNER';
      const newOwner = await manager.save(target);

      await manager.save([
        manager.create(ChangeLogEntity, {
          spaceId,
          actorUserId,
          entityType: 'MEMBERSHIP',
          entityId: actor.memberId,
          operation: 'UPDATE',
          note: 'Transfer ownership: previous OWNER becomes ADMIN',
          beforeJson: JSON.stringify(actorBefore),
          afterJson: JSON.stringify(actor),
        }),
        manager.create(ChangeLogEntity, {
          spaceId,
          actorUserId,
          entityType: 'MEMBERSHIP',
          entityId: newOwner.memberId,
          operation: 'UPDATE',
          note: `Transfer ownership from member ${actor.memberId}`,
          beforeJson: JSON.stringify(targetBefore),
          afterJson: JSON.stringify(newOwner),
        }),
      ]);
      return {
        spaceId,
        previousOwner: this.memberResult(actor),
        owner: this.memberResult(newOwner),
      };
    });
  }

  async leaveSpace(spaceId: string, actorUserId: string) {
    return this.dataSource.transaction(async (manager) => {
      const membership = await manager.findOneBy(SpaceMemberEntity, {
        spaceId,
        userId: actorUserId,
      });
      if (!membership) {
        throw new NotFoundException('Membership not found');
      }
      if (membership.role === 'OWNER') {
        throw new ConflictException(
          'Transfer ownership before leaving this Family Space',
        );
      }

      const removed = { ...membership };
      await manager.remove(membership);
      await manager.save(
        manager.create(ChangeLogEntity, {
          spaceId,
          actorUserId,
          entityType: 'MEMBERSHIP',
          entityId: removed.memberId,
          operation: 'DELETE',
          note: `Member left Family Space with role ${removed.role}`,
          beforeJson: JSON.stringify(removed),
        }),
      );
      return { spaceId, left: true };
    });
  }

  async addMember(
    spaceId: string,
    userId: string,
    role: 'ADMIN' | 'EDITOR' | 'VIEWER',
    actorUserId: string,
  ) {
    const [space, user, actor] = await Promise.all([
      this.spacesRepo.findOneBy({ spaceId }),
      this.usersRepo.findOneBy({ userId }),
      this.membersRepo.findOneBy({ spaceId, userId: actorUserId }),
    ]);
    if (!space) throw new NotFoundException('Family Space not found');
    if (!user) throw new NotFoundException('User not found');
    if (!actor)
      throw new ForbiddenException('Actor is not a member of this space');
    if (actor.role === 'ADMIN' && role === 'ADMIN') {
      throw new ForbiddenException('Only OWNER can add an ADMIN');
    }

    try {
      return await this.membersRepo.manager.transaction(async (manager) => {
        const saved = await manager.save(
          manager.create(SpaceMemberEntity, { spaceId, userId, role }),
        );
        await manager.save(
          manager.create(ChangeLogEntity, {
            spaceId,
            actorUserId,
            entityType: 'MEMBERSHIP',
            entityId: saved.memberId,
            operation: 'CREATE',
            note: `Add member with role ${role}`,
            afterJson: JSON.stringify(saved),
          }),
        );
        return saved;
      });
    } catch (error: unknown) {
      const message = databaseErrorMessage(error);
      if (message.includes('UNIQUE') || message.includes('constraint failed')) {
        throw new ConflictException('Member already exists in this space');
      }
      throw error;
    }
  }

  async createInvitation(
    spaceId: string,
    role: 'ADMIN' | 'EDITOR' | 'VIEWER',
    actorUserId: string,
    expiresInDays = 7,
    targetEmail?: string,
  ) {
    const [space, actor] = await Promise.all([
      this.spacesRepo.findOneBy({ spaceId }),
      this.membersRepo.findOneBy({ spaceId, userId: actorUserId }),
    ]);
    if (!space) throw new NotFoundException('Family Space not found');
    if (!actor)
      throw new ForbiddenException('Actor is not a member of this space');
    if (actor.role === 'ADMIN' && role === 'ADMIN') {
      throw new ForbiddenException('Only OWNER can invite an ADMIN');
    }

    const expiresAt = new Date();
    expiresAt.setDate(expiresAt.getDate() + expiresInDays);
    const roleCode = { VIEWER: 'V', EDITOR: 'K', ADMIN: 'P' }[role];
    const normalizedTargetEmail = targetEmail
      ? this.normalizeEmail(targetEmail)
      : null;

    for (let attempt = 0; attempt < 3; attempt += 1) {
      try {
        return await this.invitationsRepo.manager.transaction(
          async (manager) => {
            const invite = await manager.save(
              manager.create(SpaceInvitationEntity, {
                spaceId,
                role,
                targetEmail: normalizedTargetEmail,
                createdBy: actorUserId,
                token: `FR-${roleCode}-${randomBytes(18).toString('base64url')}`,
                expiresAt,
              }),
            );
            await manager.save(
              manager.create(ChangeLogEntity, {
                spaceId,
                actorUserId,
                entityType: 'INVITATION',
                entityId: invite.inviteId,
                operation: 'CREATE',
                note: `Create invitation for role ${role}`,
                afterJson: JSON.stringify(this.invitationAuditSnapshot(invite)),
              }),
            );
            return {
              inviteId: invite.inviteId,
              token: invite.token,
              role: invite.role,
              spaceId: invite.spaceId,
              spaceName: space.name,
              expiresAt: invite.expiresAt,
              maskedTargetEmail: this.maskEmail(invite.targetEmail),
            };
          },
        );
      } catch (error: unknown) {
        const message = databaseErrorMessage(error);
        if (!message.includes('UNIQUE') && !message.includes('constraint')) {
          throw error;
        }
      }
    }
    throw new ConflictException('Could not create a unique invitation token');
  }

  async listInvitations(
    spaceId: string,
    actorUserId: string,
    status?: InvitationStatus,
  ) {
    const actor = await this.membersRepo.findOneBy({
      spaceId,
      userId: actorUserId,
    });
    this.assertInvitationManager(actor);

    const invitations = await this.invitationsRepo.find({
      where: { spaceId },
      order: { createdAt: 'DESC' },
      take: 100,
    });
    const visibleInvitations =
      actor.role === 'ADMIN'
        ? invitations.filter((invitation) => invitation.role !== 'ADMIN')
        : invitations;
    const userIds = [
      ...new Set(
        visibleInvitations.flatMap((invitation) =>
          [invitation.createdBy, invitation.acceptedBy].filter(
            (userId): userId is string => Boolean(userId),
          ),
        ),
      ),
    ];
    const users = userIds.length
      ? await this.usersRepo.findBy({ userId: In(userIds) })
      : [];
    const usersById = new Map(users.map((user) => [user.userId, user]));

    return visibleInvitations
      .map((invitation) => {
        const invitationStatus = this.invitationStatus(invitation);
        return {
          inviteId: invitation.inviteId,
          role: invitation.role,
          status: invitationStatus,
          createdBy: invitation.createdBy,
          createdByName:
            usersById.get(invitation.createdBy)?.displayName ??
            'Anggota keluarga',
          acceptedBy: invitation.acceptedBy ?? null,
          acceptedByName: invitation.acceptedBy
            ? (usersById.get(invitation.acceptedBy)?.displayName ??
              'Anggota keluarga')
            : null,
          createdAt: invitation.createdAt,
          expiresAt: invitation.expiresAt,
          acceptedAt: invitation.acceptedAt ?? null,
          revokedAt: invitation.revokedAt ?? null,
          maskedTargetEmail: this.maskEmail(invitation.targetEmail),
        };
      })
      .filter((invitation) => !status || invitation.status === status);
  }

  async revokeInvitation(
    spaceId: string,
    inviteId: string,
    actorUserId: string,
  ) {
    return this.dataSource.transaction(async (manager) => {
      const [actor, invitation] = await Promise.all([
        manager.findOneBy(SpaceMemberEntity, {
          spaceId,
          userId: actorUserId,
        }),
        manager.findOneBy(SpaceInvitationEntity, { spaceId, inviteId }),
      ]);
      if (!invitation) throw new NotFoundException('Invitation not found');
      this.assertInvitationManager(actor, invitation);
      if (invitation.revokedAt) {
        return {
          spaceId,
          inviteId,
          status: 'REVOKED' as const,
          revokedAt: invitation.revokedAt,
        };
      }
      this.assertInvitationUsable(invitation);

      const revokedAt = new Date();
      const result = await manager.update(
        SpaceInvitationEntity,
        {
          spaceId,
          inviteId,
          acceptedAt: IsNull(),
          revokedAt: IsNull(),
          expiresAt: MoreThan(revokedAt),
        },
        { revokedAt },
      );
      if (result.affected !== 1) {
        const current = await manager.findOneBy(SpaceInvitationEntity, {
          spaceId,
          inviteId,
        });
        if (!current) throw new NotFoundException('Invitation not found');
        this.assertInvitationUsable(current);
        throw new ConflictException('Invitation status changed; try again');
      }

      const before = this.invitationAuditSnapshot(invitation);
      invitation.revokedAt = revokedAt;
      await manager.save(
        manager.create(ChangeLogEntity, {
          spaceId,
          actorUserId,
          entityType: 'INVITATION',
          entityId: invitation.inviteId,
          operation: 'DELETE',
          note: `Revoke invitation for role ${invitation.role}`,
          beforeJson: JSON.stringify(before),
          afterJson: JSON.stringify(this.invitationAuditSnapshot(invitation)),
        }),
      );
      return {
        spaceId,
        inviteId,
        status: 'REVOKED' as const,
        revokedAt,
      };
    });
  }

  async previewInvitation(token: string, actorUserId: string) {
    const [invite, user] = await Promise.all([
      this.invitationsRepo.findOneBy({ token }),
      this.usersRepo.findOneBy({ userId: actorUserId }),
    ]);
    if (!invite) throw new NotFoundException('Invitation not found');
    if (!user) throw new NotFoundException('User not found');
    const space = await this.spacesRepo.findOneBy({ spaceId: invite.spaceId });
    if (!space || space.status === 'DELETED') {
      throw new NotFoundException('Family Space not found');
    }
    if (space.status === 'ARCHIVED') {
      throw new ConflictException('Archived Family Space cannot be joined');
    }
    this.assertInvitationUsable(invite);
    this.assertInvitationTarget(invite, user);
    return {
      spaceId: invite.spaceId,
      spaceName: space.name,
      role: invite.role,
      expiresAt: invite.expiresAt,
      maskedTargetEmail: this.maskEmail(invite.targetEmail),
    };
  }

  async acceptInvitation(token: string, actorUserId: string) {
    try {
      return await this.dataSource.transaction(async (manager) => {
        const invite = await manager.findOneBy(SpaceInvitationEntity, {
          token,
        });
        if (!invite) throw new NotFoundException('Invitation not found');
        this.assertInvitationUsable(invite);

        const [space, user, existing] = await Promise.all([
          manager.findOneBy(FamilySpaceEntity, { spaceId: invite.spaceId }),
          manager.findOneBy(UserEntity, { userId: actorUserId }),
          manager.findOneBy(SpaceMemberEntity, {
            spaceId: invite.spaceId,
            userId: actorUserId,
          }),
        ]);
        if (!space || space.status === 'DELETED') {
          throw new NotFoundException('Family Space not found');
        }
        if (space.status === 'ARCHIVED') {
          throw new ConflictException('Archived Family Space cannot be joined');
        }
        if (!user) throw new NotFoundException('User not found');
        this.assertInvitationTarget(invite, user);
        if (existing) {
          throw new ConflictException('User is already a member of this space');
        }

        const acceptedAt = new Date();
        const acceptance = await manager.update(
          SpaceInvitationEntity,
          {
            inviteId: invite.inviteId,
            acceptedAt: IsNull(),
            revokedAt: IsNull(),
            expiresAt: MoreThan(acceptedAt),
          },
          { acceptedBy: actorUserId, acceptedAt },
        );
        if (acceptance.affected !== 1) {
          const current = await manager.findOneBy(SpaceInvitationEntity, {
            inviteId: invite.inviteId,
          });
          if (!current) throw new NotFoundException('Invitation not found');
          this.assertInvitationUsable(current);
          throw new ConflictException('Invitation status changed; try again');
        }

        const membership = await manager.save(
          manager.create(SpaceMemberEntity, {
            spaceId: invite.spaceId,
            userId: actorUserId,
            role: invite.role,
          }),
        );
        await manager.save(
          manager.create(ChangeLogEntity, {
            spaceId: invite.spaceId,
            actorUserId,
            entityType: 'MEMBERSHIP',
            entityId: membership.memberId,
            operation: 'CREATE',
            note: `Accept invitation with role ${invite.role}`,
            afterJson: JSON.stringify(membership),
          }),
        );
        return {
          ...space,
          role: membership.role,
          memberId: membership.memberId,
          joinedAt: membership.joinedAt,
        };
      });
    } catch (error: unknown) {
      const message = databaseErrorMessage(error);
      if (message.includes('UNIQUE') || message.includes('constraint failed')) {
        throw new ConflictException('User is already a member of this space');
      }
      throw error;
    }
  }

  private assertInvitationUsable(invite: SpaceInvitationEntity) {
    if (invite.revokedAt)
      throw new BadRequestException('Invitation is revoked');
    if (invite.acceptedAt)
      throw new ConflictException('Invitation has already been accepted');
    if (invite.expiresAt.getTime() <= Date.now()) {
      throw new BadRequestException('Invitation has expired');
    }
  }

  private assertInvitationManager(
    actor: SpaceMemberEntity | null,
    invitation?: SpaceInvitationEntity,
  ): asserts actor is SpaceMemberEntity {
    if (!actor)
      throw new ForbiddenException('Actor is not a member of this space');
    if (actor.role !== 'OWNER' && actor.role !== 'ADMIN') {
      throw new ForbiddenException('Invitation manager access is required');
    }
    if (actor.role === 'ADMIN' && invitation?.role === 'ADMIN') {
      throw new ForbiddenException(
        'ADMIN can only manage EDITOR and VIEWER invitations',
      );
    }
  }

  private invitationStatus(
    invitation: SpaceInvitationEntity,
  ): InvitationStatus {
    if (invitation.revokedAt) return 'REVOKED';
    if (invitation.acceptedAt) return 'ACCEPTED';
    if (invitation.expiresAt.getTime() <= Date.now()) return 'EXPIRED';
    return 'ACTIVE';
  }

  private invitationAuditSnapshot(invitation: SpaceInvitationEntity) {
    return {
      inviteId: invitation.inviteId,
      role: invitation.role,
      maskedTargetEmail: this.maskEmail(invitation.targetEmail),
      createdBy: invitation.createdBy,
      createdAt: invitation.createdAt,
      expiresAt: invitation.expiresAt,
      acceptedBy: invitation.acceptedBy ?? null,
      acceptedAt: invitation.acceptedAt ?? null,
      revokedAt: invitation.revokedAt ?? null,
    };
  }

  private assertInvitationTarget(
    invitation: SpaceInvitationEntity,
    user: UserEntity,
  ) {
    if (!invitation.targetEmail) return;
    if (
      !user.email ||
      this.normalizeEmail(user.email) !== invitation.targetEmail
    ) {
      throw new ForbiddenException(
        'Invitation is not available for this account',
      );
    }
  }

  private normalizeEmail(email: string) {
    return email.trim().toLowerCase();
  }

  private maskEmail(email: string | null) {
    if (!email) return null;
    const [local, domain] = email.split('@');
    return `${local.slice(0, 1)}***@${domain}`;
  }

  private assertMembershipManager(
    actor: SpaceMemberEntity | null,
    target: SpaceMemberEntity | null,
    requestedRole?: 'ADMIN' | 'EDITOR' | 'VIEWER',
  ): asserts target is SpaceMemberEntity {
    if (!actor)
      throw new ForbiddenException('Actor is not a member of this space');
    if (!target) throw new NotFoundException('Target member not found');
    if (target.role === 'OWNER') {
      throw new ForbiddenException(
        'OWNER membership can only change through ownership transfer',
      );
    }
    if (
      actor.role === 'ADMIN' &&
      (target.role === 'ADMIN' || requestedRole === 'ADMIN')
    ) {
      throw new ForbiddenException(
        'ADMIN can only manage EDITOR and VIEWER memberships',
      );
    }
    if (actor.role !== 'OWNER' && actor.role !== 'ADMIN') {
      throw new ForbiddenException('Membership manager access is required');
    }
  }

  private memberResult(member: SpaceMemberEntity) {
    return {
      memberId: member.memberId,
      userId: member.userId,
      role: member.role,
      joinedAt: member.joinedAt,
    };
  }
}
