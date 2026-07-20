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
import { DisabledObjectStorage } from './storage/disabled-object-storage';
import { OBJECT_STORAGE } from './storage/object-storage';
import { SupabaseObjectStorage } from './storage/supabase-object-storage';
import { normalizedSupabaseProjectUrl } from '../config/environment';

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
  providers: [
    ArchiveService,
    SpaceMemberGuard,
    {
      provide: OBJECT_STORAGE,
      useFactory: () => {
        const projectUrl = process.env.SUPABASE_URL;
        const secretKey =
          process.env.SUPABASE_SECRET_KEY ??
          process.env.SUPABASE_SERVICE_ROLE_KEY;
        if (projectUrl && secretKey) {
          return new SupabaseObjectStorage(
            normalizedSupabaseProjectUrl(projectUrl),
            secretKey,
            process.env.SUPABASE_STORAGE_BUCKET ?? 'family-media',
          );
        }
        return new DisabledObjectStorage();
      },
    },
  ],
  exports: [TypeOrmModule],
})
export class ArchiveModule {}
