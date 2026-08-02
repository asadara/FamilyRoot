import {
  BadRequestException,
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { In, Repository } from 'typeorm';
import { isUUID } from 'class-validator';
import { ChangeLogEntity } from './change-log.entity';
import { UserEntity } from '../users/user.entity';
import { SpaceMemberEntity } from '../spaces/space-member.entity';
import { HistoryAccessRequestEntity } from './history-access-request.entity';

@Injectable()
export class ChangesService {
  constructor(
    @InjectRepository(ChangeLogEntity)
    private readonly changesRepo: Repository<ChangeLogEntity>,
    @InjectRepository(UserEntity)
    private readonly usersRepo: Repository<UserEntity>,
    @InjectRepository(SpaceMemberEntity)
    private readonly membersRepo: Repository<SpaceMemberEntity>,
    @InjectRepository(HistoryAccessRequestEntity)
    private readonly historyAccessRepo: Repository<HistoryAccessRequestEntity>,
  ) {}

  async findBySpace(spaceId: string, limit = 10) {
    if (!Number.isInteger(limit) || limit <= 0 || limit > 100) {
      throw new BadRequestException('limit must be between 1 and 100');
    }
    const boundedLimit = Math.min(limit, 10);

    const changes = await this.changesRepo.find({
      where: { spaceId },
      order: { createdAt: 'DESC', changeId: 'DESC' },
      take: boundedLimit,
      select: [
        'changeId',
        'createdAt',
        'actorUserId',
        'entityType',
        'operation',
        'note',
      ],
    });
    return this.withActorNames(changes);
  }

  async requestFullHistoryAccess(spaceId: string, userId: string) {
    const existing = await this.historyAccessRepo.findOneBy({
      spaceId,
      userId,
    });
    if (existing?.status === 'APPROVED' || existing?.status === 'PENDING') {
      return existing;
    }
    const request =
      existing ?? this.historyAccessRepo.create({ spaceId, userId });
    request.status = 'PENDING';
    request.reviewedByUserId = null;
    request.reviewedAt = null;
    const saved = await this.historyAccessRepo.save(request);
    await this.changesRepo.save(
      this.changesRepo.create({
        spaceId,
        actorUserId: userId,
        entityType: 'HISTORY_ACCESS',
        entityId: saved.requestId,
        operation: 'CREATE',
        note: 'Request full history access',
        afterJson: JSON.stringify(saved),
      }),
    );
    return saved;
  }

  async findMyHistoryAccessRequest(spaceId: string, userId: string) {
    return {
      request: await this.historyAccessRepo.findOneBy({ spaceId, userId }),
    };
  }

  async listHistoryAccessRequests(spaceId: string) {
    const requests = await this.historyAccessRepo.find({
      where: { spaceId },
      order: { createdAt: 'DESC' },
    });
    const users = requests.length
      ? await this.usersRepo.find({
          where: { userId: In(requests.map((request) => request.userId)) },
          select: ['userId', 'displayName'],
        })
      : [];
    const nameByUserId = new Map(
      users.map((user) => [user.userId, user.displayName]),
    );
    return requests.map((request) => ({
      ...request,
      userDisplayName: nameByUserId.get(request.userId) ?? 'Anggota keluarga',
    }));
  }

  async reviewHistoryAccessRequest(
    spaceId: string,
    requestId: string,
    approved: boolean,
    reviewerUserId: string,
  ) {
    const request = await this.historyAccessRepo.findOneBy({
      requestId,
      spaceId,
    });
    if (!request)
      throw new NotFoundException('History access request not found');
    const beforeJson = JSON.stringify(request);
    request.status = approved ? 'APPROVED' : 'REJECTED';
    request.reviewedByUserId = reviewerUserId;
    request.reviewedAt = new Date();
    const saved = await this.historyAccessRepo.save(request);
    await this.changesRepo.save(
      this.changesRepo.create({
        spaceId,
        actorUserId: reviewerUserId,
        entityType: 'HISTORY_ACCESS',
        entityId: saved.requestId,
        operation: 'UPDATE',
        note: approved
          ? 'Approve full history access'
          : 'Reject full history access',
        beforeJson,
        afterJson: JSON.stringify(saved),
      }),
    );
    return saved;
  }

  async findFullHistory(
    spaceId: string,
    userId: string,
    limit = 50,
    before?: string,
  ) {
    if (!Number.isInteger(limit) || limit < 1 || limit > 50) {
      throw new BadRequestException('limit must be between 1 and 50');
    }
    const membership = await this.membersRepo.findOneBy({ spaceId, userId });
    const privileged =
      membership?.role === 'OWNER' || membership?.role === 'ADMIN';
    if (!privileged) {
      const access = await this.historyAccessRepo.findOneBy({
        spaceId,
        userId,
        status: 'APPROVED',
      });
      if (!access) {
        throw new ForbiddenException('Full history requires admin approval');
      }
    }
    const query = this.changesRepo
      .createQueryBuilder('change')
      .select([
        'change.changeId',
        'change.createdAt',
        'change.actorUserId',
        'change.entityType',
        'change.operation',
        'change.note',
      ])
      .where('change.spaceId = :spaceId', { spaceId })
      .orderBy('change.createdAt', 'DESC')
      .addOrderBy('change.changeId', 'DESC')
      .take(limit);
    if (before) {
      const separator = before.lastIndexOf('|');
      const beforeDate = new Date(before.slice(0, separator));
      const beforeId = before.slice(separator + 1);
      if (
        separator <= 0 ||
        Number.isNaN(beforeDate.getTime()) ||
        !isUUID(beforeId)
      ) {
        throw new BadRequestException('Invalid before cursor');
      }
      query.andWhere(
        '(change.createdAt < :beforeDate OR ' +
          '(change.createdAt = :beforeDate AND change.changeId < :beforeId))',
        { beforeDate, beforeId },
      );
    }
    const changes = await query.getMany();
    return {
      items: await this.withActorNames(changes),
      nextCursor:
        changes.length === limit
          ? `${changes[changes.length - 1].createdAt.toISOString()}|${changes[changes.length - 1].changeId}`
          : null,
    };
  }

  private async withActorNames(changes: ChangeLogEntity[]) {
    const actorIds = [
      ...new Set(changes.map((change) => change.actorUserId)),
    ].filter((actorUserId) => actorUserId !== 'SYSTEM');
    const actors = actorIds.length
      ? await this.usersRepo.find({
          where: { userId: In(actorIds) },
          select: ['userId', 'displayName'],
        })
      : [];
    const displayNameByUserId = new Map(
      actors.map((actor) => [actor.userId, actor.displayName]),
    );
    return changes.map((change) => ({
      ...change,
      actorDisplayName:
        change.actorUserId === 'SYSTEM'
          ? 'Sistem'
          : (displayNameByUserId.get(change.actorUserId) ?? 'Anggota keluarga'),
    }));
  }
}
