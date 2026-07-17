import {
  BadRequestException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { ChangeLogEntity } from '../changes/change-log.entity';
import { PersonEntity } from '../persons/person.entity';
import { EditProposalEntity } from './edit-proposal.entity';
import { FactSourceEntity } from './fact-source.entity';
import { MediaItemEntity } from './media-item.entity';

@Injectable()
export class ArchiveService {
  constructor(
    @InjectRepository(FactSourceEntity)
    private readonly sourcesRepo: Repository<FactSourceEntity>,
    @InjectRepository(MediaItemEntity)
    private readonly mediaRepo: Repository<MediaItemEntity>,
    @InjectRepository(EditProposalEntity)
    private readonly proposalsRepo: Repository<EditProposalEntity>,
    @InjectRepository(PersonEntity)
    private readonly personsRepo: Repository<PersonEntity>,
    @InjectRepository(ChangeLogEntity)
    private readonly changeRepo: Repository<ChangeLogEntity>,
  ) {}

  private async assertPerson(spaceId: string, personId: string) {
    const person = await this.personsRepo.findOneBy({
      spaceId,
      personId,
      isDeleted: false,
    });
    if (!person) throw new NotFoundException('Person not found');
    return person;
  }

  listSources(spaceId: string, personId: string) {
    return this.sourcesRepo.find({
      where: { spaceId, personId },
      order: { createdAt: 'DESC' },
    });
  }

  async createSource(
    spaceId: string,
    personId: string,
    input: {
      title: string;
      type: 'DOCUMENT' | 'STORY' | 'PHOTO' | 'OTHER';
      url?: string | null;
      note?: string | null;
    },
    actorUserId: string,
  ) {
    await this.assertPerson(spaceId, personId);
    return this.sourcesRepo.manager.transaction(async (manager) => {
      const saved = await manager.save(
        manager.create(FactSourceEntity, {
          spaceId,
          personId,
          title: input.title.trim(),
          type: input.type,
          url: input.url?.trim() || null,
          note: input.note?.trim() || null,
        }),
      );
      await manager.save(
        manager.create(ChangeLogEntity, {
          spaceId,
          actorUserId,
          entityType: 'SOURCE',
          entityId: saved.sourceId,
          operation: 'CREATE',
          note: 'Add fact source',
          afterJson: JSON.stringify(saved),
        }),
      );
      return saved;
    });
  }

  listMedia(spaceId: string, personId: string) {
    return this.mediaRepo.find({
      where: { spaceId, personId },
      order: { createdAt: 'DESC' },
    });
  }

  async createMedia(
    spaceId: string,
    personId: string,
    input: {
      label: string;
      kind: 'PHOTO' | 'DOCUMENT' | 'AUDIO' | 'OTHER';
      uri: string;
      sourceId?: string | null;
    },
    actorUserId: string,
  ) {
    await this.assertPerson(spaceId, personId);
    if (input.sourceId) {
      const source = await this.sourcesRepo.findOneBy({
        spaceId,
        personId,
        sourceId: input.sourceId,
      });
      if (!source) throw new BadRequestException('Source not found for person');
    }

    return this.mediaRepo.manager.transaction(async (manager) => {
      const saved = await manager.save(
        manager.create(MediaItemEntity, {
          spaceId,
          personId,
          label: input.label.trim(),
          kind: input.kind,
          uri: input.uri.trim(),
          sourceId: input.sourceId ?? null,
        }),
      );
      await manager.save(
        manager.create(ChangeLogEntity, {
          spaceId,
          actorUserId,
          entityType: 'MEDIA',
          entityId: saved.mediaId,
          operation: 'CREATE',
          note: 'Add media metadata',
          afterJson: JSON.stringify(saved),
        }),
      );
      return saved;
    });
  }

  listProposals(spaceId: string) {
    return this.proposalsRepo.find({
      where: { spaceId },
      order: { createdAt: 'DESC' },
    });
  }

  async createProposal(
    input: {
      spaceId: string;
      personId: string;
      field: 'notes' | 'birthPlace' | 'deathPlace';
      proposedValue: string;
      reason?: string | null;
    },
    actorUserId: string,
  ) {
    await this.assertPerson(input.spaceId, input.personId);
    return this.proposalsRepo.manager.transaction(async (manager) => {
      const saved = await manager.save(
        manager.create(EditProposalEntity, {
          spaceId: input.spaceId,
          personId: input.personId,
          field: input.field,
          proposedValue: input.proposedValue.trim(),
          reason: input.reason?.trim() || null,
        }),
      );
      await manager.save(
        manager.create(ChangeLogEntity, {
          spaceId: input.spaceId,
          actorUserId,
          entityType: 'PROPOSAL',
          entityId: saved.proposalId,
          operation: 'CREATE',
          note: 'Create edit proposal',
          afterJson: JSON.stringify(saved),
        }),
      );
      return saved;
    });
  }

  async approveProposal(
    spaceId: string,
    proposalId: string,
    actorUserId: string,
  ) {
    const proposal = await this.proposalsRepo.findOneBy({
      spaceId,
      proposalId,
    });
    if (!proposal) throw new NotFoundException('Proposal not found');
    if (proposal.status !== 'PENDING') return proposal;

    const person = await this.assertPerson(spaceId, proposal.personId);
    return this.proposalsRepo.manager.transaction(async (manager) => {
      const beforePerson = JSON.stringify(person);
      person[proposal.field] = proposal.proposedValue;
      const savedPerson = await manager.save(person);

      const beforeProposal = JSON.stringify(proposal);
      proposal.status = 'APPROVED';
      proposal.reviewedByUserId = actorUserId;
      proposal.reviewedAt = new Date();
      const savedProposal = await manager.save(proposal);

      await manager.save(
        manager.create(ChangeLogEntity, {
          spaceId,
          actorUserId,
          entityType: 'PERSON',
          entityId: savedPerson.personId,
          operation: 'UPDATE',
          note: `Approve proposal for ${proposal.field}`,
          beforeJson: beforePerson,
          afterJson: JSON.stringify(savedPerson),
        }),
      );
      await manager.save(
        manager.create(ChangeLogEntity, {
          spaceId,
          actorUserId,
          entityType: 'PROPOSAL',
          entityId: savedProposal.proposalId,
          operation: 'VERIFY',
          note: 'Approve edit proposal',
          beforeJson: beforeProposal,
          afterJson: JSON.stringify(savedProposal),
        }),
      );
      return savedProposal;
    });
  }

  async rejectProposal(
    spaceId: string,
    proposalId: string,
    actorUserId: string,
  ) {
    const proposal = await this.proposalsRepo.findOneBy({
      spaceId,
      proposalId,
    });
    if (!proposal) throw new NotFoundException('Proposal not found');
    if (proposal.status !== 'PENDING') return proposal;

    const beforeProposal = JSON.stringify(proposal);
    proposal.status = 'REJECTED';
    proposal.reviewedByUserId = actorUserId;
    proposal.reviewedAt = new Date();
    const saved = await this.proposalsRepo.save(proposal);
    await this.changeRepo.save({
      spaceId,
      actorUserId,
      entityType: 'PROPOSAL',
      entityId: saved.proposalId,
      operation: 'VERIFY',
      note: 'Reject edit proposal',
      beforeJson: beforeProposal,
      afterJson: JSON.stringify(saved),
    });
    return saved;
  }
}
