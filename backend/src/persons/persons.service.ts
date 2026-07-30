import {
  Injectable,
  BadRequestException,
  ConflictException,
  ForbiddenException,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { IsNull, Repository } from 'typeorm';
import { PersonEntity } from './person.entity';
import {
  isCareRelationshipMeta,
  isLineageParentChildMeta,
  ParentChildMeta,
  RelationshipEntity,
} from './relationship.entity';
import { ChangeLogEntity } from '../changes/change-log.entity';
import { databaseErrorMessage } from '../common/database-error';
import { ClientMutationEntity } from './client-mutation.entity';
import { randomUUID } from 'crypto';
import {
  PersonPrivacyDecision,
  PersonPrivacyService,
} from './person-privacy.service';

@Injectable()
export class PersonsService {
  constructor(
    @InjectRepository(PersonEntity)
    private readonly personsRepo: Repository<PersonEntity>,
    @InjectRepository(RelationshipEntity)
    private readonly relationsRepo: Repository<RelationshipEntity>,
    @InjectRepository(ChangeLogEntity)
    private readonly changeRepo: Repository<ChangeLogEntity>,
    private readonly privacyService: PersonPrivacyService,
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
      if (!isLineageParentChildMeta(rel.meta)) continue;
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
      clientMutationId?: string;
    },
    actorUserId: string,
  ) {
    const clientMutationId = dto.clientMutationId ?? randomUUID();
    const requestFingerprint = JSON.stringify({
      spaceId: dto.spaceId,
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
      lifeStatus: dto.lifeStatus ?? 'ALIVE',
    });
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
      const priorMutation = await manager.findOne(ClientMutationEntity, {
        where: { clientMutationId },
      });
      if (priorMutation) {
        if (
          priorMutation.actorUserId !== actorUserId ||
          priorMutation.requestFingerprint !== requestFingerprint
        ) {
          throw new ConflictException(
            'clientMutationId was already used for another mutation',
          );
        }
        const priorPerson = JSON.parse(
          priorMutation.responseJson,
        ) as PersonEntity;
        const current = await manager.findOneBy(PersonEntity, {
          personId: priorPerson.personId,
          spaceId: dto.spaceId,
        });
        if (!current) throw new NotFoundException('Person not found');
        const decision = await this.privacyService.decisionForPerson(
          dto.spaceId,
          current,
          actorUserId,
          manager,
        );
        return this.personReadResult(current, decision);
      }
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
        visibility: lifeStatus === 'DECEASED' ? 'FAMILY' : 'LIMITED',
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
      await manager.save(
        manager.create(ClientMutationEntity, {
          clientMutationId,
          actorUserId,
          spaceId: saved.spaceId,
          operation: 'CREATE_PERSON',
          requestFingerprint,
          responseJson: JSON.stringify(saved),
        }),
      );
      const decision = await this.privacyService.decisionForPerson(
        saved.spaceId,
        saved,
        actorUserId,
        manager,
      );
      return this.personReadResult(saved, decision);
    });
  }

  async findBySpace(spaceId: string, actorUserId: string) {
    const people = await this.personsRepo.find({
      where: { spaceId, isDeleted: false },
      order: { createdAt: 'DESC' },
      select: [
        'personId',
        'fullName',
        'createdAt',
        'lifeStatus',
        'deceasedAt',
        'birthDate',
        'birthPlace',
        'nickName',
        'gender',
        'deathPlace',
        'notes',
        'version',
        'visibility',
      ],
    });
    const decisions = await this.privacyService.decisionsForPeople(
      spaceId,
      people,
      actorUserId,
    );
    return people.map((person) =>
      this.personReadResult(
        person,
        decisions.get(person.personId) as PersonPrivacyDecision,
      ),
    );
  }

  async resolveCreateMutation(
    spaceId: string,
    clientMutationId: string,
    actorUserId: string,
  ) {
    const mutation = await this.personsRepo.manager.findOne(
      ClientMutationEntity,
      {
        where: {
          clientMutationId,
          spaceId,
          actorUserId,
          operation: 'CREATE_PERSON',
        },
      },
    );
    if (!mutation) {
      throw new NotFoundException('Create-person mutation result not found');
    }
    const stored = JSON.parse(mutation.responseJson) as PersonEntity;
    return this.findOneForUser(spaceId, stored.personId, actorUserId);
  }

  async findOneForUser(spaceId: string, personId: string, actorUserId: string) {
    const { person, decision } =
      await this.privacyService.findPersonWithDecision(
        spaceId,
        personId,
        actorUserId,
      );
    return this.personReadResult(person, decision);
  }

  async findDuplicateCandidates(spaceId: string, actorUserId: string) {
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
        'visibility',
      ],
    });
    const decisions = await this.privacyService.decisionsForPeople(
      spaceId,
      people,
      actorUserId,
    );
    const fullAccessPeople = people.filter(
      (person) => decisions.get(person.personId)?.access === 'FULL',
    );
    const groups = new Map<string, typeof fullAccessPeople>();
    for (const person of fullAccessPeople) {
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
        people: group.map((person) =>
          this.personReadResult(
            person,
            decisions.get(person.personId) as PersonPrivacyDecision,
          ),
        ),
      }));
  }

  async updateVisibility(
    spaceId: string,
    personId: string,
    visibility: PersonEntity['visibility'],
    actorUserId: string,
    expectedVersion: number,
  ) {
    return this.personsRepo.manager.transaction(
      'SERIALIZABLE',
      async (manager) => {
        let personQuery = manager
          .getRepository(PersonEntity)
          .createQueryBuilder('person')
          .where('person.spaceId = :spaceId', { spaceId })
          .andWhere('person.personId = :personId', { personId })
          .andWhere('person.isDeleted = :isDeleted', { isDeleted: false });
        if (manager.connection.options.type !== 'sqlite') {
          personQuery = personQuery.setLock('pessimistic_write');
        }
        const person = await personQuery.getOne();
        if (!person) throw new NotFoundException('Person not found');
        const decision = await this.privacyService.decisionForPerson(
          spaceId,
          person,
          actorUserId,
          manager,
        );
        if (!decision.canManageVisibility) {
          throw new ForbiddenException(
            'Privacy is controlled by the verified claimant',
          );
        }
        if (person.version !== expectedVersion) {
          throw new ConflictException({
            message: 'Person was changed by another contributor',
            details: {
              personId,
              version: person.version,
              visibility: person.visibility,
              updatedAt: person.updatedAt,
            },
          });
        }
        if (person.visibility === visibility) {
          return this.personReadResult(person, decision);
        }

        const before = { ...person };
        const update = await manager
          .createQueryBuilder()
          .update(PersonEntity)
          .set({ visibility, version: () => 'version + 1' })
          .where('spaceId = :spaceId', { spaceId })
          .andWhere('personId = :personId', { personId })
          .andWhere('version = :expectedVersion', { expectedVersion })
          .execute();
        if (update.affected !== 1) {
          throw new ConflictException(
            'Person was changed by another contributor',
          );
        }
        const saved = await manager.findOneByOrFail(PersonEntity, {
          spaceId,
          personId,
        });
        await manager.save(
          manager.create(ChangeLogEntity, {
            spaceId,
            actorUserId,
            entityType: 'PERSON',
            entityId: personId,
            operation: 'UPDATE',
            note: `Change person visibility from ${before.visibility} to ${visibility}`,
            beforeJson: JSON.stringify(before),
            afterJson: JSON.stringify(saved),
          }),
        );
        return this.personReadResult(saved, decision);
      },
    );
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
    await this.privacyService.requireFullAccessForPeople(
      spaceId,
      [source, target],
      actorUserId,
    );

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
      source.deletedAt = new Date();
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
    meta: ParentChildMeta,
    actorUserId: string,
    clientMutationId: string,
    startDate?: string | null,
    endDate?: string | null,
    careContext?: string | null,
  ) {
    const normalizedContext = careContext?.trim() || null;
    const requestFingerprint = JSON.stringify({
      spaceId,
      parentId,
      childId,
      meta,
      startDate: startDate ?? null,
      endDate: endDate ?? null,
      careContext: normalizedContext,
    });
    const priorMutation = await this.personsRepo.manager.findOne(
      ClientMutationEntity,
      { where: { clientMutationId } },
    );
    if (priorMutation) {
      if (
        priorMutation.actorUserId !== actorUserId ||
        priorMutation.requestFingerprint !== requestFingerprint
      ) {
        throw new ConflictException(
          'clientMutationId was already used for another mutation',
        );
      }
      return JSON.parse(priorMutation.responseJson) as RelationshipEntity;
    }
    if (parentId === childId) {
      throw new BadRequestException(
        'Parent and child cannot be the same person',
      );
    }
    if (endDate && startDate && endDate < startDate) {
      throw new BadRequestException('endDate must be >= startDate');
    }
    if (!isCareRelationshipMeta(meta) && normalizedContext) {
      throw new BadRequestException(
        'careContext is only available for foster or guardian relationships',
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
    await this.privacyService.requireFullAccessForPeople(
      spaceId,
      people,
      actorUserId,
    );

    if (!isCareRelationshipMeta(meta)) {
      const partnership = await this.relationsRepo.findOne({
        where: [
          {
            spaceId,
            type: 'SPOUSE',
            fromPersonId: parentId,
            toPersonId: childId,
          },
          {
            spaceId,
            type: 'SPOUSE',
            fromPersonId: childId,
            toPersonId: parentId,
          },
        ],
      });
      if (partnership) {
        throw new BadRequestException(
          'Parent-child relationship cannot be created between spouses',
        );
      }

      const cycle = await this.isDescendant(spaceId, childId, parentId);
      if (cycle) {
        throw new BadRequestException('Cycle detected in family tree');
      }
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
      startDate: startDate ?? null,
      endDate: endDate ?? null,
      careContext: normalizedContext,
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
            note: isCareRelationshipMeta(meta)
              ? `Add ${meta.toLowerCase()} care relationship`
              : 'Add parent-child relationship',
            afterJson: JSON.stringify(savedRelation),
          }),
        );
        await manager.save(
          manager.create(ClientMutationEntity, {
            clientMutationId,
            actorUserId,
            spaceId,
            operation: 'ADD_PARENT_CHILD',
            requestFingerprint,
            responseJson: JSON.stringify(savedRelation),
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

  async updateLifeStatus(
    spaceId: string,
    personId: string,
    lifeStatus: 'ALIVE' | 'DECEASED' | 'UNKNOWN',
    deceasedAt?: string | null,
    actorUserId?: string,
    expectedVersion?: number,
    clientMutationId?: string,
  ) {
    if (!actorUserId || !expectedVersion || !clientMutationId) {
      throw new BadRequestException(
        'Mutation identity and expectedVersion are required',
      );
    }

    const requestFingerprint = JSON.stringify({
      spaceId,
      personId,
      lifeStatus,
      deceasedAt: deceasedAt ?? null,
      expectedVersion,
    });

    return this.personsRepo.manager.transaction(async (manager) => {
      const priorMutation = await manager.findOne(ClientMutationEntity, {
        where: { clientMutationId },
      });
      if (priorMutation) {
        if (
          priorMutation.actorUserId !== actorUserId ||
          priorMutation.requestFingerprint !== requestFingerprint
        ) {
          throw new ConflictException(
            'clientMutationId was already used for another mutation',
          );
        }
        const current = await manager.findOneBy(PersonEntity, {
          personId,
          spaceId,
        });
        if (!current) throw new NotFoundException('Person not found');
        await this.privacyService.requireFullAccessForPeople(
          spaceId,
          [current],
          actorUserId,
          manager,
        );
        return JSON.parse(priorMutation.responseJson) as PersonEntity;
      }

      const person = await manager.findOneBy(PersonEntity, {
        personId,
        spaceId,
      });
      if (!person) {
        throw new NotFoundException('Person not found');
      }
      await this.privacyService.requireFullAccessForPeople(
        spaceId,
        [person],
        actorUserId,
        manager,
      );
      if (person.version !== expectedVersion) {
        throw new ConflictException({
          message: 'Person was changed by another contributor',
          details: {
            personId: person.personId,
            version: person.version,
            lifeStatus: person.lifeStatus,
            deceasedAt: person.deceasedAt,
            updatedAt: person.updatedAt,
          },
        });
      }

      const beforePerson = JSON.stringify(person);
      const effectiveDeceasedAt =
        lifeStatus === 'DECEASED'
          ? (deceasedAt ?? person.deceasedAt ?? null)
          : null;

      const updateResult = await manager
        .createQueryBuilder()
        .update(PersonEntity)
        .set({
          lifeStatus,
          deceasedAt: effectiveDeceasedAt,
          version: () => 'version + 1',
        })
        .where('personId = :personId', { personId })
        .andWhere('spaceId = :spaceId', { spaceId })
        .andWhere('version = :expectedVersion', { expectedVersion })
        .execute();
      if (updateResult.affected !== 1) {
        const current = await manager.findOneBy(PersonEntity, {
          personId,
          spaceId,
        });
        throw new ConflictException({
          message: 'Person was changed by another contributor',
          details: current
            ? {
                personId: current.personId,
                version: current.version,
                lifeStatus: current.lifeStatus,
                deceasedAt: current.deceasedAt,
                updatedAt: current.updatedAt,
              }
            : null,
        });
      }

      const savedPerson = await manager.findOneByOrFail(PersonEntity, {
        personId,
        spaceId,
      });
      await manager.save(
        manager.create(ChangeLogEntity, {
          spaceId,
          actorUserId,
          entityType: 'PERSON',
          entityId: savedPerson.personId,
          operation: 'UPDATE',
          note: 'Update life status',
          beforeJson: beforePerson,
          afterJson: JSON.stringify(savedPerson),
        }),
      );

      if (lifeStatus === 'DECEASED') {
        const endDate = effectiveDeceasedAt;
        const spouses = await manager.find(RelationshipEntity, {
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
          const savedRel = await manager.save(spouse);
          await manager.save(
            manager.create(ChangeLogEntity, {
              spaceId,
              actorUserId,
              entityType: 'RELATIONSHIP',
              entityId: savedRel.relationshipId,
              operation: 'UPDATE',
              note: 'Auto-update spouse to widowed',
              beforeJson: beforeRel,
              afterJson: JSON.stringify(savedRel),
            }),
          );
        }
      }

      await manager.save(
        manager.create(ClientMutationEntity, {
          clientMutationId,
          actorUserId,
          spaceId,
          operation: 'UPDATE_LIFE_STATUS',
          requestFingerprint,
          responseJson: JSON.stringify(savedPerson),
        }),
      );
      return savedPerson;
    });
  }

  async updateProfile(
    spaceId: string,
    personId: string,
    profile: {
      fullName?: string;
      nickName?: string;
      gender?: 'MALE' | 'FEMALE' | 'UNKNOWN';
      birthDate?: string;
      birthPlace?: string;
      deathPlace?: string;
      notes?: string;
    },
    actorUserId: string,
    expectedVersion: number,
    clientMutationId: string,
  ) {
    const normalizedProfile = {
      fullName: profile.fullName?.trim(),
      nickName: profile.nickName?.trim(),
      gender: profile.gender,
      birthDate:
        profile.birthDate === undefined
          ? undefined
          : profile.birthDate.trim() || null,
      birthPlace:
        profile.birthPlace === undefined
          ? undefined
          : profile.birthPlace.trim() || null,
      deathPlace:
        profile.deathPlace === undefined
          ? undefined
          : profile.deathPlace.trim() || null,
      notes:
        profile.notes === undefined ? undefined : profile.notes.trim() || null,
    };
    const requestFingerprint = JSON.stringify({
      spaceId,
      personId,
      profile: normalizedProfile,
      expectedVersion,
    });

    return this.personsRepo.manager.transaction(async (manager) => {
      const priorMutation = await manager.findOne(ClientMutationEntity, {
        where: { clientMutationId },
      });
      if (priorMutation) {
        if (
          priorMutation.actorUserId !== actorUserId ||
          priorMutation.requestFingerprint !== requestFingerprint
        ) {
          throw new ConflictException(
            'clientMutationId was already used for another mutation',
          );
        }
        const current = await manager.findOneBy(PersonEntity, {
          personId,
          spaceId,
        });
        if (!current) throw new NotFoundException('Person not found');
        await this.privacyService.requireFullAccessForPeople(
          spaceId,
          [current],
          actorUserId,
          manager,
        );
        return JSON.parse(priorMutation.responseJson) as PersonEntity;
      }

      const person = await manager.findOneBy(PersonEntity, {
        personId,
        spaceId,
      });
      if (!person) {
        throw new NotFoundException('Person not found');
      }
      await this.privacyService.requireFullAccessForPeople(
        spaceId,
        [person],
        actorUserId,
        manager,
      );
      if (person.version !== expectedVersion) {
        throw new ConflictException({
          message: 'Person was changed by another contributor',
          details: {
            personId: person.personId,
            version: person.version,
            lifeStatus: person.lifeStatus,
            deceasedAt: person.deceasedAt,
            fullName: person.fullName,
            nickName: person.nickName,
            gender: person.gender,
            birthDate: person.birthDate,
            birthPlace: person.birthPlace,
            deathPlace: person.deathPlace,
            notes: person.notes,
            updatedAt: person.updatedAt,
          },
        });
      }

      const nextBirthDate =
        normalizedProfile.birthDate === undefined
          ? person.birthDate
          : normalizedProfile.birthDate;
      if (
        nextBirthDate &&
        person.deceasedAt &&
        person.deceasedAt < nextBirthDate
      ) {
        throw new BadRequestException(
          'birthDate must be on or before deceasedAt',
        );
      }
      const beforePerson = JSON.stringify(person);
      const updateResult = await manager
        .createQueryBuilder()
        .update(PersonEntity)
        .set({
          fullName: normalizedProfile.fullName ?? person.fullName,
          firstName: normalizedProfile.fullName ?? person.firstName,
          lastName:
            normalizedProfile.fullName === undefined ? person.lastName : null,
          nickName:
            normalizedProfile.nickName === undefined
              ? person.nickName
              : normalizedProfile.nickName || null,
          gender: normalizedProfile.gender ?? person.gender,
          birthDate: nextBirthDate,
          birthPlace:
            normalizedProfile.birthPlace === undefined
              ? person.birthPlace
              : normalizedProfile.birthPlace,
          deathPlace:
            normalizedProfile.deathPlace === undefined
              ? person.deathPlace
              : normalizedProfile.deathPlace,
          notes:
            normalizedProfile.notes === undefined
              ? person.notes
              : normalizedProfile.notes,
          version: () => 'version + 1',
        })
        .where('personId = :personId', { personId })
        .andWhere('spaceId = :spaceId', { spaceId })
        .andWhere('version = :expectedVersion', { expectedVersion })
        .execute();
      if (updateResult.affected !== 1) {
        const current = await manager.findOneBy(PersonEntity, {
          personId,
          spaceId,
        });
        throw new ConflictException({
          message: 'Person was changed by another contributor',
          details: current
            ? {
                personId: current.personId,
                version: current.version,
                lifeStatus: current.lifeStatus,
                deceasedAt: current.deceasedAt,
                fullName: current.fullName,
                nickName: current.nickName,
                gender: current.gender,
                birthDate: current.birthDate,
                birthPlace: current.birthPlace,
                deathPlace: current.deathPlace,
                notes: current.notes,
                updatedAt: current.updatedAt,
              }
            : null,
        });
      }

      const savedPerson = await manager.findOneByOrFail(PersonEntity, {
        personId,
        spaceId,
      });
      await manager.save(
        manager.create(ChangeLogEntity, {
          spaceId,
          actorUserId,
          entityType: 'PERSON',
          entityId: savedPerson.personId,
          operation: 'UPDATE',
          note: 'Update offline-editable profile',
          beforeJson: beforePerson,
          afterJson: JSON.stringify(savedPerson),
        }),
      );
      await manager.save(
        manager.create(ClientMutationEntity, {
          clientMutationId,
          actorUserId,
          spaceId,
          operation: 'UPDATE_PROFILE',
          requestFingerprint,
          responseJson: JSON.stringify(savedPerson),
        }),
      );
      return savedPerson;
    });
  }

  private personReadResult(
    person: PersonEntity,
    decision: PersonPrivacyDecision,
  ) {
    return {
      ...this.privacyService.redact(person, decision),
      privacyAccess: decision.access,
      canManageVisibility: decision.canManageVisibility,
    };
  }
}
