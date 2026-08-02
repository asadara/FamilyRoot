import {
  ConflictException,
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { In, Repository } from 'typeorm';
import { UserPersonClaimEntity } from './user-person-claim.entity';
import { ChangeLogEntity } from '../changes/change-log.entity';
import { PersonEntity } from '../persons/person.entity';
import { SpaceMemberEntity } from '../spaces/space-member.entity';
import { ClaimConfirmationEntity } from './claim-confirmation.entity';
import { PersonPrivacyService } from '../persons/person-privacy.service';

export const REQUIRED_CLAIM_CONFIRMATIONS = 2;

export function collectiveClaimStatus(
  confirmationCount: number,
): 'PENDING' | 'VERIFIED' {
  return confirmationCount >= REQUIRED_CLAIM_CONFIRMATIONS
    ? 'VERIFIED'
    : 'PENDING';
}

@Injectable()
export class ClaimsService {
  constructor(
    @InjectRepository(UserPersonClaimEntity)
    private readonly claimsRepo: Repository<UserPersonClaimEntity>,
    @InjectRepository(ChangeLogEntity)
    private readonly changeRepo: Repository<ChangeLogEntity>,
    @InjectRepository(PersonEntity)
    private readonly personsRepo: Repository<PersonEntity>,
    @InjectRepository(SpaceMemberEntity)
    private readonly membersRepo: Repository<SpaceMemberEntity>,
    @InjectRepository(ClaimConfirmationEntity)
    private readonly confirmationsRepo: Repository<ClaimConfirmationEntity>,
    private readonly privacyService: PersonPrivacyService,
  ) {}

  async create(
    spaceId: string,
    userId: string,
    personId: string,
    actorUserId?: string,
  ) {
    const [person, membership] = await Promise.all([
      this.personsRepo.findOneBy({ spaceId, personId, isDeleted: false }),
      this.membersRepo.findOneBy({ spaceId, userId }),
    ]);
    if (!person)
      throw new NotFoundException('Person not found in this Family Space');
    if (!membership)
      throw new NotFoundException('User is not a member of this Family Space');

    const existingVerified = await this.claimsRepo.findOne({
      where: { spaceId, userId, personId, status: 'VERIFIED' },
    });
    if (existingVerified) {
      throw new ConflictException('Claim already verified for this person');
    }

    return this.claimsRepo.manager.transaction(async (manager) => {
      const saved = await manager.save(
        manager.create(UserPersonClaimEntity, { spaceId, userId, personId }),
      );
      await manager.save(
        manager.create(ChangeLogEntity, {
          spaceId,
          actorUserId: actorUserId ?? 'SYSTEM',
          entityType: 'CLAIM',
          entityId: saved.claimId,
          operation: 'CREATE',
          note: 'Create claim',
          afterJson: JSON.stringify(saved),
        }),
      );
      return saved;
    });
  }

  async list(spaceId: string, actorUserId: string) {
    const claims = await this.claimsRepo.find({
      where: { spaceId },
      order: { requestedAt: 'DESC' },
    });

    if (claims.length === 0) return [];

    const [persons, members, confirmations] = await Promise.all([
      this.personsRepo.find({
        where: claims.map((claim) => ({
          spaceId,
          personId: claim.personId,
          isDeleted: false,
        })),
        select: ['personId', 'fullName', 'visibility'],
      }),
      this.membersRepo.find({
        where: claims.map((claim) => ({ spaceId, userId: claim.userId })),
        select: ['userId', 'role'],
      }),
      this.confirmationsRepo.find({
        where: { claimId: In(claims.map((claim) => claim.claimId)) },
        select: ['claimId'],
      }),
    ]);

    const personById = new Map(
      persons.map((person) => [person.personId, person]),
    );
    const privacyDecisions = await this.privacyService.decisionsForPeople(
      spaceId,
      persons,
      actorUserId,
    );
    const memberByUserId = new Map(
      members.map((member) => [member.userId, member]),
    );
    const confirmationCountByClaimId = confirmations.reduce(
      (counts, confirmation) => {
        counts.set(
          confirmation.claimId,
          (counts.get(confirmation.claimId) ?? 0) + 1,
        );
        return counts;
      },
      new Map<string, number>(),
    );

    return claims.map((claim) => {
      const recordedCount = confirmationCountByClaimId.get(claim.claimId) ?? 0;
      const isLegacyVerified =
        claim.status === 'VERIFIED' && recordedCount === 0;
      return {
        ...claim,
        personName: personById.get(claim.personId)
          ? this.privacyService.redact(
              personById.get(claim.personId) as PersonEntity,
              privacyDecisions.get(claim.personId)!,
            ).fullName
          : null,
        memberRole: memberByUserId.get(claim.userId)?.role ?? null,
        confirmationCount: isLegacyVerified
          ? REQUIRED_CLAIM_CONFIRMATIONS
          : recordedCount,
        required: REQUIRED_CLAIM_CONFIRMATIONS,
        verificationBasis: isLegacyVerified ? 'LEGACY' : 'COLLECTIVE',
      };
    });
  }

  async findMine(spaceId: string, userId: string) {
    const claims = await this.claimsRepo.find({
      where: { spaceId, userId },
      order: { requestedAt: 'DESC' },
    });
    const claim =
      claims.find((candidate) => candidate.status === 'VERIFIED') ?? claims[0];
    if (!claim) return { claim: null };

    const person = await this.personsRepo.findOne({
      where: { spaceId, personId: claim.personId, isDeleted: false },
      select: ['personId', 'fullName'],
    });
    return {
      claim: {
        ...claim,
        personName: person?.fullName ?? null,
      },
    };
  }

  async verify(claimId: string, actorUserId: string) {
    return this.claimsRepo.manager.transaction(async (manager) => {
      let claimQuery = manager
        .getRepository(UserPersonClaimEntity)
        .createQueryBuilder('claim')
        .where('claim.claimId = :claimId', { claimId });
      if (manager.connection.options.type !== 'sqlite') {
        claimQuery = claimQuery.setLock('pessimistic_write');
      }
      const claim = await claimQuery.getOne();
      if (!claim) throw new NotFoundException('Claim not found');

      const actorMembership = await manager.findOneBy(SpaceMemberEntity, {
        spaceId: claim.spaceId,
        userId: actorUserId,
      });
      if (
        !actorMembership ||
        (actorMembership.role !== 'OWNER' && actorMembership.role !== 'ADMIN')
      ) {
        throw new ForbiddenException('Only OWNER or ADMIN can confirm a claim');
      }
      if (claim.userId === actorUserId) {
        throw new ForbiddenException(
          'A claim owner cannot confirm their own claim',
        );
      }

      const existingConfirmation = await manager.findOneBy(
        ClaimConfirmationEntity,
        { claimId, confirmedBy: actorUserId },
      );
      if (existingConfirmation) {
        const confirmationCount = await manager.countBy(
          ClaimConfirmationEntity,
          { claimId },
        );
        return {
          ...claim,
          confirmationCount,
          required: REQUIRED_CLAIM_CONFIRMATIONS,
          confirmationRecorded: false,
        };
      }
      if (claim.status === 'VERIFIED') {
        throw new ConflictException('Claim is already verified');
      }
      if (claim.status === 'REJECTED') {
        throw new ConflictException('Rejected claim cannot be confirmed');
      }

      const beforeJson = JSON.stringify(claim);
      const confirmation = await manager.save(
        manager.create(ClaimConfirmationEntity, {
          claimId,
          confirmedBy: actorUserId,
        }),
      );
      const confirmationCount = await manager.countBy(ClaimConfirmationEntity, {
        claimId,
      });
      claim.status = collectiveClaimStatus(confirmationCount);
      const saved = await manager.save(claim);
      await manager.save(
        manager.create(ChangeLogEntity, {
          spaceId: saved.spaceId,
          actorUserId: actorUserId ?? 'SYSTEM',
          entityType: 'CLAIM',
          entityId: saved.claimId,
          operation: 'VERIFY',
          note: `Confirm claim (${confirmationCount}/${REQUIRED_CLAIM_CONFIRMATIONS})`,
          beforeJson,
          afterJson: JSON.stringify({
            claim: saved,
            confirmation: {
              confirmationId: confirmation.confirmationId,
              confirmedBy: confirmation.confirmedBy,
              confirmedAt: confirmation.confirmedAt,
            },
            confirmationCount,
            required: REQUIRED_CLAIM_CONFIRMATIONS,
          }),
        }),
      );
      return {
        ...saved,
        confirmationCount,
        required: REQUIRED_CLAIM_CONFIRMATIONS,
        confirmationRecorded: true,
      };
    });
  }
}
