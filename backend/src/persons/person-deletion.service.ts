import {
  ConflictException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectDataSource } from '@nestjs/typeorm';
import { DataSource, EntityManager, Not } from 'typeorm';
import { EditProposalEntity } from '../archive/edit-proposal.entity';
import { FactSourceEntity } from '../archive/fact-source.entity';
import { MediaItemEntity } from '../archive/media-item.entity';
import { ChangeLogEntity } from '../changes/change-log.entity';
import { UserPersonClaimEntity } from '../claims/user-person-claim.entity';
import { PersonEntity } from './person.entity';
import { RelationshipEntity } from './relationship.entity';

export interface PersonDeletionBlocker {
  code: 'RELATIONSHIPS' | 'CLAIMS' | 'MEDIA' | 'SOURCES' | 'PENDING_PROPOSALS';
  message: string;
  count: number;
}

export interface PersonDeletionImpact {
  personId: string;
  fullName: string;
  relationshipCount: number;
  claimCount: number;
  mediaCount: number;
  sourceCount: number;
  pendingProposalCount: number;
  canDelete: boolean;
  blockers: PersonDeletionBlocker[];
}

@Injectable()
export class PersonDeletionService {
  constructor(@InjectDataSource() private readonly dataSource: DataSource) {}

  getImpact(spaceId: string, personId: string) {
    return this.getImpactWithManager(
      this.dataSource.manager,
      spaceId,
      personId,
    );
  }

  async requestDeletion(
    spaceId: string,
    personId: string,
    reason: string,
    actorUserId: string,
  ) {
    return this.dataSource.transaction(async (manager) => {
      await this.requireActivePerson(manager, spaceId, personId);
      const existing = await manager.findOne(EditProposalEntity, {
        where: {
          spaceId,
          personId,
          field: 'DELETE_PERSON',
          status: 'PENDING',
        },
      });
      if (existing) {
        throw new ConflictException(
          'A deletion request for this person is already pending',
        );
      }

      const proposal = await manager.save(
        manager.create(EditProposalEntity, {
          spaceId,
          personId,
          field: 'DELETE_PERSON',
          proposedValue: 'REQUEST_DELETE',
          reason: reason.trim(),
        }),
      );
      await manager.save(
        manager.create(ChangeLogEntity, {
          spaceId,
          actorUserId,
          entityType: 'PROPOSAL',
          entityId: proposal.proposalId,
          operation: 'CREATE',
          note: 'Request person deletion',
          beforeJson: null,
          afterJson: JSON.stringify(proposal),
        }),
      );
      return proposal;
    });
  }

  softDelete(spaceId: string, personId: string, actorUserId: string) {
    return this.dataSource.transaction((manager) =>
      this.softDeleteWithManager(manager, spaceId, personId, actorUserId),
    );
  }

  async softDeleteWithManager(
    manager: EntityManager,
    spaceId: string,
    personId: string,
    actorUserId: string,
    excludedProposalId?: string,
  ) {
    const person = await this.requireActivePerson(manager, spaceId, personId);
    const impact = await this.getImpactWithManager(
      manager,
      spaceId,
      personId,
      excludedProposalId,
    );
    if (!impact.canDelete) {
      throw new ConflictException({
        message: 'Person cannot be deleted until linked data is resolved',
        details: { impact },
      });
    }

    const beforeJson = JSON.stringify(person);
    person.isDeleted = true;
    person.deletedAt = new Date();
    const saved = await manager.save(person);
    await manager.save(
      manager.create(ChangeLogEntity, {
        spaceId,
        actorUserId,
        entityType: 'PERSON',
        entityId: personId,
        operation: 'DELETE',
        note: 'Soft delete person',
        beforeJson,
        afterJson: JSON.stringify(saved),
      }),
    );
    return { personId, deleted: true };
  }

  async getImpactWithManager(
    manager: EntityManager,
    spaceId: string,
    personId: string,
    excludedProposalId?: string,
  ): Promise<PersonDeletionImpact> {
    const person = await this.requireActivePerson(manager, spaceId, personId);
    const [
      relationshipCount,
      claimCount,
      mediaCount,
      sourceCount,
      pendingProposalCount,
    ] = await Promise.all([
      manager.count(RelationshipEntity, {
        where: [
          { spaceId, fromPersonId: personId },
          { spaceId, toPersonId: personId },
        ],
      }),
      manager.count(UserPersonClaimEntity, {
        where: { spaceId, personId },
      }),
      manager.count(MediaItemEntity, { where: { spaceId, personId } }),
      manager.count(FactSourceEntity, { where: { spaceId, personId } }),
      manager.count(EditProposalEntity, {
        where: {
          spaceId,
          personId,
          status: 'PENDING',
          ...(excludedProposalId
            ? { proposalId: Not(excludedProposalId) }
            : {}),
        },
      }),
    ]);

    const blockers: PersonDeletionBlocker[] = [];
    this.addBlocker(
      blockers,
      'RELATIONSHIPS',
      relationshipCount,
      'Selesaikan hubungan keluarga terlebih dahulu',
    );
    this.addBlocker(
      blockers,
      'CLAIMS',
      claimCount,
      'Selesaikan klaim identitas terlebih dahulu',
    );
    this.addBlocker(
      blockers,
      'MEDIA',
      mediaCount,
      'Selesaikan media yang terhubung terlebih dahulu',
    );
    this.addBlocker(
      blockers,
      'SOURCES',
      sourceCount,
      'Selesaikan sumber data yang terhubung terlebih dahulu',
    );
    this.addBlocker(
      blockers,
      'PENDING_PROPOSALS',
      pendingProposalCount,
      'Selesaikan usulan yang masih menunggu terlebih dahulu',
    );

    return {
      personId,
      fullName: person.fullName,
      relationshipCount,
      claimCount,
      mediaCount,
      sourceCount,
      pendingProposalCount,
      canDelete: blockers.length === 0,
      blockers,
    };
  }

  private async requireActivePerson(
    manager: EntityManager,
    spaceId: string,
    personId: string,
  ) {
    const person = await manager.findOne(PersonEntity, {
      where: { spaceId, personId, isDeleted: false },
    });
    if (!person) throw new NotFoundException('Person not found');
    return person;
  }

  private addBlocker(
    blockers: PersonDeletionBlocker[],
    code: PersonDeletionBlocker['code'],
    count: number,
    message: string,
  ) {
    if (count > 0) blockers.push({ code, count, message });
  }
}
