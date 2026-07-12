import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { PersonEntity } from '../persons/person.entity';
import { RelationshipEntity } from '../persons/relationship.entity';
import { UserPersonClaimEntity } from '../claims/user-person-claim.entity';

@Injectable()
export class ExportService {
  constructor(
    @InjectRepository(PersonEntity)
    private readonly personsRepo: Repository<PersonEntity>,
    @InjectRepository(RelationshipEntity)
    private readonly relationsRepo: Repository<RelationshipEntity>,
    @InjectRepository(UserPersonClaimEntity)
    private readonly claimsRepo: Repository<UserPersonClaimEntity>,
  ) {}

  async exportSpace(spaceId: string) {
    const persons = await this.personsRepo.find({
      where: { spaceId, isDeleted: false },
      order: { createdAt: 'ASC' },
      select: ['personId', 'fullName', 'lifeStatus', 'deceasedAt', 'createdAt'],
    });

    const relationships = await this.relationsRepo.find({
      where: { spaceId },
      order: { createdAt: 'ASC' },
      select: [
        'relationshipId',
        'type',
        'fromPersonId',
        'toPersonId',
        'meta',
        'startDate',
        'endDate',
        'createdAt',
      ],
    });

    const claims = await this.claimsRepo.find({
      where: { spaceId },
      order: { requestedAt: 'ASC' },
      select: ['claimId', 'status', 'userId', 'personId', 'requestedAt'],
    });

    return {
      spaceId,
      persons,
      relationships,
      claims: claims.map((c) => ({
        claimId: c.claimId,
        status: c.status,
        userId: c.userId,
        personId: c.personId,
        createdAt: c.requestedAt,
      })),
    };
  }
}
