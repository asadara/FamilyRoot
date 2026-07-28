import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { MutationReceiptInterceptor } from './mutation-receipt.interceptor';
import { NotificationsController } from './notifications.controller';
import { NotificationsService } from './notifications.service';
import { UserNotificationEntity } from './user-notification.entity';

@Module({
  imports: [TypeOrmModule.forFeature([UserNotificationEntity])],
  controllers: [NotificationsController],
  providers: [NotificationsService, MutationReceiptInterceptor],
  exports: [NotificationsService, MutationReceiptInterceptor],
})
export class NotificationsModule {}
