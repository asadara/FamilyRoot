/* eslint-disable @typescript-eslint/no-unsafe-assignment, @typescript-eslint/no-unsafe-member-access */
import { INestApplication, ValidationPipe } from '@nestjs/common';
import { Test, TestingModule } from '@nestjs/testing';
import request from 'supertest';
import { App } from 'supertest/types';
import { DataSource } from 'typeorm';
import { AppModule } from '../src/app.module';
import { OBJECT_STORAGE } from '../src/archive/storage/object-storage';
import { ChangeLogEntity } from '../src/changes/change-log.entity';

type Role = 'VIEWER' | 'EDITOR' | 'ADMIN';

describe('Invitation and collaboration roles smoke (e2e)', () => {
  let app: INestApplication<App>;

  beforeAll(async () => {
    const moduleFixture: TestingModule = await Test.createTestingModule({
      imports: [AppModule],
    })
      .overrideProvider(OBJECT_STORAGE)
      .useValue({
        putObject: () => Promise.resolve(),
        deleteObject: () => Promise.resolve(),
        createSignedReadUrl: () =>
          Promise.resolve('https://storage.example.test/smoke'),
      })
      .compile();
    app = moduleFixture.createNestApplication();
    app.useGlobalPipes(
      new ValidationPipe({
        whitelist: true,
        forbidNonWhitelisted: true,
        transform: true,
      }),
    );
    await app.init();
  });

  afterAll(async () => app.close());

  async function register(label: string) {
    const response = await request(app.getHttpServer())
      .post('/auth/register')
      .send({
        email: `smoke-${label}@example.test`,
        displayName: `Smoke ${label}`,
        password: `smoke-${label}-password`,
      })
      .expect(201);
    return response.body.accessToken as string;
  }

  async function inviteAndJoin(
    ownerToken: string,
    inviteeToken: string,
    spaceId: string,
    role: Role,
  ) {
    const invitation = await request(app.getHttpServer())
      .post('/spaces/invitations')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({ spaceId, role, expiresInDays: 1 })
      .expect(201);
    const token = invitation.body.token as string;
    expect(token).toEqual(expect.any(String));
    expect(token).toMatch(
      new RegExp(`^FR-${{ VIEWER: 'V', EDITOR: 'K', ADMIN: 'P' }[role]}-`),
    );

    await request(app.getHttpServer())
      .get(`/spaces/invitations/${token}`)
      .set('Authorization', `Bearer ${inviteeToken}`)
      .expect(200)
      .expect(({ body }) =>
        expect(body).toEqual(expect.objectContaining({ spaceId, role })),
      );

    await request(app.getHttpServer())
      .post('/spaces/invitations/accept')
      .set('Authorization', `Bearer ${inviteeToken}`)
      .send({ token })
      .expect(201)
      .expect(({ body }) =>
        expect(body).toEqual(expect.objectContaining({ spaceId, role })),
      );

    await request(app.getHttpServer())
      .get('/spaces')
      .set('Authorization', `Bearer ${inviteeToken}`)
      .expect(200)
      .expect(({ body }) =>
        expect(body).toEqual(
          expect.arrayContaining([expect.objectContaining({ spaceId, role })]),
        ),
      );

    return token;
  }

  it('joins VIEWER, EDITOR, and ADMIN by invitation and enforces collaboration permissions', async () => {
    const ownerToken = await register('owner');
    const viewerToken = await register('viewer');
    const editorToken = await register('editor');
    const adminToken = await register('admin');

    const space = await request(app.getHttpServer())
      .post('/spaces')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({ name: 'Invitation Collaboration Smoke' })
      .expect(201);
    const spaceId = space.body.spaceId as string;

    const person = await request(app.getHttpServer())
      .post('/persons')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        spaceId,
        firstName: 'Budi',
        nickName: 'Budi',
        gender: 'MALE',
      })
      .expect(201);
    const personId = person.body.personId as string;
    await request(app.getHttpServer())
      .patch(`/persons/${personId}/visibility`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        spaceId,
        visibility: 'FAMILY',
        expectedVersion: person.body.version,
      })
      .expect(200);

    const viewerInvitation = await inviteAndJoin(
      ownerToken,
      viewerToken,
      spaceId,
      'VIEWER',
    );
    await inviteAndJoin(ownerToken, editorToken, spaceId, 'EDITOR');
    await inviteAndJoin(ownerToken, adminToken, spaceId, 'ADMIN');

    await request(app.getHttpServer())
      .get('/persons')
      .set('Authorization', `Bearer ${viewerToken}`)
      .query({ spaceId })
      .expect(200)
      .expect(({ body }) =>
        expect(body).toEqual(
          expect.arrayContaining([expect.objectContaining({ personId })]),
        ),
      );
    await request(app.getHttpServer())
      .post('/persons')
      .set('Authorization', `Bearer ${viewerToken}`)
      .send({
        spaceId,
        firstName: 'Blocked',
        nickName: 'Blocked',
        gender: 'UNKNOWN',
      })
      .expect(403);
    const proposal = await request(app.getHttpServer())
      .post('/proposals')
      .set('Authorization', `Bearer ${viewerToken}`)
      .send({
        spaceId,
        personId,
        field: 'notes',
        proposedValue: 'Usulan dari pembaca',
        reason: 'Smoke kolaborasi pembaca',
      })
      .expect(201);
    const comment = await request(app.getHttpServer())
      .post(`/proposals/${proposal.body.proposalId as string}/comments`)
      .set('Authorization', `Bearer ${viewerToken}`)
      .send({
        spaceId,
        body: '  Mohon periksa catatan sumber keluarga.  ',
      })
      .expect(201);
    expect(comment.body).toEqual(
      expect.objectContaining({
        proposalId: proposal.body.proposalId,
        body: 'Mohon periksa catatan sumber keluarga.',
        authorDisplayName: expect.any(String),
        isMine: true,
      }),
    );
    expect(comment.body).not.toHaveProperty('authorUserId');
    await request(app.getHttpServer())
      .get(`/proposals/${proposal.body.proposalId as string}/comments`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .query({ spaceId })
      .expect(200)
      .expect(({ body }) => {
        expect(body).toEqual(
          expect.arrayContaining([
            expect.objectContaining({
              commentId: comment.body.commentId,
              isMine: false,
            }),
          ]),
        );
      });
    await request(app.getHttpServer())
      .post(`/proposals/${proposal.body.proposalId as string}/comments`)
      .set('Authorization', `Bearer ${viewerToken}`)
      .send({ spaceId, body: '   ' })
      .expect(400);
    const notificationHistory = await request(app.getHttpServer())
      .get('/notifications')
      .set('Authorization', `Bearer ${viewerToken}`)
      .query({ limit: 20 })
      .expect(200);
    expect(notificationHistory.body.unreadCount).toBeGreaterThanOrEqual(2);
    expect(notificationHistory.body.items).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ code: 'COMMENT_SAVED', readAt: null }),
        expect.objectContaining({ code: 'ACTION_INVALID', readAt: null }),
      ]),
    );
    const commentReceipt = (
      notificationHistory.body.items as Array<{
        notificationId: string;
        code: string;
      }>
    ).find((item) => item.code === 'COMMENT_SAVED');
    await request(app.getHttpServer())
      .patch(`/notifications/${commentReceipt?.notificationId ?? ''}/read`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .expect(404);
    await request(app.getHttpServer())
      .patch(`/notifications/${commentReceipt?.notificationId ?? ''}/read`)
      .set('Authorization', `Bearer ${viewerToken}`)
      .expect(200)
      .expect(({ body }) => expect(body.readAt).not.toBeNull());
    await request(app.getHttpServer())
      .post('/notifications/read-all')
      .set('Authorization', `Bearer ${viewerToken}`)
      .expect(201);
    process.stdout.write(
      'VIEWER_INVITE_JOIN_READ_PROPOSE_AND_COMMENT_WITH_DIRECT_EDIT_BLOCKED: PASS\n',
    );

    await request(app.getHttpServer())
      .post('/persons')
      .set('Authorization', `Bearer ${editorToken}`)
      .send({
        spaceId,
        firstName: 'Kontributor',
        nickName: 'Editor',
        gender: 'UNKNOWN',
      })
      .expect(201);
    await request(app.getHttpServer())
      .get('/export/space')
      .set('Authorization', `Bearer ${editorToken}`)
      .query({ spaceId })
      .expect(403);
    await request(app.getHttpServer())
      .post('/spaces/invitations')
      .set('Authorization', `Bearer ${editorToken}`)
      .send({ spaceId, role: 'VIEWER', expiresInDays: 1 })
      .expect(403);
    process.stdout.write(
      'EDITOR_INVITE_JOIN_AND_DIRECT_CONTRIBUTION_WITH_ADMIN_ACTIONS_BLOCKED: PASS\n',
    );

    await request(app.getHttpServer())
      .post('/proposals/approve')
      .set('Authorization', `Bearer ${adminToken}`)
      .send({ spaceId, proposalId: proposal.body.proposalId })
      .expect(201)
      .expect(({ body }) => expect(body.status).toBe('APPROVED'));
    await request(app.getHttpServer())
      .get('/export/space')
      .set('Authorization', `Bearer ${adminToken}`)
      .query({ spaceId })
      .expect(200);
    await request(app.getHttpServer())
      .post('/spaces/invitations')
      .set('Authorization', `Bearer ${adminToken}`)
      .send({ spaceId, role: 'VIEWER', expiresInDays: 1 })
      .expect(201);
    await request(app.getHttpServer())
      .post('/spaces/invitations')
      .set('Authorization', `Bearer ${adminToken}`)
      .send({ spaceId, role: 'ADMIN', expiresInDays: 1 })
      .expect(403);
    process.stdout.write(
      'ADMIN_INVITE_JOIN_REVIEW_EXPORT_AND_LOWER_ROLE_INVITE: PASS\n',
    );

    await request(app.getHttpServer())
      .post('/spaces/invitations/accept')
      .set('Authorization', `Bearer ${viewerToken}`)
      .send({ token: viewerInvitation })
      .expect(409);
    process.stdout.write('ONE_TIME_INVITATION_REPLAY_BLOCKED: PASS\n');
  });

  it('restricts a targeted invitation to the matching normalized account email', async () => {
    const ownerToken = await register('target-owner');
    const targetToken = await register('target-match');
    const mismatchToken = await register('target-mismatch');

    const space = await request(app.getHttpServer())
      .post('/spaces')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({ name: 'Targeted Invitation Smoke' })
      .expect(201);
    const spaceId = space.body.spaceId as string;

    const invitation = await request(app.getHttpServer())
      .post('/spaces/invitations')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        spaceId,
        role: 'VIEWER',
        expiresInDays: 1,
        targetEmail: '  SMOKE-TARGET-MATCH@EXAMPLE.TEST  ',
      })
      .expect(201);
    const token = invitation.body.token as string;
    expect(invitation.body).toEqual(
      expect.objectContaining({
        spaceId,
        role: 'VIEWER',
        maskedTargetEmail: 's***@example.test',
      }),
    );
    expect(invitation.body).not.toHaveProperty('targetEmail');

    await request(app.getHttpServer())
      .get(`/spaces/invitations/${token}`)
      .set('Authorization', `Bearer ${mismatchToken}`)
      .expect(403);
    await request(app.getHttpServer())
      .post('/spaces/invitations/accept')
      .set('Authorization', `Bearer ${mismatchToken}`)
      .send({ token })
      .expect(403);

    await request(app.getHttpServer())
      .get(`/spaces/invitations/${token}`)
      .set('Authorization', `Bearer ${targetToken}`)
      .expect(200)
      .expect(({ body }) =>
        expect(body).toEqual(
          expect.objectContaining({
            spaceId,
            role: 'VIEWER',
            maskedTargetEmail: 's***@example.test',
          }),
        ),
      );

    await request(app.getHttpServer())
      .get(`/spaces/${spaceId}/invitations`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .expect(200)
      .expect(({ body }) => {
        const listed = (
          body as Array<{
            inviteId: string;
            maskedTargetEmail: string | null;
          }>
        ).find((item) => item.inviteId === invitation.body.inviteId);
        expect(listed).toEqual(
          expect.objectContaining({
            maskedTargetEmail: 's***@example.test',
          }),
        );
        expect(listed).not.toHaveProperty('token');
        expect(listed).not.toHaveProperty('targetEmail');
      });

    const audit = await app
      .get(DataSource)
      .getRepository(ChangeLogEntity)
      .findOneBy({
        entityType: 'INVITATION',
        entityId: invitation.body.inviteId as string,
        operation: 'CREATE',
      });
    expect(audit?.afterJson).toBeTruthy();
    const auditSnapshot = JSON.parse(audit?.afterJson ?? '{}') as Record<
      string,
      unknown
    >;
    expect(auditSnapshot.maskedTargetEmail).toBe('s***@example.test');
    expect(auditSnapshot).not.toHaveProperty('token');
    expect(auditSnapshot).not.toHaveProperty('targetEmail');

    await request(app.getHttpServer())
      .post('/spaces/invitations/accept')
      .set('Authorization', `Bearer ${targetToken}`)
      .send({ token })
      .expect(201)
      .expect(({ body }) =>
        expect(body).toEqual(
          expect.objectContaining({ spaceId, role: 'VIEWER' }),
        ),
      );
    process.stdout.write(
      'TARGETED_INVITATION_NORMALIZATION_MATCH_AND_MISMATCH: PASS\n',
    );
  });

  it('returns proposal comparison context and audits structured rejection reasons', async () => {
    const ownerToken = await register('proposal-review-owner');
    const space = await request(app.getHttpServer())
      .post('/spaces')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({ name: 'Proposal Review Context' })
      .expect(201);
    const spaceId = space.body.spaceId as string;
    const person = await request(app.getHttpServer())
      .post('/persons')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        spaceId,
        firstName: 'Review',
        nickName: 'Review',
        gender: 'UNKNOWN',
        birthPlace: 'Bandung',
      })
      .expect(201);
    const personId = person.body.personId as string;

    const proposal = await request(app.getHttpServer())
      .post('/proposals')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        spaceId,
        personId,
        field: 'birthPlace',
        proposedValue: 'Jakarta',
        reason: 'Ada sumber keluarga baru',
      })
      .expect(201);
    expect(proposal.body.beforeValue).toBe('Bandung');

    await request(app.getHttpServer())
      .get('/proposals')
      .query({ spaceId })
      .set('Authorization', `Bearer ${ownerToken}`)
      .expect(200)
      .expect(({ body }) =>
        expect(body).toEqual(
          expect.arrayContaining([
            expect.objectContaining({
              proposalId: proposal.body.proposalId,
              beforeValue: 'Bandung',
              currentValue: 'Bandung',
              proposedValue: 'Jakarta',
              reviewReason: null,
            }),
          ]),
        ),
      );

    await request(app.getHttpServer())
      .post('/proposals/reject')
      .set('Authorization', `Bearer ${ownerToken}`)
      .set('X-App-Version-Code', '3')
      .send({ spaceId, proposalId: proposal.body.proposalId })
      .expect(400);

    const rejected = await request(app.getHttpServer())
      .post('/proposals/reject')
      .set('Authorization', `Bearer ${ownerToken}`)
      .set('X-App-Version-Code', '3')
      .send({
        spaceId,
        proposalId: proposal.body.proposalId,
        reviewReason: '  Data belum didukung sumber  ',
      })
      .expect(201);
    expect(rejected.body).toEqual(
      expect.objectContaining({
        status: 'REJECTED',
        reviewReason: 'Data belum didukung sumber',
      }),
    );
    expect(rejected.body.reviewedAt).toEqual(expect.any(String));
    expect(rejected.body.reviewedByUserId).toEqual(expect.any(String));

    const audit = await app
      .get(DataSource)
      .getRepository(ChangeLogEntity)
      .findOneBy({
        entityType: 'PROPOSAL',
        entityId: proposal.body.proposalId as string,
        operation: 'VERIFY',
      });
    const auditAfter = JSON.parse(audit?.afterJson ?? '{}') as Record<
      string,
      unknown
    >;
    expect(auditAfter.reviewReason).toBe('Data belum didukung sumber');
    expect(auditAfter.reviewedAt).toBeTruthy();

    const legacyProposal = await request(app.getHttpServer())
      .post('/proposals')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        spaceId,
        personId,
        field: 'deathPlace',
        proposedValue: 'Surabaya',
      })
      .expect(201);
    await request(app.getHttpServer())
      .post('/proposals/reject')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        spaceId,
        proposalId: legacyProposal.body.proposalId,
      })
      .expect(201)
      .expect(({ body }) => {
        expect(body.status).toBe('REJECTED');
        expect(body.reviewReason).toBeNull();
      });
    process.stdout.write(
      'PROPOSAL_BEFORE_PROPOSED_REVIEW_REASON_AND_LEGACY_COMPATIBILITY: PASS\n',
    );
  });

  it('lists invitation lifecycle safely and enforces role-aware revocation', async () => {
    const ownerToken = await register('lifecycle-owner');
    const adminToken = await register('lifecycle-admin');
    const viewerToken = await register('lifecycle-viewer');

    const space = await request(app.getHttpServer())
      .post('/spaces')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({ name: 'Invitation Lifecycle Smoke' })
      .expect(201);
    const spaceId = space.body.spaceId as string;

    await inviteAndJoin(ownerToken, adminToken, spaceId, 'ADMIN');
    const pendingAdmin = await request(app.getHttpServer())
      .post('/spaces/invitations')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({ spaceId, role: 'ADMIN', expiresInDays: 1 })
      .expect(201);
    const pendingViewer = await request(app.getHttpServer())
      .post('/spaces/invitations')
      .set('Authorization', `Bearer ${adminToken}`)
      .send({ spaceId, role: 'VIEWER', expiresInDays: 1 })
      .expect(201);

    await request(app.getHttpServer())
      .get(`/spaces/${spaceId}/invitations`)
      .query({ status: 'ACTIVE' })
      .set('Authorization', `Bearer ${ownerToken}`)
      .expect(200)
      .expect(({ body }) => {
        expect(body).toEqual(
          expect.arrayContaining([
            expect.objectContaining({
              inviteId: pendingAdmin.body.inviteId,
              role: 'ADMIN',
              status: 'ACTIVE',
            }),
            expect.objectContaining({
              inviteId: pendingViewer.body.inviteId,
              role: 'VIEWER',
              status: 'ACTIVE',
            }),
          ]),
        );
        expect(body[0]).not.toHaveProperty('token');
      });

    await request(app.getHttpServer())
      .get(`/spaces/${spaceId}/invitations`)
      .set('Authorization', `Bearer ${adminToken}`)
      .expect(200)
      .expect(({ body }) => {
        const invitations = body as Array<{ role: string }>;
        expect(
          invitations.every((invitation) => invitation.role !== 'ADMIN'),
        ).toBe(true);
      });

    await request(app.getHttpServer())
      .delete(
        `/spaces/${spaceId}/invitations/${pendingAdmin.body.inviteId as string}`,
      )
      .set('Authorization', `Bearer ${adminToken}`)
      .expect(403);

    const revoked = await request(app.getHttpServer())
      .delete(
        `/spaces/${spaceId}/invitations/${pendingViewer.body.inviteId as string}`,
      )
      .set('Authorization', `Bearer ${adminToken}`)
      .expect(200);
    expect(revoked.body).toEqual(
      expect.objectContaining({
        inviteId: pendingViewer.body.inviteId,
        status: 'REVOKED',
      }),
    );

    await request(app.getHttpServer())
      .delete(
        `/spaces/${spaceId}/invitations/${pendingViewer.body.inviteId as string}`,
      )
      .set('Authorization', `Bearer ${adminToken}`)
      .expect(200)
      .expect(({ body }) => expect(body.status).toBe('REVOKED'));

    await request(app.getHttpServer())
      .get(`/spaces/invitations/${pendingViewer.body.token as string}`)
      .set('Authorization', `Bearer ${viewerToken}`)
      .expect(400);

    await request(app.getHttpServer())
      .get(`/spaces/${spaceId}/invitations`)
      .query({ status: 'REVOKED' })
      .set('Authorization', `Bearer ${adminToken}`)
      .expect(200)
      .expect(({ body }) =>
        expect(body).toEqual([
          expect.objectContaining({
            inviteId: pendingViewer.body.inviteId,
            status: 'REVOKED',
          }),
        ]),
      );

    const acceptedAdmin = await request(app.getHttpServer())
      .get(`/spaces/${spaceId}/invitations`)
      .query({ status: 'ACCEPTED' })
      .set('Authorization', `Bearer ${ownerToken}`)
      .expect(200);
    expect(acceptedAdmin.body).toHaveLength(1);
    await request(app.getHttpServer())
      .delete(
        `/spaces/${spaceId}/invitations/${
          acceptedAdmin.body[0].inviteId as string
        }`,
      )
      .set('Authorization', `Bearer ${ownerToken}`)
      .expect(409);

    await request(app.getHttpServer())
      .get(`/spaces/${spaceId}/invitations`)
      .set('Authorization', `Bearer ${viewerToken}`)
      .expect(403);

    await request(app.getHttpServer())
      .get('/changes')
      .query({ spaceId, limit: 20 })
      .set('Authorization', `Bearer ${ownerToken}`)
      .expect(200)
      .expect(({ body }) =>
        expect(body).toEqual(
          expect.arrayContaining([
            expect.objectContaining({
              entityType: 'INVITATION',
              operation: 'DELETE',
              note: 'Revoke invitation for role VIEWER',
            }),
          ]),
        ),
      );
    process.stdout.write(
      'INVITATION_LIST_HISTORY_ROLE_SCOPE_REVOKE_AND_AUDIT: PASS\n',
    );
  });
});
