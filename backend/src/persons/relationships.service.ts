import {
  BadRequestException,
  ConflictException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { In, Repository } from 'typeorm';
import {
  isCareRelationshipMeta,
  isLineageParentChildMeta,
  RelationshipEntity,
} from './relationship.entity';
import { ChangeLogEntity } from '../changes/change-log.entity';
import { PersonEntity } from './person.entity';
import { databaseErrorMessage } from '../common/database-error';
import { ClientMutationEntity } from './client-mutation.entity';
import { PersonPrivacyService } from './person-privacy.service';

@Injectable()
export class RelationshipsService {
  constructor(
    @InjectRepository(RelationshipEntity)
    private readonly relationsRepo: Repository<RelationshipEntity>,
    @InjectRepository(PersonEntity)
    private readonly personsRepo: Repository<PersonEntity>,
    @InjectRepository(ChangeLogEntity)
    private readonly changeRepo: Repository<ChangeLogEntity>,
    private readonly privacyService: PersonPrivacyService,
  ) {}

  private async hasParentChildPath(
    spaceId: string,
    ancestorId: string,
    descendantId: string,
  ): Promise<boolean> {
    const parentage = await this.relationsRepo.find({
      where: { spaceId, type: 'PARENT_CHILD' },
      select: ['fromPersonId', 'toPersonId'],
    });
    const childrenByParent = new Map<string, string[]>();
    parentage.forEach((relationship) => {
      if (!isLineageParentChildMeta(relationship.meta)) return;
      const children = childrenByParent.get(relationship.fromPersonId) ?? [];
      children.push(relationship.toPersonId);
      childrenByParent.set(relationship.fromPersonId, children);
    });
    const pending = [ancestorId];
    const visited = new Set([ancestorId]);
    while (pending.length > 0) {
      const current = pending.shift();
      if (!current) continue;
      for (const childId of childrenByParent.get(current) ?? []) {
        if (childId === descendantId) return true;
        if (!visited.has(childId)) {
          visited.add(childId);
          pending.push(childId);
        }
      }
    }
    return false;
  }

  async findByPerson(spaceId: string, personId: string, actorUserId: string) {
    const parentRelationships = await this.relationsRepo.find({
      where: { spaceId, type: 'PARENT_CHILD', toPersonId: personId },
      order: { createdAt: 'DESC' },
      select: [
        'relationshipId',
        'type',
        'fromPersonId',
        'toPersonId',
        'meta',
        'createdAt',
        'startDate',
        'endDate',
        'careContext',
      ],
    });

    const childRelationships = await this.relationsRepo.find({
      where: { spaceId, type: 'PARENT_CHILD', fromPersonId: personId },
      order: { createdAt: 'DESC' },
      select: [
        'relationshipId',
        'type',
        'fromPersonId',
        'toPersonId',
        'meta',
        'createdAt',
        'startDate',
        'endDate',
        'careContext',
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
        'type',
        'fromPersonId',
        'toPersonId',
        'meta',
        'createdAt',
        'startDate',
        'endDate',
        'careContext',
      ],
    });

    const redacted = await this.redactRelationshipDetails(
      spaceId,
      [...parentRelationships, ...childRelationships, ...spouses],
      actorUserId,
    );
    const byId = new Map(
      redacted.map((relationship) => [
        relationship.relationshipId,
        relationship,
      ]),
    );
    return {
      personId,
      parents: parentRelationships
        .filter((relationship) => !isCareRelationshipMeta(relationship.meta))
        .map((relationship) => byId.get(relationship.relationshipId)),
      children: childRelationships
        .filter((relationship) => !isCareRelationshipMeta(relationship.meta))
        .map((relationship) => byId.get(relationship.relationshipId)),
      caregivers: parentRelationships
        .filter((relationship) => isCareRelationshipMeta(relationship.meta))
        .map((relationship) => byId.get(relationship.relationshipId)),
      careRecipients: childRelationships
        .filter((relationship) => isCareRelationshipMeta(relationship.meta))
        .map((relationship) => byId.get(relationship.relationshipId)),
      spouses: spouses.map((relationship) =>
        byId.get(relationship.relationshipId),
      ),
    };
  }

  async findAll(spaceId: string, actorUserId: string) {
    const relationships = await this.relationsRepo.find({
      where: { spaceId },
      order: { createdAt: 'DESC' },
      select: [
        'relationshipId',
        'type',
        'fromPersonId',
        'toPersonId',
        'meta',
        'startDate',
        'endDate',
        'careContext',
        'createdAt',
      ],
    });
    return this.redactRelationshipDetails(spaceId, relationships, actorUserId);
  }

  async remove(
    spaceId: string,
    relationshipId: string,
    actorUserId: string,
    clientMutationId: string,
  ) {
    const requestFingerprint = JSON.stringify({ spaceId, relationshipId });
    return this.relationsRepo.manager.transaction(async (manager) => {
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
        return JSON.parse(priorMutation.responseJson) as {
          relationshipId: string;
          deleted: boolean;
        };
      }
      const relationship = await manager.findOneBy(RelationshipEntity, {
        spaceId,
        relationshipId,
      });
      if (!relationship) throw new NotFoundException('Relationship not found');
      const people = await manager.findBy(PersonEntity, {
        spaceId,
        personId: In([relationship.fromPersonId, relationship.toPersonId]),
        isDeleted: false,
      });
      await this.privacyService.requireFullAccessForPeople(
        spaceId,
        people,
        actorUserId,
        manager,
      );

      await manager.remove(relationship);
      await manager.save(
        manager.create(ChangeLogEntity, {
          spaceId,
          actorUserId,
          entityType: 'RELATIONSHIP',
          entityId: relationshipId,
          operation: 'DELETE',
          note: `Delete ${relationship.type.toLowerCase()} relationship`,
          beforeJson: JSON.stringify(relationship),
        }),
      );
      const response = { relationshipId, deleted: true };
      await manager.save(
        manager.create(ClientMutationEntity, {
          clientMutationId,
          actorUserId,
          spaceId,
          operation: 'DELETE_RELATIONSHIP',
          requestFingerprint,
          responseJson: JSON.stringify(response),
        }),
      );
      return response;
    });
  }

  async findPath(
    spaceId: string,
    fromPersonId: string,
    toPersonId: string,
    actorUserId: string,
  ) {
    const people = await this.personsRepo.find({
      where: [
        { spaceId, personId: fromPersonId, isDeleted: false },
        { spaceId, personId: toPersonId, isDeleted: false },
      ],
      select: ['personId', 'fullName', 'visibility'],
    });
    if (people.length < 2 && fromPersonId !== toPersonId) {
      throw new BadRequestException(
        'Both people must exist in this Family Space',
      );
    }
    if (fromPersonId === toPersonId) {
      const decisions = await this.privacyService.decisionsForPeople(
        spaceId,
        people,
        actorUserId,
      );
      return {
        found: true,
        people: people.map((person) => ({
          personId: person.personId,
          fullName: this.privacyService.redact(
            person,
            decisions.get(person.personId)!,
          ).fullName,
        })),
        edges: [],
      };
    }

    const relationships = await this.relationsRepo.find({
      where: { spaceId },
      select: [
        'relationshipId',
        'type',
        'fromPersonId',
        'toPersonId',
        'meta',
        'createdAt',
      ],
    });
    const personIds = new Set<string>();
    relationships.forEach((rel) => {
      personIds.add(rel.fromPersonId);
      personIds.add(rel.toPersonId);
    });
    const personNames = await this.personsRepo.find({
      where: [...personIds].map((personId) => ({
        spaceId,
        personId,
        isDeleted: false,
      })),
      select: ['personId', 'fullName', 'visibility'],
    });
    const pathDecisions = await this.privacyService.decisionsForPeople(
      spaceId,
      personNames,
      actorUserId,
    );
    const personById = new Map(
      personNames.map((person) => [person.personId, person]),
    );
    const adjacency = new Map<
      string,
      Array<{
        next: string;
        relationship: RelationshipEntity;
        direction: string;
      }>
    >();
    const addEdge = (
      from: string,
      next: string,
      relationship: RelationshipEntity,
      direction: string,
    ) => {
      adjacency.set(from, [
        ...(adjacency.get(from) ?? []),
        { next, relationship, direction },
      ]);
    };
    relationships.forEach((rel) => {
      addEdge(rel.fromPersonId, rel.toPersonId, rel, 'FORWARD');
      addEdge(rel.toPersonId, rel.fromPersonId, rel, 'REVERSE');
    });

    const queue = [fromPersonId];
    const visited = new Set([fromPersonId]);
    const previous = new Map<
      string,
      { personId: string; relationship: RelationshipEntity; direction: string }
    >();

    while (queue.length > 0) {
      const current = queue.shift()!;
      for (const edge of adjacency.get(current) ?? []) {
        if (visited.has(edge.next)) continue;
        visited.add(edge.next);
        previous.set(edge.next, {
          personId: current,
          relationship: edge.relationship,
          direction: edge.direction,
        });
        if (edge.next === toPersonId) queue.length = 0;
        else queue.push(edge.next);
      }
    }

    if (!previous.has(toPersonId)) {
      return { found: false, people: [], edges: [] };
    }

    const pathPeople = [toPersonId];
    const edges: Array<{
      relationshipId: string;
      type: RelationshipEntity['type'];
      fromPersonId: string;
      toPersonId: string;
      meta: RelationshipEntity['meta'];
      direction: string;
    }> = [];
    let cursor = toPersonId;
    while (cursor !== fromPersonId) {
      const prev = previous.get(cursor)!;
      edges.unshift({
        relationshipId: prev.relationship.relationshipId,
        type: prev.relationship.type,
        fromPersonId: prev.relationship.fromPersonId,
        toPersonId: prev.relationship.toPersonId,
        meta: prev.relationship.meta,
        direction: prev.direction,
      });
      cursor = prev.personId;
      pathPeople.unshift(cursor);
    }

    return {
      found: true,
      people: pathPeople.map((personId) => ({
        personId,
        fullName: personById.get(personId)
          ? this.privacyService.redact(
              personById.get(personId) as PersonEntity,
              pathDecisions.get(personId)!,
            ).fullName
          : 'Anggota keluarga',
      })),
      edges,
    };
  }

  async createSpouse(
    spaceId: string,
    personAId: string,
    personBId: string,
    meta: 'MARRIED' | 'DIVORCED' | 'WIDOWED',
    startDate: string | null | undefined,
    endDate?: string | null,
    actorUserId?: string,
    clientMutationId?: string,
  ) {
    if (!actorUserId || !clientMutationId) {
      throw new BadRequestException('Mutation identity is required');
    }
    const [fromPersonId, toPersonId] =
      personAId < personBId ? [personAId, personBId] : [personBId, personAId];
    const requestFingerprint = JSON.stringify({
      spaceId,
      fromPersonId,
      toPersonId,
      meta,
      startDate: startDate ?? null,
      endDate: endDate ?? null,
    });
    const priorMutation = await this.relationsRepo.manager.findOne(
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
    if (personAId === personBId) {
      throw new BadRequestException('Spouse cannot be the same person');
    }
    if (endDate && startDate && endDate < startDate) {
      throw new BadRequestException('endDate must be >= startDate');
    }

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
    await this.privacyService.requireFullAccessForPeople(
      spaceId,
      [personA, personB],
      actorUserId,
    );
    const isAncestorPair =
      (await this.hasParentChildPath(spaceId, personAId, personBId)) ||
      (await this.hasParentChildPath(spaceId, personBId, personAId));
    if (isAncestorPair) {
      throw new BadRequestException(
        'Spouse relationship cannot be created between ancestor and descendant',
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
      startDate: startDate ?? null,
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
        await manager.save(
          manager.create(ClientMutationEntity, {
            clientMutationId,
            actorUserId,
            spaceId,
            operation: 'ADD_SPOUSE',
            requestFingerprint,
            responseJson: JSON.stringify(savedRelation),
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

  private async redactRelationshipDetails(
    spaceId: string,
    relationships: RelationshipEntity[],
    actorUserId: string,
  ) {
    const personIds = [
      ...new Set(
        relationships.flatMap((relationship) => [
          relationship.fromPersonId,
          relationship.toPersonId,
        ]),
      ),
    ];
    const people = personIds.length
      ? await this.personsRepo.findBy({
          spaceId,
          personId: In(personIds),
          isDeleted: false,
        })
      : [];
    const decisions = await this.privacyService.decisionsForPeople(
      spaceId,
      people,
      actorUserId,
    );
    return relationships.map((relationship) => {
      const full =
        decisions.get(relationship.fromPersonId)?.access === 'FULL' &&
        decisions.get(relationship.toPersonId)?.access === 'FULL';
      return full
        ? relationship
        : {
            ...relationship,
            startDate: null,
            endDate: null,
            careContext: null,
          };
    });
  }
}
