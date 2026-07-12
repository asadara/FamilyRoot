import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { ExportController } from './export.controller';
import { ExportService } from './export.service';
import { PersonEntity } from '../persons/person.entity';
import { RelationshipEntity } from '../persons/relationship.entity';
import { UserPersonClaimEntity } from '../claims/user-person-claim.entity';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      PersonEntity,
      RelationshipEntity,
      UserPersonClaimEntity,
    ]),
  ],
  controllers: [ExportController],
  providers: [ExportService],
})
export class ExportModule {}
