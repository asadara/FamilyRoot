import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { SpaceMemberEntity } from '../spaces/space-member.entity';
import { UserPersonClaimEntity } from '../claims/user-person-claim.entity';
import { SpaceMemberGuard } from './space-member.guard';

@Module({
  imports: [
    TypeOrmModule.forFeature([SpaceMemberEntity, UserPersonClaimEntity]),
  ],
  providers: [SpaceMemberGuard],
  exports: [SpaceMemberGuard],
})
export class CommonModule {}
