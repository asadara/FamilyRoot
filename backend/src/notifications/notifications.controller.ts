import {
  BadRequestException,
  Controller,
  Get,
  Param,
  Patch,
  Post,
  Query,
} from '@nestjs/common';
import { isUUID } from 'class-validator';
import { ActorUserId } from '../common/actor-user-id.decorator';
import { NotificationsService } from './notifications.service';

@Controller('notifications')
export class NotificationsController {
  constructor(private readonly notificationsService: NotificationsService) {}

  @Get()
  list(@ActorUserId() actorUserId: string, @Query('limit') limit = '10') {
    return this.notificationsService.list(actorUserId, Number(limit));
  }

  @Patch(':notificationId/read')
  markRead(
    @ActorUserId() actorUserId: string,
    @Param('notificationId') notificationId: string,
  ) {
    if (!isUUID(notificationId)) {
      throw new BadRequestException('Invalid notificationId');
    }
    return this.notificationsService.markRead(actorUserId, notificationId);
  }

  @Post('read-all')
  markAllRead(@ActorUserId() actorUserId: string) {
    return this.notificationsService.markAllRead(actorUserId);
  }
}
