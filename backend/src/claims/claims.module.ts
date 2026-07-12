import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { ClaimsController } from './claims.controller';
import { ClaimsService } from './claims.service';
import { UserPersonClaimEntity } from './user-person-claim.entity';
import { ChangeLogEntity } from '../changes/change-log.entity';
import { SpaceMemberEntity } from '../spaces/space-member.entity';
import { SpaceMemberGuard } from '../common/space-member.guard';
import { PersonEntity } from '../persons/person.entity';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      UserPersonClaimEntity,
      ChangeLogEntity,
      SpaceMemberEntity,
      PersonEntity,
    ]),
  ],
  controllers: [ClaimsController],
  providers: [ClaimsService, SpaceMemberGuard],
})
export class ClaimsModule {}
