import {
  ConflictException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { UserPersonClaimEntity } from './user-person-claim.entity';
import { ChangeLogEntity } from '../changes/change-log.entity';
import { PersonEntity } from '../persons/person.entity';
import { SpaceMemberEntity } from '../spaces/space-member.entity';

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

  async verify(claimId: string, actorUserId?: string) {
    const claim = await this.claimsRepo.findOneBy({ claimId });
    if (!claim) {
      throw new NotFoundException('Claim not found');
    }

    return this.claimsRepo.manager.transaction(async (manager) => {
      const beforeJson = JSON.stringify(claim);
      claim.status = 'VERIFIED';
      const saved = await manager.save(claim);
      await manager.save(
        manager.create(ChangeLogEntity, {
          spaceId: saved.spaceId,
          actorUserId: actorUserId ?? 'SYSTEM',
          entityType: 'CLAIM',
          entityId: saved.claimId,
          operation: 'VERIFY',
          note: 'Verify claim',
          beforeJson,
          afterJson: JSON.stringify(saved),
        }),
      );
      return saved;
    });
  }
}
