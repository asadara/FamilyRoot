import {
  BadRequestException,
  Injectable,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { IsNull, Repository } from 'typeorm';
import { UserNotificationEntity } from './user-notification.entity';

export interface MutationReceipt {
  userId: string;
  spaceId: string | null;
  kind: UserNotificationEntity['kind'];
  code: string;
  title: string;
  message: string;
}

@Injectable()
export class NotificationsService {
  private readonly logger = new Logger(NotificationsService.name);

  constructor(
    @InjectRepository(UserNotificationEntity)
    private readonly notificationsRepo: Repository<UserNotificationEntity>,
  ) {}

  async record(receipt: MutationReceipt) {
    try {
      await this.notificationsRepo.save(
        this.notificationsRepo.create({
          ...receipt,
          readAt: null,
        }),
      );
    } catch {
      // A receipt must never turn an already completed family mutation into a
      // client-visible failure. Do not log family/user identifiers here.
      this.logger.warn('Unable to persist a mutation receipt');
    }
  }

  async list(userId: string, limit = 50) {
    if (!Number.isInteger(limit) || limit < 1 || limit > 100) {
      throw new BadRequestException('limit must be between 1 and 100');
    }
    const [items, unreadCount] = await Promise.all([
      this.notificationsRepo.find({
        where: { userId },
        order: { createdAt: 'DESC' },
        take: limit,
      }),
      this.notificationsRepo.countBy({ userId, readAt: IsNull() }),
    ]);
    return {
      items: items.map((item) => this.result(item)),
      unreadCount,
    };
  }

  async markRead(userId: string, notificationId: string) {
    const notification = await this.notificationsRepo.findOneBy({
      userId,
      notificationId,
    });
    if (!notification) throw new NotFoundException('Notification not found');
    if (!notification.readAt) {
      notification.readAt = new Date();
      await this.notificationsRepo.save(notification);
    }
    return this.result(notification);
  }

  async markAllRead(userId: string) {
    const readAt = new Date();
    const result = await this.notificationsRepo
      .createQueryBuilder()
      .update(UserNotificationEntity)
      .set({ readAt })
      .where('"userId" = :userId', { userId })
      .andWhere('"readAt" IS NULL')
      .execute();
    return { updated: result.affected ?? 0, readAt };
  }

  private result(item: UserNotificationEntity) {
    return {
      notificationId: item.notificationId,
      spaceId: item.spaceId,
      kind: item.kind,
      code: item.code,
      title: item.title,
      message: item.message,
      readAt: item.readAt,
      createdAt: item.createdAt,
    };
  }
}
