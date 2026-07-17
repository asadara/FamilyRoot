import {
  Injectable,
  BadRequestException,
  ConflictException,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { IsNull, Repository } from 'typeorm';
import { PersonEntity } from './person.entity';
import { RelationshipEntity } from './relationship.entity';
import { ChangeLogEntity } from '../changes/change-log.entity';
import { databaseErrorMessage } from '../common/database-error';

@Injectable()
export class PersonsService {
  constructor(
    @InjectRepository(PersonEntity)
    private readonly personsRepo: Repository<PersonEntity>,
    @InjectRepository(RelationshipEntity)
    private readonly relationsRepo: Repository<RelationshipEntity>,
    @InjectRepository(ChangeLogEntity)
    private readonly changeRepo: Repository<ChangeLogEntity>,
  ) {}

  private async isDescendant(
    spaceId: string,
    ancestorId: string,
    descendantId: string,
  ): Promise<boolean> {
    const relations = await this.personsRepo.manager.find(RelationshipEntity, {
      where: {
        spaceId,
        type: 'PARENT_CHILD',
        fromPersonId: ancestorId,
      },
    });

    for (const rel of relations) {
      if (rel.toPersonId === descendantId) return true;
      const deeper = await this.isDescendant(
        spaceId,
        rel.toPersonId,
        descendantId,
      );
      if (deeper) return true;
    }

    return false;
  }

  private async countBiologicalParents(
    spaceId: string,
    childId: string,
  ): Promise<number> {
    return this.personsRepo.manager.count(RelationshipEntity, {
      where: {
        spaceId,
        type: 'PARENT_CHILD',
        toPersonId: childId,
        meta: 'BIOLOGICAL',
      },
    });
  }

  async create(
    dto: {
      spaceId: string;
      title?: string | null;
      firstName: string;
      lastName?: string | null;
      suffix?: string | null;
      nickName: string;
      gender: 'MALE' | 'FEMALE' | 'UNKNOWN';
      birthDate?: string | null;
      birthPlace?: string | null;
      deathDate?: string | null;
      deathPlace?: string | null;
      idNumber?: string | null;
      lifeStatus?: 'ALIVE' | 'DECEASED' | 'UNKNOWN';
    },
    actorUserId: string,
  ) {
    const nameParts = [
      dto.title,
      dto.firstName,
      dto.lastName,
      dto.suffix,
    ].filter((part) => part && String(part).trim().length > 0);
    const fullName = nameParts.join(' ');

    const lifeStatus = dto.lifeStatus ?? 'ALIVE';
    const deceasedAt =
      lifeStatus === 'DECEASED' ? (dto.deathDate ?? null) : null;

    if (dto.birthDate && dto.deathDate && dto.deathDate < dto.birthDate) {
      throw new BadRequestException('deathDate must be on or after birthDate');
    }
    if (lifeStatus === 'ALIVE' && dto.deathDate) {
      throw new BadRequestException('An ALIVE person cannot have a deathDate');
    }

    return this.personsRepo.manager.transaction(async (manager) => {
      const person = manager.create(PersonEntity, {
        spaceId: dto.spaceId,
        fullName,
        title: dto.title ?? null,
        firstName: dto.firstName,
        lastName: dto.lastName ?? null,
        suffix: dto.suffix ?? null,
        nickName: dto.nickName,
        gender: dto.gender,
        birthDate: dto.birthDate ?? null,
        birthPlace: dto.birthPlace ?? null,
        deathDate: dto.deathDate ?? null,
        deathPlace: dto.deathPlace ?? null,
        idNumber: dto.idNumber ?? null,
        lifeStatus,
        deceasedAt,
      });
      const saved = await manager.save(person);
      await manager.save(
        manager.create(ChangeLogEntity, {
          spaceId: saved.spaceId,
          actorUserId,
          entityType: 'PERSON',
          entityId: saved.personId,
          operation: 'CREATE',
          note: 'Create person',
          afterJson: JSON.stringify(saved),
        }),
      );
      return saved;
    });
  }

  findBySpace(spaceId: string) {
    return this.personsRepo.find({
      where: { spaceId, isDeleted: false },
      order: { createdAt: 'DESC' },
      select: [
        'personId',
        'fullName',
        'createdAt',
        'lifeStatus',
        'deceasedAt',
        'birthDate',
        'gender',
      ],
    });
  }

  async findDuplicateCandidates(spaceId: string) {
    const people = await this.personsRepo.find({
      where: { spaceId, isDeleted: false },
      order: { fullName: 'ASC' },
      select: [
        'personId',
        'fullName',
        'birthDate',
        'gender',
        'lifeStatus',
        'createdAt',
      ],
    });
    const groups = new Map<string, typeof people>();
    for (const person of people) {
      const key = [
        person.fullName.trim().toLowerCase().replace(/\s+/g, ' '),
        person.birthDate ?? 'unknown-birth',
      ].join('|');
      groups.set(key, [...(groups.get(key) ?? []), person]);
    }
    return [...groups.values()]
      .filter((group) => group.length > 1)
      .map((group) => ({
        reason: 'Same normalized full name and birth date',
        people: group,
      }));
  }

  async mergePersons(
    spaceId: string,
    sourcePersonId: string,
    targetPersonId: string,
    actorUserId: string,
  ) {
    if (sourcePersonId === targetPersonId) {
      throw new BadRequestException(
        'sourcePersonId and targetPersonId must differ',
      );
    }

    const [source, target] = await Promise.all([
      this.personsRepo.findOneBy({
        spaceId,
        personId: sourcePersonId,
        isDeleted: false,
      }),
      this.personsRepo.findOneBy({
        spaceId,
        personId: targetPersonId,
        isDeleted: false,
      }),
    ]);
    if (!source || !target) {
      throw new NotFoundException('Source or target person not found');
    }

    return this.personsRepo.manager.transaction(async (manager) => {
      const relations = await manager.find(RelationshipEntity, {
        where: [
          { spaceId, fromPersonId: sourcePersonId },
          { spaceId, toPersonId: sourcePersonId },
        ],
      });

      for (const relation of relations) {
        const fromPersonId =
          relation.fromPersonId === sourcePersonId
            ? targetPersonId
            : relation.fromPersonId;
        const toPersonId =
          relation.toPersonId === sourcePersonId
            ? targetPersonId
            : relation.toPersonId;

        if (fromPersonId === toPersonId) {
          await manager.delete(RelationshipEntity, {
            relationshipId: relation.relationshipId,
          });
          continue;
        }

        const existing = await manager.findOne(RelationshipEntity, {
          where: {
            spaceId,
            type: relation.type,
            fromPersonId,
            toPersonId,
          },
        });
        if (existing) {
          await manager.delete(RelationshipEntity, {
            relationshipId: relation.relationshipId,
          });
          continue;
        }

        relation.fromPersonId = fromPersonId;
        relation.toPersonId = toPersonId;
        await manager.save(relation);
      }

      const beforeSource = JSON.stringify(source);
      source.isDeleted = true;
      source.notes = [
        source.notes,
        `Merged into ${target.fullName} (${target.personId})`,
      ]
        .filter(Boolean)
        .join('\n');
      const savedSource = await manager.save(source);

      await manager.save(
        manager.create(ChangeLogEntity, {
          spaceId,
          actorUserId,
          entityType: 'PERSON',
          entityId: savedSource.personId,
          operation: 'DELETE',
          note: `Merge duplicate into ${target.personId}`,
          beforeJson: beforeSource,
          afterJson: JSON.stringify(savedSource),
        }),
      );

      return { sourcePersonId, targetPersonId, merged: true };
    });
  }

  async addParentChild(
    spaceId: string,
    parentId: string,
    childId: string,
    meta: 'BIOLOGICAL' | 'ADOPTIVE' | 'STEP',
    actorUserId: string,
  ) {
    if (parentId === childId) {
      throw new BadRequestException(
        'Parent and child cannot be the same person',
      );
    }

    const people = await this.personsRepo.find({
      where: [
        { personId: parentId, spaceId, isDeleted: false },
        { personId: childId, spaceId, isDeleted: false },
      ],
    });
    if (people.length !== 2) {
      throw new BadRequestException(
        'Parent and child must be active persons in this Family Space',
      );
    }

    const cycle = await this.isDescendant(spaceId, childId, parentId);
    if (cycle) {
      throw new BadRequestException('Cycle detected in family tree');
    }

    if (meta === 'BIOLOGICAL') {
      const bioCount = await this.countBiologicalParents(spaceId, childId);
      if (bioCount >= 2) {
        throw new BadRequestException('Child already has 2 biological parents');
      }
    }

    const rel = this.personsRepo.manager.create(RelationshipEntity, {
      spaceId,
      type: 'PARENT_CHILD',
      fromPersonId: parentId,
      toPersonId: childId,
      meta,
    });

    let saved: RelationshipEntity;
    try {
      saved = await this.personsRepo.manager.transaction(async (manager) => {
        const savedRelation = await manager.save(rel);
        await manager.save(
          manager.create(ChangeLogEntity, {
            spaceId,
            actorUserId,
            entityType: 'RELATIONSHIP',
            entityId: savedRelation.relationshipId,
            operation: 'CREATE',
            note: 'Add parent-child relationship',
            afterJson: JSON.stringify(savedRelation),
          }),
        );
        return savedRelation;
      });
    } catch (error: unknown) {
      const message = databaseErrorMessage(error);
      if (message.includes('UNIQUE') || message.includes('constraint failed')) {
        throw new ConflictException('Relationship already exists');
      }
      throw error;
    }

    return saved;
  }

  async softDelete(spaceId: string, personId: string, actorUserId: string) {
    const person = await this.personsRepo.findOneBy({ personId, spaceId });
    if (!person) {
      throw new NotFoundException('Person not found');
    }

    if (person.isDeleted) {
      return person;
    }

    return this.personsRepo.manager.transaction(async (manager) => {
      const before = { ...person };
      person.isDeleted = true;
      const saved = await manager.save(person);
      await manager.save(
        manager.create(ChangeLogEntity, {
          spaceId,
          actorUserId,
          entityType: 'PERSON',
          entityId: saved.personId,
          operation: 'DELETE',
          note: 'Soft delete person',
          beforeJson: JSON.stringify(before),
          afterJson: JSON.stringify(saved),
        }),
      );
      return saved;
    });
  }

  async updateLifeStatus(
    spaceId: string,
    personId: string,
    lifeStatus: 'ALIVE' | 'DECEASED' | 'UNKNOWN',
    deceasedAt?: string | null,
    actorUserId?: string,
  ) {
    const person = await this.personsRepo.findOneBy({ personId, spaceId });
    if (!person) {
      throw new NotFoundException('Person not found');
    }

    const beforePerson = JSON.stringify(person);

    const effectiveDeceasedAt =
      lifeStatus === 'DECEASED'
        ? (deceasedAt ?? new Date().toISOString().slice(0, 10))
        : null;

    person.lifeStatus = lifeStatus;
    person.deceasedAt = effectiveDeceasedAt;

    const savedPerson = await this.personsRepo.save(person);

    await this.changeRepo.save({
      spaceId,
      actorUserId: actorUserId ?? 'SYSTEM',
      entityType: 'PERSON',
      entityId: savedPerson.personId,
      operation: 'UPDATE',
      note: 'Update life status',
      beforeJson: beforePerson,
      afterJson: JSON.stringify(savedPerson),
    });

    if (lifeStatus === 'DECEASED') {
      const endDate =
        effectiveDeceasedAt ?? new Date().toISOString().slice(0, 10);

      const spouses = await this.relationsRepo.find({
        where: [
          {
            spaceId,
            type: 'SPOUSE',
            fromPersonId: personId,
            meta: 'MARRIED',
            endDate: IsNull(),
          },
          {
            spaceId,
            type: 'SPOUSE',
            toPersonId: personId,
            meta: 'MARRIED',
            endDate: IsNull(),
          },
        ],
      });

      for (const spouse of spouses) {
        const beforeRel = JSON.stringify(spouse);
        spouse.meta = 'WIDOWED';
        spouse.endDate = endDate;

        const savedRel = await this.relationsRepo.save(spouse);
        await this.changeRepo.save({
          spaceId,
          actorUserId: actorUserId ?? 'SYSTEM',
          entityType: 'RELATIONSHIP',
          entityId: savedRel.relationshipId,
          operation: 'UPDATE',
          note: 'Auto-update spouse to widowed',
          beforeJson: beforeRel,
          afterJson: JSON.stringify(savedRel),
        });
      }
    }

    return savedPerson;
  }
}
