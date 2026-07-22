import {
  BadRequestException,
  ConflictException,
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { randomBytes } from 'crypto';
import { DataSource, Repository } from 'typeorm';
import { UserEntity } from '../users/user.entity';
import { FamilySpaceEntity } from './family-space.entity';
import { SpaceInvitationEntity } from './space-invitation.entity';
import { SpaceMemberEntity } from './space-member.entity';
import { ChangeLogEntity } from '../changes/change-log.entity';
import { databaseErrorMessage } from '../common/database-error';

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
        manager.create(FamilySpaceEntity, { name: name.trim(), createdBy }),
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
    const spaces = await this.spacesRepo.findByIds(
      memberships.map((item) => item.spaceId),
    );
    const byId = new Map(spaces.map((space) => [space.spaceId, space]));
    return memberships.flatMap((membership) => {
      const space = byId.get(membership.spaceId);
      return space ? [{ ...space, role: membership.role }] : [];
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

    for (let attempt = 0; attempt < 3; attempt += 1) {
      try {
        return await this.invitationsRepo.manager.transaction(
          async (manager) => {
            const invite = await manager.save(
              manager.create(SpaceInvitationEntity, {
                spaceId,
                role,
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
                afterJson: JSON.stringify({
                  inviteId: invite.inviteId,
                  role: invite.role,
                  expiresAt: invite.expiresAt,
                }),
              }),
            );
            return {
              inviteId: invite.inviteId,
              token: invite.token,
              role: invite.role,
              spaceId: invite.spaceId,
              spaceName: space.name,
              expiresAt: invite.expiresAt,
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

  async previewInvitation(token: string) {
    const invite = await this.invitationsRepo.findOneBy({ token });
    if (!invite) throw new NotFoundException('Invitation not found');
    const space = await this.spacesRepo.findOneBy({ spaceId: invite.spaceId });
    if (!space) throw new NotFoundException('Family Space not found');
    this.assertInvitationUsable(invite);
    return {
      spaceId: invite.spaceId,
      spaceName: space.name,
      role: invite.role,
      expiresAt: invite.expiresAt,
    };
  }

  async acceptInvitation(token: string, actorUserId: string) {
    const invite = await this.invitationsRepo.findOneBy({ token });
    if (!invite) throw new NotFoundException('Invitation not found');
    this.assertInvitationUsable(invite);

    const [space, user, existing] = await Promise.all([
      this.spacesRepo.findOneBy({ spaceId: invite.spaceId }),
      this.usersRepo.findOneBy({ userId: actorUserId }),
      this.membersRepo.findOneBy({
        spaceId: invite.spaceId,
        userId: actorUserId,
      }),
    ]);
    if (!space) throw new NotFoundException('Family Space not found');
    if (!user) throw new NotFoundException('User not found');
    if (existing)
      throw new ConflictException('User is already a member of this space');

    try {
      return await this.dataSource.transaction(async (manager) => {
        const membership = await manager.save(
          manager.create(SpaceMemberEntity, {
            spaceId: invite.spaceId,
            userId: actorUserId,
            role: invite.role,
          }),
        );
        invite.acceptedBy = actorUserId;
        invite.acceptedAt = new Date();
        await manager.save(invite);
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
}
