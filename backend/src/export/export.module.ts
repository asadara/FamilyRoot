import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { ExportController } from './export.controller';
import { ExportService } from './export.service';
import { PersonEntity } from '../persons/person.entity';
import { RelationshipEntity } from '../persons/relationship.entity';
import { UserPersonClaimEntity } from '../claims/user-person-claim.entity';
import { EditProposalEntity } from '../archive/edit-proposal.entity';
import { FactSourceEntity } from '../archive/fact-source.entity';
import { MediaItemEntity } from '../archive/media-item.entity';
import { ChangeLogEntity } from '../changes/change-log.entity';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      PersonEntity,
      RelationshipEntity,
      UserPersonClaimEntity,
      FactSourceEntity,
      MediaItemEntity,
      EditProposalEntity,
      ChangeLogEntity,
    ]),
  ],
  controllers: [ExportController],
  providers: [ExportService],
})
export class ExportModule {}
