import { BadRequestException, Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { EntityManager, Repository } from 'typeorm';
import { PersonEntity } from '../persons/person.entity';
import { RelationshipEntity } from '../persons/relationship.entity';
import { UserPersonClaimEntity } from '../claims/user-person-claim.entity';
import { EditProposalEntity } from '../archive/edit-proposal.entity';
import { FactSourceEntity } from '../archive/fact-source.entity';
import { MediaItemEntity } from '../archive/media-item.entity';
import { ChangeLogEntity } from '../changes/change-log.entity';

type PortablePerson = Pick<
  PersonEntity,
  | 'personId'
  | 'fullName'
  | 'title'
  | 'firstName'
  | 'lastName'
  | 'suffix'
  | 'nickName'
  | 'gender'
  | 'birthDate'
  | 'birthPlace'
  | 'deathDate'
  | 'deathPlace'
  | 'notes'
  | 'lifeStatus'
  | 'deceasedAt'
>;

interface GedcomIndividual {
  ref: string;
  name: string;
  gender: string | null;
  birthDate: string | null;
  birthPlace: string | null;
  deathDate: string | null;
  deathPlace: string | null;
}

interface GedcomFamily {
  husband?: string;
  wife?: string;
  children: string[];
}

@Injectable()
export class ExportService {
  constructor(
    @InjectRepository(PersonEntity)
    private readonly personsRepo: Repository<PersonEntity>,
    @InjectRepository(RelationshipEntity)
    private readonly relationsRepo: Repository<RelationshipEntity>,
    @InjectRepository(UserPersonClaimEntity)
    private readonly claimsRepo: Repository<UserPersonClaimEntity>,
    @InjectRepository(FactSourceEntity)
    private readonly sourcesRepo: Repository<FactSourceEntity>,
    @InjectRepository(MediaItemEntity)
    private readonly mediaRepo: Repository<MediaItemEntity>,
    @InjectRepository(EditProposalEntity)
    private readonly proposalsRepo: Repository<EditProposalEntity>,
  ) {}

  async exportSpace(spaceId: string) {
    const persons = await this.personsRepo.find({
      where: { spaceId, isDeleted: false },
      order: { createdAt: 'ASC' },
      select: [
        'personId',
        'fullName',
        'title',
        'firstName',
        'lastName',
        'suffix',
        'nickName',
        'gender',
        'birthDate',
        'birthPlace',
        'deathDate',
        'deathPlace',
        'notes',
        'lifeStatus',
        'deceasedAt',
        'version',
        'createdAt',
        'updatedAt',
      ],
    });
    const relationships = await this.relationsRepo.find({
      where: { spaceId },
      order: { createdAt: 'ASC' },
    });
    const claims = await this.claimsRepo.find({
      where: { spaceId },
      order: { requestedAt: 'ASC' },
      select: ['claimId', 'status', 'userId', 'personId', 'requestedAt'],
    });
    const [sources, media, proposals] = await Promise.all([
      this.sourcesRepo.find({
        where: { spaceId },
        order: { createdAt: 'ASC' },
      }),
      this.mediaRepo.find({ where: { spaceId }, order: { createdAt: 'ASC' } }),
      this.proposalsRepo.find({
        where: { spaceId },
        order: { createdAt: 'ASC' },
      }),
    ]);
    return {
      spaceId,
      persons,
      relationships,
      sources,
      media,
      proposals,
      claims: claims.map((claim) => ({
        claimId: claim.claimId,
        status: claim.status,
        userId: claim.userId,
        personId: claim.personId,
        createdAt: claim.requestedAt,
      })),
    };
  }

  async createBackup(spaceId: string) {
    const exported = await this.exportSpace(spaceId);
    return {
      format: 'familyroot-backup',
      schemaVersion: 1,
      exportedAt: new Date().toISOString(),
      sourceSpaceId: spaceId,
      persons: exported.persons,
      relationships: exported.relationships,
      sources: exported.sources,
      media: exported.media,
    };
  }

  async exportGedcom(spaceId: string) {
    const { persons, relationships } = await this.exportSpace(spaceId);
    const refs = new Map(
      persons.map((person, index) => [person.personId, `@I${index + 1}@`]),
    );
    const lines = [
      '0 HEAD',
      '1 SOUR FamilyRoot',
      '1 GEDC',
      '2 VERS 5.5.1',
      '1 CHAR UTF-8',
    ];
    persons.forEach((person) => {
      const ref = refs.get(person.personId)!;
      const surname = person.lastName?.trim();
      const given =
        [
          person.title,
          person.firstName,
          person.nickName && !person.firstName ? person.nickName : null,
        ]
          .filter(Boolean)
          .join(' ')
          .trim() || person.fullName;
      lines.push(
        `0 ${ref} INDI`,
        `1 NAME ${this.escapeGedcom(given)}${surname ? ` /${this.escapeGedcom(surname)}/` : ''}`,
      );
      if (person.gender)
        lines.push(
          `1 SEX ${person.gender === 'MALE' ? 'M' : person.gender === 'FEMALE' ? 'F' : 'U'}`,
        );
      this.appendEvent(lines, 'BIRT', person.birthDate, person.birthPlace);
      this.appendEvent(
        lines,
        'DEAT',
        person.deathDate ?? person.deceasedAt,
        person.deathPlace,
      );
      if (person.notes) lines.push(`1 NOTE ${this.escapeGedcom(person.notes)}`);
    });

    let familyIndex = 1;
    const spousePairs = relationships.filter(
      (relation) => relation.type === 'SPOUSE',
    );
    spousePairs.forEach((relation) => {
      const left = refs.get(relation.fromPersonId);
      const right = refs.get(relation.toPersonId);
      if (!left || !right) return;
      lines.push(
        `0 @F${familyIndex++}@ FAM`,
        `1 HUSB ${left}`,
        `1 WIFE ${right}`,
      );
      if (relation.startDate)
        lines.push('1 MARR', `2 DATE ${this.toGedcomDate(relation.startDate)}`);
    });
    const children = new Map<string, string[]>();
    relationships
      .filter((relation) => relation.type === 'PARENT_CHILD')
      .forEach((relation) => {
        const values = children.get(relation.toPersonId) ?? [];
        values.push(relation.fromPersonId);
        children.set(relation.toPersonId, values);
      });
    children.forEach((parentIds, childId) => {
      const child = refs.get(childId);
      const parents = parentIds
        .map((id) => refs.get(id))
        .filter((value): value is string => Boolean(value));
      if (!child || parents.length === 0) return;
      lines.push(`0 @F${familyIndex++}@ FAM`, `1 HUSB ${parents[0]}`);
      if (parents[1]) lines.push(`1 WIFE ${parents[1]}`);
      lines.push(`1 CHIL ${child}`);
    });
    lines.push('0 TRLR');
    return {
      fileName: `familyroot-${spaceId}.ged`,
      mimeType: 'text/vnd.familysearch.gedcom',
      content: `${lines.join('\r\n')}\r\n`,
    };
  }

  async importGedcom(spaceId: string, content: string, actorUserId: string) {
    const parsed = this.parseGedcom(content);
    if (parsed.individuals.length === 0)
      throw new BadRequestException('GEDCOM contains no individuals');
    return this.personsRepo.manager.transaction(async (manager) => {
      await this.assertEmptyTarget(manager, spaceId);
      const idMap = new Map<string, string>();
      for (const item of parsed.individuals) {
        const name = item.name.trim();
        const parts = name.split(/\s+/);
        const saved = await manager.save(
          manager.create(PersonEntity, {
            spaceId,
            fullName: name,
            firstName: parts[0] || name,
            lastName: parts.length > 1 ? parts.slice(1).join(' ') : null,
            nickName: parts[0] || name,
            gender: item.gender,
            birthDate: item.birthDate,
            birthPlace: item.birthPlace,
            deathDate: item.deathDate,
            deathPlace: item.deathPlace,
            lifeStatus: item.deathDate ? 'DECEASED' : 'UNKNOWN',
            deceasedAt: item.deathDate,
          }),
        );
        idMap.set(item.ref, saved.personId);
      }
      let relationshipCount = 0;
      const seen = new Set<string>();
      for (const family of parsed.families) {
        const parents = [family.husband, family.wife].filter(
          (value): value is string => Boolean(value),
        );
        if (parents.length === 2) {
          relationshipCount += await this.saveRelation(
            manager,
            spaceId,
            idMap,
            seen,
            'SPOUSE',
            parents[0],
            parents[1],
            'MARRIED',
          );
        }
        for (const child of family.children) {
          for (const parent of parents) {
            relationshipCount += await this.saveRelation(
              manager,
              spaceId,
              idMap,
              seen,
              'PARENT_CHILD',
              parent,
              child,
              'BIOLOGICAL',
            );
          }
        }
      }
      await this.saveImportAudit(
        manager,
        spaceId,
        actorUserId,
        parsed.individuals.length,
        relationshipCount,
        'GEDCOM',
      );
      return { personCount: parsed.individuals.length, relationshipCount };
    });
  }

  async restoreBackup(
    spaceId: string,
    raw: Record<string, unknown>,
    actorUserId: string,
  ) {
    if (raw.format !== 'familyroot-backup' || raw.schemaVersion !== 1) {
      throw new BadRequestException(
        'Unsupported backup format or schemaVersion',
      );
    }
    const people = Array.isArray(raw.persons) ? raw.persons : [];
    const relationships = Array.isArray(raw.relationships)
      ? raw.relationships
      : [];
    if (
      people.length === 0 ||
      people.length > 10_000 ||
      relationships.length > 30_000
    ) {
      throw new BadRequestException('Backup size is invalid');
    }
    return this.personsRepo.manager.transaction(async (manager) => {
      await this.assertEmptyTarget(manager, spaceId);
      const idMap = new Map<string, string>();
      for (const rawPerson of people) {
        const person = this.portablePerson(rawPerson);
        const saved = await manager.save(
          manager.create(PersonEntity, {
            ...person,
            personId: undefined,
            spaceId,
            version: 1,
            isDeleted: false,
          }),
        );
        idMap.set(person.personId, saved.personId);
      }
      let relationshipCount = 0;
      const seen = new Set<string>();
      for (const value of relationships) {
        if (!value || typeof value !== 'object') continue;
        const relation = value as Record<string, unknown>;
        const type =
          relation.type === 'SPOUSE'
            ? 'SPOUSE'
            : relation.type === 'PARENT_CHILD'
              ? 'PARENT_CHILD'
              : null;
        if (
          !type ||
          typeof relation.fromPersonId !== 'string' ||
          typeof relation.toPersonId !== 'string'
        )
          continue;
        const allowedMeta = [
          'BIOLOGICAL',
          'ADOPTIVE',
          'STEP',
          'MARRIED',
          'DIVORCED',
          'WIDOWED',
        ];
        const meta =
          typeof relation.meta === 'string' &&
          allowedMeta.includes(relation.meta)
            ? relation.meta
            : null;
        relationshipCount += await this.saveRelation(
          manager,
          spaceId,
          idMap,
          seen,
          type,
          relation.fromPersonId,
          relation.toPersonId,
          meta,
        );
      }
      await this.saveImportAudit(
        manager,
        spaceId,
        actorUserId,
        people.length,
        relationshipCount,
        'BACKUP',
      );
      return { personCount: people.length, relationshipCount };
    });
  }

  private portablePerson(value: unknown): PortablePerson {
    if (!value || typeof value !== 'object')
      throw new BadRequestException('Invalid person in backup');
    const item = value as Record<string, unknown>;
    if (
      typeof item.personId !== 'string' ||
      typeof item.fullName !== 'string' ||
      !item.fullName.trim()
    ) {
      throw new BadRequestException('Invalid person identity in backup');
    }
    const text = (key: string) =>
      typeof item[key] === 'string' ? String(item[key]).slice(0, 10_000) : null;
    const status =
      item.lifeStatus === 'ALIVE' || item.lifeStatus === 'DECEASED'
        ? item.lifeStatus
        : 'UNKNOWN';
    return {
      personId: item.personId,
      fullName: item.fullName.trim().slice(0, 500),
      title: text('title'),
      firstName: text('firstName'),
      lastName: text('lastName'),
      suffix: text('suffix'),
      nickName: text('nickName'),
      gender: text('gender'),
      birthDate: text('birthDate'),
      birthPlace: text('birthPlace'),
      deathDate: text('deathDate'),
      deathPlace: text('deathPlace'),
      notes: text('notes'),
      lifeStatus: status,
      deceasedAt: text('deceasedAt'),
    };
  }

  private async assertEmptyTarget(manager: EntityManager, spaceId: string) {
    if (
      await manager.count(PersonEntity, {
        where: { spaceId, isDeleted: false },
      })
    ) {
      throw new BadRequestException(
        'Restore target Family Space must be empty',
      );
    }
  }

  private async saveRelation(
    manager: EntityManager,
    spaceId: string,
    idMap: Map<string, string>,
    seen: Set<string>,
    type: 'PARENT_CHILD' | 'SPOUSE',
    fromRef: string,
    toRef: string,
    meta: string | null,
  ) {
    let from = idMap.get(fromRef);
    let to = idMap.get(toRef);
    if (!from || !to || from === to) return 0;
    if (type === 'SPOUSE' && from > to) [from, to] = [to, from];
    const key = `${type}:${from}:${to}`;
    if (seen.has(key)) return 0;
    seen.add(key);
    await manager.save(
      manager.create(RelationshipEntity, {
        spaceId,
        type,
        fromPersonId: from,
        toPersonId: to,
        meta: meta as RelationshipEntity['meta'],
      }),
    );
    return 1;
  }

  private async saveImportAudit(
    manager: EntityManager,
    spaceId: string,
    actorUserId: string,
    personCount: number,
    relationshipCount: number,
    source: string,
  ) {
    await manager.save(
      manager.create(ChangeLogEntity, {
        spaceId,
        actorUserId,
        entityType: 'SPACE',
        entityId: spaceId,
        operation: 'UPDATE',
        note: `Import ${source}: ${personCount} people, ${relationshipCount} relationships`,
      }),
    );
  }

  private appendEvent(
    lines: string[],
    tag: string,
    date: string | null,
    place: string | null,
  ) {
    if (!date && !place) return;
    lines.push(`1 ${tag}`);
    if (date) lines.push(`2 DATE ${this.toGedcomDate(date)}`);
    if (place) lines.push(`2 PLAC ${this.escapeGedcom(place)}`);
  }

  private escapeGedcom(value: string) {
    return value.replace(/[\r\n]+/g, ' ').trim();
  }

  private toGedcomDate(value: string) {
    const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(value);
    if (!match) return value;
    const months = [
      'JAN',
      'FEB',
      'MAR',
      'APR',
      'MAY',
      'JUN',
      'JUL',
      'AUG',
      'SEP',
      'OCT',
      'NOV',
      'DEC',
    ];
    return `${Number(match[3])} ${months[Number(match[2]) - 1]} ${match[1]}`;
  }

  private fromGedcomDate(value: string) {
    const months = [
      'JAN',
      'FEB',
      'MAR',
      'APR',
      'MAY',
      'JUN',
      'JUL',
      'AUG',
      'SEP',
      'OCT',
      'NOV',
      'DEC',
    ];
    const match = /^(\d{1,2})\s+([A-Z]{3})\s+(\d{4})$/i.exec(value.trim());
    if (!match) return null;
    const month = months.indexOf(match[2].toUpperCase()) + 1;
    if (!month) return null;
    return `${match[3]}-${String(month).padStart(2, '0')}-${String(Number(match[1])).padStart(2, '0')}`;
  }

  private parseGedcom(content: string) {
    const individuals: GedcomIndividual[] = [];
    const families: GedcomFamily[] = [];
    let individual: GedcomIndividual | null = null;
    let family: GedcomFamily | null = null;
    let event: 'BIRT' | 'DEAT' | null = null;
    content
      .replace(/^\uFEFF/, '')
      .split(/\r?\n/)
      .forEach((rawLine) => {
        const match =
          /^(\d+)\s+(?:(@[^@]+@)\s+)?([A-Z0-9_]+)(?:\s+(.*))?$/i.exec(
            rawLine.trim(),
          );
        if (!match) return;
        const level = Number(match[1]);
        const ref = match[2];
        const tag = match[3].toUpperCase();
        const value = (match[4] ?? '').trim();
        if (level === 0) {
          individual = null;
          family = null;
          event = null;
          if (tag === 'INDI' && ref) {
            individual = {
              ref,
              name: '',
              gender: null,
              birthDate: null,
              birthPlace: null,
              deathDate: null,
              deathPlace: null,
            };
            individuals.push(individual);
          } else if (tag === 'FAM') {
            family = { children: [] };
            families.push(family);
          }
          return;
        }
        if (individual) {
          if (level === 1 && tag === 'NAME')
            individual.name = value
              .replace(/\//g, '')
              .replace(/\s+/g, ' ')
              .trim();
          else if (level === 1 && tag === 'SEX')
            individual.gender =
              value === 'M' ? 'MALE' : value === 'F' ? 'FEMALE' : 'UNKNOWN';
          else if (level === 1 && (tag === 'BIRT' || tag === 'DEAT'))
            event = tag;
          else if (level === 2 && tag === 'DATE' && event)
            individual[event === 'BIRT' ? 'birthDate' : 'deathDate'] =
              this.fromGedcomDate(value);
          else if (level === 2 && tag === 'PLAC' && event)
            individual[event === 'BIRT' ? 'birthPlace' : 'deathPlace'] = value;
        } else if (family && level === 1) {
          if (tag === 'HUSB') family.husband = value;
          else if (tag === 'WIFE') family.wife = value;
          else if (tag === 'CHIL') family.children.push(value);
        }
      });
    individuals.forEach((item) => {
      if (!item.name) item.name = `Unknown ${item.ref}`;
    });
    return { individuals, families };
  }
}
