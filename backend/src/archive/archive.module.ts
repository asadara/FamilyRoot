import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { ChangeLogEntity } from '../changes/change-log.entity';
import { SpaceMemberGuard } from '../common/space-member.guard';
import { UserPersonClaimEntity } from '../claims/user-person-claim.entity';
import { PersonEntity } from '../persons/person.entity';
import { SpaceMemberEntity } from '../spaces/space-member.entity';
import { ArchiveController } from './archive.controller';
import { ArchiveService } from './archive.service';
import { EditProposalEntity } from './edit-proposal.entity';
import { FactSourceEntity } from './fact-source.entity';
import { MediaItemEntity } from './media-item.entity';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      FactSourceEntity,
      MediaItemEntity,
      EditProposalEntity,
      PersonEntity,
      ChangeLogEntity,
      SpaceMemberEntity,
      UserPersonClaimEntity,
    ]),
  ],
  controllers: [ArchiveController],
  providers: [ArchiveService, SpaceMemberGuard],
  exports: [TypeOrmModule],
})
export class ArchiveModule {}
