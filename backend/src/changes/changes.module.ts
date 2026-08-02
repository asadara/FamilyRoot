import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { ChangeLogEntity } from './change-log.entity';
import { ChangesService } from './changes.service';
import { ChangesController } from './changes.controller';
import { UserEntity } from '../users/user.entity';
import { SpaceMemberEntity } from '../spaces/space-member.entity';
import { HistoryAccessRequestEntity } from './history-access-request.entity';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      ChangeLogEntity,
      UserEntity,
      SpaceMemberEntity,
      HistoryAccessRequestEntity,
    ]),
  ],
  providers: [ChangesService],
  controllers: [ChangesController],
  exports: [TypeOrmModule, ChangesService],
})
export class ChangesModule {}
