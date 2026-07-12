import {
  BadRequestException,
  ConflictException,
  Injectable,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { In, Repository } from 'typeorm';
import { RelationshipEntity } from './relationship.entity';
import { ChangeLogEntity } from '../changes/change-log.entity';
import { PersonEntity } from './person.entity';
import { databaseErrorMessage } from '../common/database-error';

@Injectable()
export class RelationshipsService {
  constructor(
    @InjectRepository(RelationshipEntity)
    private readonly relationsRepo: Repository<RelationshipEntity>,
    @InjectRepository(PersonEntity)
    private readonly personsRepo: Repository<PersonEntity>,
    @InjectRepository(ChangeLogEntity)
    private readonly changeRepo: Repository<ChangeLogEntity>,
  ) {}

  async findByPerson(spaceId: string, personId: string) {
    const parents = await this.relationsRepo.find({
      where: { spaceId, type: 'PARENT_CHILD', toPersonId: personId },
      order: { createdAt: 'DESC' },
      select: [
        'relationshipId',
        'fromPersonId',
        'toPersonId',
        'meta',
        'createdAt',
        'startDate',
        'endDate',
      ],
    });

    const children = await this.relationsRepo.find({
      where: { spaceId, type: 'PARENT_CHILD', fromPersonId: personId },
      order: { createdAt: 'DESC' },
      select: [
        'relationshipId',
        'fromPersonId',
        'toPersonId',
        'meta',
        'createdAt',
        'startDate',
        'endDate',
      ],
    });

    const spouses = await this.relationsRepo.find({
      where: [
        { spaceId, type: 'SPOUSE', fromPersonId: personId },
        { spaceId, type: 'SPOUSE', toPersonId: personId },
      ],
      order: { createdAt: 'DESC' },
      select: [
        'relationshipId',
        'fromPersonId',
        'toPersonId',
        'meta',
        'createdAt',
        'startDate',
        'endDate',
      ],
    });

    return { personId, parents, children, spouses };
  }

  findAll(spaceId: string) {
    return this.relationsRepo.find({
      where: { spaceId, type: 'PARENT_CHILD' },
      order: { createdAt: 'DESC' },
      select: [
        'relationshipId',
        'fromPersonId',
        'toPersonId',
        'meta',
        'createdAt',
      ],
    });
  }

  async createSpouse(
    spaceId: string,
    personAId: string,
    personBId: string,
    meta: 'MARRIED' | 'DIVORCED' | 'WIDOWED',
    startDate: string,
    endDate?: string | null,
    actorUserId?: string,
  ) {
    if (personAId === personBId) {
      throw new BadRequestException('Spouse cannot be the same person');
    }
    if (endDate && endDate < startDate) {
      throw new BadRequestException('endDate must be >= startDate');
    }

    const [fromPersonId, toPersonId] =
      personAId < personBId ? [personAId, personBId] : [personBId, personAId];

    const people = await this.personsRepo.findBy({
      personId: In([personAId, personBId]),
      spaceId,
      isDeleted: false,
    });
    if (people.length < 2) {
      throw new BadRequestException(
        'Spouses must be active persons in this Family Space',
      );
    }
    const personA = people.find((p) => p.personId === personAId);
    const personB = people.find((p) => p.personId === personBId);
    if (!personA || !personB) {
      throw new BadRequestException(
        'Spouses must be active persons in this Family Space',
      );
    }
    const aGender = personA.gender;
    const bGender = personB.gender;
    if (aGender === 'MALE' && bGender !== 'FEMALE') {
      throw new BadRequestException('Spouse gender must be FEMALE for male');
    }
    if (aGender === 'FEMALE' && bGender !== 'MALE') {
      throw new BadRequestException('Spouse gender must be MALE for female');
    }

    const existing = await this.relationsRepo.findOne({
      where: { spaceId, type: 'SPOUSE', fromPersonId, toPersonId },
    });
    if (existing) {
      throw new ConflictException('Spouse relationship already exists');
    }

    const rel = this.relationsRepo.create({
      spaceId,
      type: 'SPOUSE',
      fromPersonId,
      toPersonId,
      meta,
      startDate,
      endDate: endDate ?? null,
    });

    let saved: RelationshipEntity;
    try {
      saved = await this.relationsRepo.manager.transaction(async (manager) => {
        const savedRelation = await manager.save(rel);
        await manager.save(
          manager.create(ChangeLogEntity, {
            spaceId,
            actorUserId: actorUserId ?? 'SYSTEM',
            entityType: 'RELATIONSHIP',
            entityId: savedRelation.relationshipId,
            operation: 'CREATE',
            note: 'Create spouse relationship',
            afterJson: JSON.stringify(savedRelation),
          }),
        );
        return savedRelation;
      });
    } catch (error: unknown) {
      const message = databaseErrorMessage(error);
      if (message.includes('UNIQUE') || message.includes('constraint failed')) {
        throw new ConflictException('Spouse relationship already exists');
      }
      throw error;
    }

    return saved;
  }
}
