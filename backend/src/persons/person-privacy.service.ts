import {
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { EntityManager, In, Repository } from 'typeorm';
import { UserPersonClaimEntity } from '../claims/user-person-claim.entity';
import { SpaceMemberEntity } from '../spaces/space-member.entity';
import { PersonEntity } from './person.entity';

export type PersonPrivacyAccess = 'FULL' | 'STRUCTURE' | 'MINIMUM';

export interface PersonPrivacyDecision {
  access: PersonPrivacyAccess;
  canManageVisibility: boolean;
}

export function privacyAccessForVisibility(
  visibility: PersonEntity['visibility'],
): PersonPrivacyAccess {
  if (visibility === 'PRIVATE') return 'MINIMUM';
  if (visibility === 'LIMITED') return 'STRUCTURE';
  return 'FULL';
}

@Injectable()
export class PersonPrivacyService {
  constructor(
    @InjectRepository(UserPersonClaimEntity)
    private readonly claimsRepo: Repository<UserPersonClaimEntity>,
    @InjectRepository(SpaceMemberEntity)
    private readonly membersRepo: Repository<SpaceMemberEntity>,
  ) {}

  async decisionsForPeople(
    spaceId: string,
    people: PersonEntity[],
    actorUserId: string,
    manager: EntityManager = this.claimsRepo.manager,
  ) {
    const membership = await manager.findOneBy(SpaceMemberEntity, {
      spaceId,
      userId: actorUserId,
    });
    if (!membership) {
      throw new ForbiddenException('Actor is not a member of this space');
    }
    const personIds = people.map((person) => person.personId);
    const claims = personIds.length
      ? await manager.find(UserPersonClaimEntity, {
          where: {
            spaceId,
            personId: In(personIds),
            status: 'VERIFIED',
          },
          select: ['personId', 'userId'],
        })
      : [];
    const claimantsByPerson = new Map<string, Set<string>>();
    for (const claim of claims) {
      const claimants = claimantsByPerson.get(claim.personId) ?? new Set();
      claimants.add(claim.userId);
      claimantsByPerson.set(claim.personId, claimants);
    }

    return new Map(
      people.map((person) => {
        const claimants = claimantsByPerson.get(person.personId);
        const claimedByActor = claimants?.has(actorUserId) === true;
        const hasVerifiedClaim = Boolean(claimants?.size);
        const temporaryManager =
          !hasVerifiedClaim &&
          (membership.role === 'OWNER' || membership.role === 'ADMIN');
        const roleAwareAccess =
          person.visibility === 'LIMITED' && membership.role !== 'VIEWER'
            ? ('FULL' as const)
            : privacyAccessForVisibility(person.visibility);
        return [
          person.personId,
          {
            access:
              claimedByActor || temporaryManager
                ? ('FULL' as const)
                : roleAwareAccess,
            canManageVisibility: claimedByActor || temporaryManager,
          },
        ];
      }),
    );
  }

  async decisionForPerson(
    spaceId: string,
    person: PersonEntity,
    actorUserId: string,
    manager?: EntityManager,
  ) {
    const decisions = await this.decisionsForPeople(
      spaceId,
      [person],
      actorUserId,
      manager,
    );
    return decisions.get(person.personId) as PersonPrivacyDecision;
  }

  async findPersonWithDecision(
    spaceId: string,
    personId: string,
    actorUserId: string,
  ) {
    const person = await this.claimsRepo.manager.findOneBy(PersonEntity, {
      spaceId,
      personId,
      isDeleted: false,
    });
    if (!person) throw new NotFoundException('Person not found');
    return {
      person,
      decision: await this.decisionForPerson(spaceId, person, actorUserId),
    };
  }

  async requireFullAccessForPeople(
    spaceId: string,
    people: PersonEntity[],
    actorUserId: string,
    manager?: EntityManager,
  ) {
    const decisions = await this.decisionsForPeople(
      spaceId,
      people,
      actorUserId,
      manager,
    );
    if (
      people.some((person) => decisions.get(person.personId)?.access !== 'FULL')
    ) {
      throw new ForbiddenException(
        'Full person access is required for this action',
      );
    }
    return decisions;
  }

  redact(person: PersonEntity, decision: PersonPrivacyDecision): PersonEntity {
    if (decision.access === 'FULL') return person;
    const common = {
      ...person,
      title: null,
      firstName: null,
      lastName: null,
      suffix: null,
      gender: null,
      birthDate: null,
      birthPlace: null,
      deathDate: null,
      deathPlace: null,
      idNumber: null,
      notes: null,
      lifeStatus: 'UNKNOWN' as const,
      deceasedAt: null,
    };
    if (decision.access === 'STRUCTURE') return common;
    return {
      ...common,
      fullName: 'Anggota keluarga',
      nickName: null,
    };
  }
}
