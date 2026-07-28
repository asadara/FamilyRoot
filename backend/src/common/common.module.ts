import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { SpaceMemberEntity } from '../spaces/space-member.entity';
import { UserPersonClaimEntity } from '../claims/user-person-claim.entity';
import { SpaceMemberGuard } from './space-member.guard';
import { FamilySpaceEntity } from '../spaces/family-space.entity';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      SpaceMemberEntity,
      UserPersonClaimEntity,
      FamilySpaceEntity,
    ]),
  ],
  providers: [SpaceMemberGuard],
  exports: [SpaceMemberGuard],
})
export class CommonModule {}
