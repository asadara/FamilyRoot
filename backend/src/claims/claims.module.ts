import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { ClaimsController } from './claims.controller';
import { ClaimsService } from './claims.service';
import { UserPersonClaimEntity } from './user-person-claim.entity';
import { ChangeLogEntity } from '../changes/change-log.entity';
import { SpaceMemberEntity } from '../spaces/space-member.entity';
import { PersonEntity } from '../persons/person.entity';
import { ClaimConfirmationEntity } from './claim-confirmation.entity';
import { PersonsModule } from '../persons/persons.module';

@Module({
  imports: [
    PersonsModule,
    TypeOrmModule.forFeature([
      UserPersonClaimEntity,
      ChangeLogEntity,
      SpaceMemberEntity,
      PersonEntity,
      ClaimConfirmationEntity,
    ]),
  ],
  controllers: [ClaimsController],
  providers: [ClaimsService],
})
export class ClaimsModule {}
