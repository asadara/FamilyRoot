import {
  ConflictException,
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { DataSource, Repository } from 'typeorm';
import { UserEntity } from '../users/user.entity';
import { FamilySpaceEntity } from './family-space.entity';
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
}
