import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { UsersController } from './users.controller';
import { UsersService } from './users.service';
import { UserEntity } from './user.entity';
import { AccountLifecycleAuditEntity } from './account-lifecycle-audit.entity';

@Module({
  imports: [
    TypeOrmModule.forFeature([UserEntity, AccountLifecycleAuditEntity]),
  ],
  controllers: [UsersController],
  providers: [UsersService],
  exports: [UsersService],
})
export class UsersModule {}
