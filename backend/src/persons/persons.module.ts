import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { PersonsController } from './persons.controller';
import { PersonsService } from './persons.service';
import { PersonEntity } from './person.entity';
import { RelationshipEntity } from './relationship.entity';
import { ChangesModule } from '../changes/changes.module';
import { RelationshipsController } from './relationships.controller';
import { RelationshipsService } from './relationships.service';
import { SpaceMemberEntity } from '../spaces/space-member.entity';
import { UserPersonClaimEntity } from '../claims/user-person-claim.entity';
import { SpaceMemberGuard } from '../common/space-member.guard';
import { ClientMutationEntity } from './client-mutation.entity';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      PersonEntity,
      RelationshipEntity,
      SpaceMemberEntity,
      UserPersonClaimEntity,
      ClientMutationEntity,
    ]),
    ChangesModule,
  ],
  controllers: [PersonsController, RelationshipsController],
  providers: [PersonsService, RelationshipsService, SpaceMemberGuard],
})
export class PersonsModule {}
