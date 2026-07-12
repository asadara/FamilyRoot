import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { SpacesController } from './spaces.controller';
import { SpacesService } from './spaces.service';
import { FamilySpaceEntity } from './family-space.entity';
import { SpaceMemberEntity } from './space-member.entity';
import { UserEntity } from '../users/user.entity';
import { ChangeLogEntity } from '../changes/change-log.entity';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      FamilySpaceEntity,
      SpaceMemberEntity,
      UserEntity,
      ChangeLogEntity,
    ]),
  ],
  controllers: [SpacesController],
  providers: [SpacesService],
})
export class SpacesModule {}
