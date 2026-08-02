import {
  BadRequestException,
  ConflictException,
  ForbiddenException,
  Inject,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { In, Repository } from 'typeorm';
import { ChangeLogEntity } from '../changes/change-log.entity';
import { PersonEntity } from '../persons/person.entity';
import { EditProposalEntity } from './edit-proposal.entity';
import { FactSourceEntity } from './fact-source.entity';
import { MediaItemEntity } from './media-item.entity';
import { processUploadedImage } from './image-processor';
import { OBJECT_STORAGE } from './storage/object-storage';
import type { ObjectStorage } from './storage/object-storage';
import { randomUUID } from 'node:crypto';
import { PersonDeletionService } from '../persons/person-deletion.service';
import { PersonPrivacyService } from '../persons/person-privacy.service';
import { ProposalCommentEntity } from './proposal-comment.entity';
import { UserEntity } from '../users/user.entity';
import { ClientMutationEntity } from '../persons/client-mutation.entity';
import { UserPersonClaimEntity } from '../claims/user-person-claim.entity';
import { SpaceMemberEntity } from '../spaces/space-member.entity';

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
    @InjectRepository(ProposalCommentEntity)
    private readonly proposalCommentsRepo: Repository<ProposalCommentEntity>,
    @InjectRepository(UserEntity)
    private readonly usersRepo: Repository<UserEntity>,
    @InjectRepository(UserPersonClaimEntity)
    private readonly claimsRepo: Repository<UserPersonClaimEntity>,
    @InjectRepository(SpaceMemberEntity)
    private readonly membersRepo: Repository<SpaceMemberEntity>,
    @Inject(OBJECT_STORAGE)
    private readonly objectStorage: ObjectStorage,
    private readonly personDeletionService: PersonDeletionService,
    private readonly personPrivacyService: PersonPrivacyService,
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

  async listSources(spaceId: string, personId: string, actorUserId: string) {
    const { decision } = await this.personPrivacyService.findPersonWithDecision(
      spaceId,
      personId,
      actorUserId,
    );
    if (decision.access !== 'FULL') return [];
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
      clientMutationId?: string;
    },
    actorUserId: string,
  ) {
    await this.assertFullPrivacyAccess(spaceId, personId, actorUserId);
    const clientMutationId = input.clientMutationId ?? randomUUID();
    const normalized = {
      spaceId,
      personId,
      title: input.title.trim(),
      type: input.type,
      url: input.url?.trim() || null,
      note: input.note?.trim() || null,
    };
    const requestFingerprint = JSON.stringify(normalized);
    return this.sourcesRepo.manager.transaction(async (manager) => {
      const priorMutation = await manager.findOne(ClientMutationEntity, {
        where: { clientMutationId },
      });
      if (priorMutation) {
        if (
          priorMutation.actorUserId !== actorUserId ||
          priorMutation.operation !== 'CREATE_SOURCE' ||
          priorMutation.requestFingerprint !== requestFingerprint
        ) {
          throw new ConflictException(
            'clientMutationId was already used for another mutation',
          );
        }
        const priorSource = JSON.parse(
          priorMutation.responseJson,
        ) as FactSourceEntity;
        const current = await manager.findOneBy(FactSourceEntity, {
          sourceId: priorSource.sourceId,
          spaceId,
          personId,
        });
        if (!current) throw new NotFoundException('Source not found');
        return current;
      }
      const saved = await manager.save(
        manager.create(FactSourceEntity, {
          ...normalized,
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
      await manager.save(
        manager.create(ClientMutationEntity, {
          clientMutationId,
          actorUserId,
          spaceId,
          operation: 'CREATE_SOURCE',
          requestFingerprint,
          responseJson: JSON.stringify(saved),
        }),
      );
      return saved;
    });
  }

  async listMedia(spaceId: string, personId: string, actorUserId: string) {
    const { decision } = await this.personPrivacyService.findPersonWithDecision(
      spaceId,
      personId,
      actorUserId,
    );
    if (decision.access !== 'FULL') return [];
    return this.mediaRepo.find({
      where: { spaceId, personId },
      order: { createdAt: 'DESC' },
    });
  }

  async listProfilePhotos(spaceId: string, actorUserId: string) {
    const media = await this.mediaRepo.find({
      where: { spaceId, kind: 'PHOTO' },
      order: { createdAt: 'DESC' },
    });
    const latestManagedPhotoByPerson = new Map<string, MediaItemEntity>();
    for (const item of media) {
      if (
        item.uri.startsWith('object://') &&
        !latestManagedPhotoByPerson.has(item.personId)
      ) {
        latestManagedPhotoByPerson.set(item.personId, item);
      }
    }

    const people = latestManagedPhotoByPerson.size
      ? await this.personsRepo.findBy({
          spaceId,
          personId: In([...latestManagedPhotoByPerson.keys()]),
          isDeleted: false,
        })
      : [];
    const decisions = await this.personPrivacyService.decisionsForPeople(
      spaceId,
      people,
      actorUserId,
    );
    const visiblePersonIds = new Set(
      people
        .filter((person) => decisions.get(person.personId)?.access === 'FULL')
        .map((person) => person.personId),
    );

    const expiresIn = 15 * 60;
    return Promise.all(
      [...latestManagedPhotoByPerson.values()]
        .filter((item) => visiblePersonIds.has(item.personId))
        .map(async (item) => ({
          personId: item.personId,
          mediaId: item.mediaId,
          url: await this.objectStorage.createSignedReadUrl(
            item.uri.slice('object://'.length),
            expiresIn,
          ),
          expiresIn,
        })),
    );
  }

  async getMyProfilePhoto(spaceId: string, actorUserId: string) {
    const claim = await this.claimsRepo.findOne({
      where: { spaceId, userId: actorUserId, status: 'VERIFIED' },
      order: { requestedAt: 'DESC' },
    });
    if (!claim) return { photo: null };
    const media = await this.mediaRepo.find({
      where: { spaceId, personId: claim.personId, kind: 'PHOTO' },
      order: { createdAt: 'DESC' },
    });
    const photo = media.find((item) => item.uri.startsWith('object://'));
    if (!photo) return { photo: null };
    await this.assertFullPrivacyAccess(spaceId, claim.personId, actorUserId);
    const expiresIn = 15 * 60;
    return {
      photo: {
        personId: claim.personId,
        mediaId: photo.mediaId,
        url: await this.objectStorage.createSignedReadUrl(
          photo.uri.slice('object://'.length),
          expiresIn,
        ),
        expiresIn,
      },
    };
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
    await this.assertFullPrivacyAccess(spaceId, personId, actorUserId);
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

  async uploadImage(
    spaceId: string,
    personId: string,
    input: { label: string; sourceId?: string | null },
    file: Buffer,
    actorUserId: string,
  ) {
    await this.assertCanManageProfilePhoto(spaceId, personId, actorUserId);
    const previousManagedPhotos = (
      await this.mediaRepo.find({
        where: { spaceId, personId, kind: 'PHOTO' },
        order: { createdAt: 'DESC' },
      })
    ).filter((item) => item.uri.startsWith('object://'));
    const image = await processUploadedImage(file);
    const objectPath = `${spaceId}/${personId}/${randomUUID()}.${image.extension}`;

    await this.objectStorage.putObject({
      path: objectPath,
      contentType: image.contentType,
      body: image.body,
    });

    let saved: MediaItemEntity | null = null;
    try {
      saved = await this.createMedia(
        spaceId,
        personId,
        {
          label: input.label,
          kind: 'PHOTO',
          uri: `object://${objectPath}`,
          sourceId: input.sourceId,
        },
        actorUserId,
      );
      const expiresIn = 15 * 60;
      const url = await this.objectStorage.createSignedReadUrl(
        objectPath,
        expiresIn,
      );
      if (previousManagedPhotos.length > 0) {
        const oldMetadataDeleted = await this.mediaRepo
          .delete({
            mediaId: In(previousManagedPhotos.map((item) => item.mediaId)),
          })
          .then(() => true)
          .catch(() => false);
        if (oldMetadataDeleted) {
          await Promise.allSettled(
            previousManagedPhotos.map((item) =>
              this.objectStorage.deleteObject(
                item.uri.slice('object://'.length),
              ),
            ),
          );
        }
      }
      return {
        ...saved,
        url,
        expiresIn,
      };
    } catch (error) {
      if (saved) {
        await this.mediaRepo
          .delete({ mediaId: saved.mediaId })
          .catch(() => undefined);
      }
      await this.objectStorage.deleteObject(objectPath).catch(() => undefined);
      throw error;
    }
  }

  async getMediaAccess(
    spaceId: string,
    personId: string,
    mediaId: string,
    actorUserId: string,
  ) {
    const { decision } = await this.personPrivacyService.findPersonWithDecision(
      spaceId,
      personId,
      actorUserId,
    );
    if (decision.access !== 'FULL') {
      throw new NotFoundException('Media not found');
    }
    const media = await this.mediaRepo.findOneBy({
      spaceId,
      personId,
      mediaId,
    });
    if (!media) throw new NotFoundException('Media not found');
    if (!media.uri.startsWith('object://')) {
      throw new BadRequestException('Media is not managed by private storage');
    }

    const expiresIn = 60;
    return {
      url: await this.objectStorage.createSignedReadUrl(
        media.uri.slice('object://'.length),
        expiresIn,
      ),
      expiresIn,
    };
  }

  private async assertFullPrivacyAccess(
    spaceId: string,
    personId: string,
    actorUserId: string,
  ) {
    const { person, decision } =
      await this.personPrivacyService.findPersonWithDecision(
        spaceId,
        personId,
        actorUserId,
      );
    if (decision.access !== 'FULL') {
      throw new ForbiddenException(
        'Full person access is required for this action',
      );
    }
    return person;
  }

  private async assertCanManageProfilePhoto(
    spaceId: string,
    personId: string,
    actorUserId: string,
  ) {
    await this.assertFullPrivacyAccess(spaceId, personId, actorUserId);
    const member = await this.membersRepo.findOneBy({
      spaceId,
      userId: actorUserId,
    });
    if (!member) {
      throw new ForbiddenException('User is not a member of this space');
    }
    if (member.role !== 'VIEWER') return;

    const verifiedSelfClaim = await this.claimsRepo.findOneBy({
      spaceId,
      userId: actorUserId,
      personId,
      status: 'VERIFIED',
    });
    if (!verifiedSelfClaim) {
      throw new ForbiddenException(
        'Viewers can only manage their verified profile photo',
      );
    }
  }

  async listProposals(spaceId: string, actorUserId: string) {
    const proposals = await this.proposalsRepo.find({
      where: { spaceId },
      order: { createdAt: 'DESC' },
    });
    const personIds = [...new Set(proposals.map((item) => item.personId))];
    const people = personIds.length
      ? await this.personsRepo.find({
          where: { spaceId, personId: In(personIds) },
          select: [
            'personId',
            'fullName',
            'notes',
            'birthPlace',
            'deathPlace',
            'visibility',
          ],
        })
      : [];
    const decisions = await this.personPrivacyService.decisionsForPeople(
      spaceId,
      people,
      actorUserId,
    );
    const peopleById = new Map(
      people
        .filter((person) => decisions.get(person.personId)?.access === 'FULL')
        .map((person) => [person.personId, person]),
    );
    return proposals
      .filter((proposal) => peopleById.has(proposal.personId))
      .map((proposal) => ({
        ...proposal,
        personName: peopleById.get(proposal.personId)?.fullName,
        currentValue: this.proposalFieldValue(
          peopleById.get(proposal.personId),
          proposal.field,
        ),
      }));
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
    const person = await this.assertFullPrivacyAccess(
      input.spaceId,
      input.personId,
      actorUserId,
    );
    return this.proposalsRepo.manager.transaction(async (manager) => {
      const saved = await manager.save(
        manager.create(EditProposalEntity, {
          spaceId: input.spaceId,
          personId: input.personId,
          field: input.field,
          proposedValue: input.proposedValue.trim(),
          beforeValue: this.proposalFieldValue(person, input.field),
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

  async listProposalComments(
    spaceId: string,
    proposalId: string,
    actorUserId: string,
  ) {
    await this.assertProposalPrivacyAccess(spaceId, proposalId, actorUserId);
    const comments = await this.proposalCommentsRepo.find({
      where: { spaceId, proposalId },
      order: { createdAt: 'ASC' },
    });
    const authorIds = [...new Set(comments.map((item) => item.authorUserId))];
    const authors = authorIds.length
      ? await this.usersRepo.find({
          where: { userId: In(authorIds) },
          select: ['userId', 'displayName'],
        })
      : [];
    const authorNames = new Map(
      authors.map((author) => [author.userId, author.displayName]),
    );
    return comments.map((comment) =>
      this.proposalCommentResult(
        comment,
        authorNames.get(comment.authorUserId) ?? 'Anggota keluarga',
        actorUserId,
      ),
    );
  }

  async createProposalComment(
    spaceId: string,
    proposalId: string,
    body: string,
    actorUserId: string,
  ) {
    await this.assertProposalPrivacyAccess(spaceId, proposalId, actorUserId);
    const normalizedBody = body.trim();
    if (!normalizedBody) {
      throw new BadRequestException('Comment body is required');
    }
    const author = await this.usersRepo.findOne({
      where: { userId: actorUserId },
      select: ['userId', 'displayName'],
    });
    if (!author) throw new NotFoundException('Comment author not found');

    return this.proposalCommentsRepo.manager.transaction(async (manager) => {
      const saved = await manager.save(
        manager.create(ProposalCommentEntity, {
          spaceId,
          proposalId,
          authorUserId: actorUserId,
          body: normalizedBody,
        }),
      );
      await manager.save(
        manager.create(ChangeLogEntity, {
          spaceId,
          actorUserId,
          entityType: 'PROPOSAL',
          entityId: saved.commentId,
          operation: 'CREATE',
          note: `Add comment to proposal ${proposalId}`,
          afterJson: JSON.stringify({
            commentId: saved.commentId,
            proposalId,
            authorUserId: actorUserId,
            createdAt: saved.createdAt,
          }),
        }),
      );
      return this.proposalCommentResult(saved, author.displayName, actorUserId);
    });
  }

  private async assertProposalPrivacyAccess(
    spaceId: string,
    proposalId: string,
    actorUserId: string,
  ) {
    const proposal = await this.proposalsRepo.findOneBy({
      spaceId,
      proposalId,
    });
    if (!proposal) throw new NotFoundException('Proposal not found');
    await this.assertFullPrivacyAccess(spaceId, proposal.personId, actorUserId);
    return proposal;
  }

  private proposalCommentResult(
    comment: ProposalCommentEntity,
    authorDisplayName: string,
    actorUserId: string,
  ) {
    return {
      commentId: comment.commentId,
      proposalId: comment.proposalId,
      body: comment.body,
      authorDisplayName,
      isMine: comment.authorUserId === actorUserId,
      createdAt: comment.createdAt,
    };
  }

  async approveProposal(
    spaceId: string,
    proposalId: string,
    actorUserId: string,
    reviewReason?: string,
  ) {
    const proposal = await this.proposalsRepo.findOneBy({
      spaceId,
      proposalId,
    });
    if (!proposal) throw new NotFoundException('Proposal not found');
    await this.assertFullPrivacyAccess(spaceId, proposal.personId, actorUserId);
    if (proposal.status !== 'PENDING') return proposal;

    return this.proposalsRepo.manager.transaction(async (manager) => {
      if (proposal.field === 'DELETE_PERSON') {
        await this.personDeletionService.softDeleteWithManager(
          manager,
          spaceId,
          proposal.personId,
          actorUserId,
          proposal.proposalId,
        );
      } else {
        const person = await this.assertPerson(spaceId, proposal.personId);
        const beforePerson = JSON.stringify(person);
        person[proposal.field] = proposal.proposedValue;
        const savedPerson = await manager.save(person);
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
      }

      const beforeProposal = JSON.stringify(proposal);
      proposal.status = 'APPROVED';
      proposal.reviewedByUserId = actorUserId;
      proposal.reviewedAt = new Date();
      proposal.reviewReason = this.normalizedReviewReason(reviewReason);
      const savedProposal = await manager.save(proposal);

      await manager.save(
        manager.create(ChangeLogEntity, {
          spaceId,
          actorUserId,
          entityType: 'PROPOSAL',
          entityId: savedProposal.proposalId,
          operation: 'VERIFY',
          note:
            proposal.field === 'DELETE_PERSON'
              ? 'Approve person deletion request'
              : 'Approve edit proposal',
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
    reviewReason?: string,
    requireReviewReason = false,
  ) {
    const normalizedReason = this.normalizedReviewReason(reviewReason);
    if (requireReviewReason && !normalizedReason) {
      throw new BadRequestException(
        'reviewReason is required when rejecting a proposal',
      );
    }
    const proposalForAccess = await this.proposalsRepo.findOneBy({
      spaceId,
      proposalId,
    });
    if (!proposalForAccess) throw new NotFoundException('Proposal not found');
    await this.assertFullPrivacyAccess(
      spaceId,
      proposalForAccess.personId,
      actorUserId,
    );
    return this.proposalsRepo.manager.transaction(async (manager) => {
      const proposal = await manager.findOneBy(EditProposalEntity, {
        spaceId,
        proposalId,
      });
      if (!proposal) throw new NotFoundException('Proposal not found');
      if (proposal.status !== 'PENDING') return proposal;

      const beforeProposal = JSON.stringify(proposal);
      proposal.status = 'REJECTED';
      proposal.reviewedByUserId = actorUserId;
      proposal.reviewedAt = new Date();
      proposal.reviewReason = normalizedReason;
      const saved = await manager.save(proposal);
      await manager.save(
        manager.create(ChangeLogEntity, {
          spaceId,
          actorUserId,
          entityType: 'PROPOSAL',
          entityId: saved.proposalId,
          operation: 'VERIFY',
          note: 'Reject edit proposal',
          beforeJson: beforeProposal,
          afterJson: JSON.stringify(saved),
        }),
      );
      return saved;
    });
  }

  private normalizedReviewReason(reviewReason?: string) {
    return reviewReason?.trim() || null;
  }

  private proposalFieldValue(
    person: PersonEntity | undefined,
    field: EditProposalEntity['field'],
  ) {
    if (!person || field === 'DELETE_PERSON') return null;
    return person[field] ?? null;
  }
}
