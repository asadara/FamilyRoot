/* eslint-disable @typescript-eslint/no-unsafe-assignment, @typescript-eslint/no-unsafe-member-access */
import {
  INestApplication,
  UnauthorizedException,
  ValidationPipe,
} from '@nestjs/common';
import { Test, TestingModule } from '@nestjs/testing';
import request from 'supertest';
import { App } from 'supertest/types';
import { AppModule } from '../src/app.module';
import { randomUUID } from 'node:crypto';
import { OBJECT_STORAGE } from '../src/archive/storage/object-storage';
import {
  GOOGLE_ID_TOKEN_VERIFIER,
  type GoogleIdentity,
} from '../src/auth/google-id-token-verifier';

describe('Phase 1 security contract (e2e)', () => {
  let app: INestApplication<App>;
  let ownerToken: string;
  let ownerId: string;
  let viewerToken: string;
  let viewerId: string;
  let editorToken: string;
  let editorId: string;
  let adminToken: string;
  let adminId: string;
  let inviteeToken: string;
  let inviteeRefreshToken: string;
  let spaceId: string;
  let personId: string;
  const storedObjects = new Map<string, Buffer>();
  const googleNewUserToken = `google-new-${'x'.repeat(120)}`;
  const googleLinkToken = `google-link-${'x'.repeat(120)}`;
  const googleIdentities = new Map<string, GoogleIdentity>([
    [
      googleNewUserToken,
      {
        subject: 'google-subject-new-user',
        email: 'new.user@gmail.com',
        displayName: 'New Google User',
        hostedDomain: null,
      },
    ],
    [
      googleLinkToken,
      {
        subject: 'google-subject-linked-user',
        email: 'linked.user@gmail.com',
        displayName: 'Linked Google User',
        hostedDomain: null,
      },
    ],
  ]);

  beforeAll(async () => {
    const moduleFixture: TestingModule = await Test.createTestingModule({
      imports: [AppModule],
    })
      .overrideProvider(OBJECT_STORAGE)
      .useValue({
        putObject: (object: { path: string; body: Buffer }) => {
          storedObjects.set(object.path, object.body);
          return Promise.resolve();
        },
        deleteObject: (path: string) => {
          storedObjects.delete(path);
          return Promise.resolve();
        },
        createSignedReadUrl: (path: string) =>
          Promise.resolve(`https://storage.example.test/signed/${path}`),
      })
      .overrideProvider(GOOGLE_ID_TOKEN_VERIFIER)
      .useValue({
        verify: (idToken: string) => {
          const identity = googleIdentities.get(idToken);
          if (!identity) {
            return Promise.reject(
              new UnauthorizedException('Google ID token is invalid'),
            );
          }
          return Promise.resolve(identity);
        },
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

  afterAll(async () => {
    delete process.env.SYSTEM_ADMIN_USER_IDS;
    await app.close();
  });

  it('keeps health public but protects family data', async () => {
    const health = await request(app.getHttpServer())
      .get('/health')
      .set('x-request-id', 'phase4-health-check')
      .expect(200);
    expect(health.body.status).toBe('ok');
    expect(health.headers['x-request-id']).toBe('phase4-health-check');
    await request(app.getHttpServer())
      .get('/persons')
      .query({ spaceId: '00000000-0000-4000-8000-000000000000' })
      .expect(401);
  });

  it('publishes a safe default Android compatibility policy', async () => {
    await request(app.getHttpServer())
      .get('/app-compatibility/android')
      .query({
        versionCode: 1,
        versionName: '0.1.0-beta',
        apiContractVersion: 1,
        channel: 'PILOT',
      })
      .expect(200)
      .expect(({ body }) => {
        expect(body).toEqual(
          expect.objectContaining({
            status: 'COMPATIBLE',
            blocking: false,
            minimumSupportedVersionCode: 1,
            latestVersionCode: 1,
            backendApiContractVersion: 1,
          }),
        );
      });
  });

  it('registers authenticated users', async () => {
    const owner = await request(app.getHttpServer())
      .post('/auth/register')
      .send({
        email: 'owner@example.test',
        displayName: 'Owner',
        password: 'very-secure-owner-password',
      })
      .expect(201);
    ownerToken = owner.body.accessToken;
    ownerId = owner.body.user.userId;
    process.env.SYSTEM_ADMIN_USER_IDS = ownerId;

    const viewer = await request(app.getHttpServer())
      .post('/auth/register')
      .send({
        email: 'viewer@example.test',
        displayName: 'Viewer',
        password: 'very-secure-viewer-password',
      })
      .expect(201);
    viewerToken = viewer.body.accessToken;
    viewerId = viewer.body.user.userId;

    const editor = await request(app.getHttpServer())
      .post('/auth/register')
      .send({
        email: 'editor@example.test',
        displayName: 'Editor',
        password: 'very-secure-editor-password',
      })
      .expect(201);
    editorToken = editor.body.accessToken;
    editorId = editor.body.user.userId;

    const admin = await request(app.getHttpServer())
      .post('/auth/register')
      .send({
        email: 'admin@example.test',
        displayName: 'Admin',
        password: 'very-secure-admin-password',
      })
      .expect(201);
    adminToken = admin.body.accessToken;
    adminId = admin.body.user.userId;

    const invitee = await request(app.getHttpServer())
      .post('/auth/register')
      .send({
        email: 'invitee@example.test',
        displayName: 'Invitee',
        password: 'very-secure-invitee-password',
      })
      .expect(201);
    inviteeToken = invitee.body.accessToken;
    inviteeRefreshToken = invitee.body.refreshToken;
    expect(owner.body.user).not.toHaveProperty('passwordHash');

    await request(app.getHttpServer())
      .post('/auth/login')
      .send({
        email: 'owner@example.test',
        password: 'wrong-password',
      })
      .expect(401)
      .expect(({ body }) => expect(body.code).toBe('UNAUTHENTICATED'));
  });

  it('allows only a system administrator to change Android compatibility policy', async () => {
    const policy = {
      channel: 'PILOT',
      minimumSupportedVersionCode: 1,
      latestVersionCode: 2,
      apiContractVersion: 1,
      enforcementEnabled: false,
      updateUrl: 'https://familyroot.example.test/android',
      message: 'Versi pilot terbaru sudah tersedia.',
    };

    await request(app.getHttpServer())
      .put('/app-compatibility/android/policy')
      .set('Authorization', `Bearer ${viewerToken}`)
      .send(policy)
      .expect(403);

    await request(app.getHttpServer())
      .put('/app-compatibility/android/policy')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send(policy)
      .expect(200)
      .expect(({ body }) => {
        expect(body).toEqual(
          expect.objectContaining({
            channel: 'PILOT',
            minimumSupportedVersionCode: 1,
            latestVersionCode: 2,
            apiContractVersion: 1,
            updatedByUserId: ownerId,
          }),
        );
      });

    await request(app.getHttpServer())
      .get('/app-compatibility/android')
      .query({
        versionCode: 1,
        apiContractVersion: 1,
        channel: 'PILOT',
      })
      .expect(200)
      .expect(({ body }) => {
        expect(body.status).toBe('UPDATE_AVAILABLE');
        expect(body.blocking).toBe(false);
      });

    await request(app.getHttpServer())
      .get('/app-compatibility/android')
      .query({
        versionCode: 3,
        apiContractVersion: 1,
        channel: 'PILOT',
      })
      .expect(200)
      .expect(({ body }) => {
        expect(body.status).toBe('APP_TOO_NEW');
        expect(body.enforcementEnabled).toBe(false);
        expect(body.blocking).toBe(false);
      });

    await request(app.getHttpServer())
      .get('/app-compatibility/android')
      .query({
        versionCode: 2,
        apiContractVersion: 2,
        channel: 'PILOT',
      })
      .expect(200)
      .expect(({ body }) => {
        expect(body.status).toBe('API_CONTRACT_MISMATCH');
        expect(body.blocking).toBe(false);
      });
  });

  it('rotates refresh tokens, detects replay, and revokes logout sessions', async () => {
    const registered = await request(app.getHttpServer())
      .post('/auth/register')
      .send({
        email: 'refresh@example.test',
        displayName: 'Refresh Test',
        password: 'very-secure-refresh-password',
      })
      .expect(201);
    expect(registered.body.refreshToken).toEqual(expect.any(String));
    expect(registered.body.refreshExpiresIn).toBeGreaterThan(0);

    const rotated = await request(app.getHttpServer())
      .post('/auth/refresh')
      .send({ refreshToken: registered.body.refreshToken })
      .expect(200);
    expect(rotated.body.refreshToken).not.toBe(registered.body.refreshToken);
    await request(app.getHttpServer())
      .get('/auth/me')
      .set('Authorization', `Bearer ${rotated.body.accessToken}`)
      .expect(200);

    await request(app.getHttpServer())
      .post('/auth/refresh')
      .send({ refreshToken: registered.body.refreshToken })
      .expect(401)
      .expect(({ body }) => expect(body.code).toBe('UNAUTHENTICATED'));
    await request(app.getHttpServer())
      .post('/auth/refresh')
      .send({ refreshToken: rotated.body.refreshToken })
      .expect(401);

    const loggedIn = await request(app.getHttpServer())
      .post('/auth/login')
      .send({
        email: 'refresh@example.test',
        password: 'very-secure-refresh-password',
      })
      .expect(201);
    await request(app.getHttpServer())
      .post('/auth/logout')
      .send({ refreshToken: loggedIn.body.refreshToken })
      .expect(204);
    await request(app.getHttpServer())
      .post('/auth/refresh')
      .send({ refreshToken: loggedIn.body.refreshToken })
      .expect(401);
  });

  it('creates and safely links accounts through verified Google identities', async () => {
    const created = await request(app.getHttpServer())
      .post('/auth/google')
      .send({ idToken: googleNewUserToken })
      .expect(200);
    expect(created.body.user).toEqual(
      expect.objectContaining({
        email: 'new.user@gmail.com',
        displayName: 'New Google User',
      }),
    );

    await request(app.getHttpServer())
      .post('/auth/google')
      .send({ idToken: googleNewUserToken })
      .expect(200)
      .expect(({ body }) =>
        expect(body.user.userId).toBe(created.body.user.userId),
      );

    const passwordAccount = await request(app.getHttpServer())
      .post('/auth/register')
      .send({
        email: 'linked.user@gmail.com',
        displayName: 'Password User',
        password: 'very-secure-linked-password',
      })
      .expect(201);
    await request(app.getHttpServer())
      .post('/auth/google')
      .send({ idToken: googleLinkToken })
      .expect(200)
      .expect(({ body }) =>
        expect(body.user.userId).toBe(passwordAccount.body.user.userId),
      );

    await request(app.getHttpServer())
      .post('/auth/google')
      .send({ idToken: `invalid-${'x'.repeat(120)}` })
      .expect(401)
      .expect(({ body }) => expect(body.code).toBe('UNAUTHENTICATED'));
  });

  it('creates a Family Space and OWNER membership atomically', async () => {
    const created = await request(app.getHttpServer())
      .post('/spaces')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({ name: 'Secure Family' })
      .expect(201);
    spaceId = created.body.spaceId;

    const spaces = await request(app.getHttpServer())
      .get('/spaces')
      .set('Authorization', `Bearer ${ownerToken}`)
      .expect(200);
    expect(spaces.body).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ spaceId, role: 'OWNER' }),
      ]),
    );
  });

  it('enforces membership and role permissions', async () => {
    await request(app.getHttpServer())
      .get('/persons')
      .set('Authorization', `Bearer ${viewerToken}`)
      .query({ spaceId })
      .expect(403);

    await request(app.getHttpServer())
      .post('/spaces/members')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({ spaceId, userId: viewerId, role: 'VIEWER' })
      .expect(201);
    await request(app.getHttpServer())
      .post('/spaces/members')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({ spaceId, userId: editorId, role: 'EDITOR' })
      .expect(201);
    await request(app.getHttpServer())
      .post('/spaces/members')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({ spaceId, userId: adminId, role: 'ADMIN' })
      .expect(201);

    await request(app.getHttpServer())
      .get('/persons')
      .set('Authorization', `Bearer ${viewerToken}`)
      .query({ spaceId })
      .expect(200);

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

    await request(app.getHttpServer())
      .post('/persons')
      .set('Authorization', `Bearer ${editorToken}`)
      .send({
        spaceId,
        firstName: 'Allowed',
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
      .get('/export/space')
      .set('Authorization', `Bearer ${adminToken}`)
      .query({ spaceId })
      .expect(200);

    await request(app.getHttpServer())
      .post('/spaces/members')
      .set('Authorization', `Bearer ${adminToken}`)
      .send({ spaceId, userId: viewerId, role: 'ADMIN' })
      .expect(403);
  });

  it('lets a logged-in user join a Family Space by invitation token', async () => {
    const createdInvite = await request(app.getHttpServer())
      .post('/spaces/invitations')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({ spaceId, role: 'VIEWER', expiresInDays: 3 })
      .expect(201);

    expect(createdInvite.body).toEqual(
      expect.objectContaining({
        token: expect.any(String),
        role: 'VIEWER',
        spaceId,
        spaceName: 'Secure Family',
      }),
    );

    await request(app.getHttpServer())
      .get(`/spaces/invitations/${createdInvite.body.token}`)
      .set('Authorization', `Bearer ${inviteeToken}`)
      .expect(200)
      .expect(({ body }) => {
        expect(body).toEqual(
          expect.objectContaining({
            role: 'VIEWER',
            spaceId,
            spaceName: 'Secure Family',
          }),
        );
      });

    await request(app.getHttpServer())
      .post('/spaces/invitations/accept')
      .set('Authorization', `Bearer ${inviteeToken}`)
      .send({ token: createdInvite.body.token })
      .expect(201)
      .expect(({ body }) => {
        expect(body).toEqual(
          expect.objectContaining({ spaceId, role: 'VIEWER' }),
        );
      });

    await request(app.getHttpServer())
      .get('/spaces')
      .set('Authorization', `Bearer ${inviteeToken}`)
      .expect(200)
      .expect(({ body }) => {
        expect(body).toEqual(
          expect.arrayContaining([
            expect.objectContaining({ spaceId, role: 'VIEWER' }),
          ]),
        );
      });

    await request(app.getHttpServer())
      .post('/spaces/invitations/accept')
      .set('Authorization', `Bearer ${inviteeToken}`)
      .send({ token: createdInvite.body.token })
      .expect(409);
  });

  it('manages membership lifecycle without leaving a Family Space ownerless', async () => {
    const created = await request(app.getHttpServer())
      .post('/spaces')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({ name: 'Lifecycle Family' })
      .expect(201);
    const lifecycleSpaceId = created.body.spaceId as string;

    const adminMembership = await request(app.getHttpServer())
      .post('/spaces/members')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        spaceId: lifecycleSpaceId,
        userId: adminId,
        role: 'ADMIN',
      })
      .expect(201);
    const editorMembership = await request(app.getHttpServer())
      .post('/spaces/members')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        spaceId: lifecycleSpaceId,
        userId: editorId,
        role: 'EDITOR',
      })
      .expect(201);
    const viewerMembership = await request(app.getHttpServer())
      .post('/spaces/members')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        spaceId: lifecycleSpaceId,
        userId: viewerId,
        role: 'VIEWER',
      })
      .expect(201);

    await request(app.getHttpServer())
      .get(`/spaces/${lifecycleSpaceId}/members`)
      .set('Authorization', `Bearer ${viewerToken}`)
      .expect(200)
      .expect(({ body }) => {
        expect(body).toHaveLength(4);
        expect(body).toEqual(
          expect.arrayContaining([
            expect.objectContaining({
              memberId: viewerMembership.body.memberId,
              displayName: 'Viewer',
              role: 'VIEWER',
              isCurrentUser: true,
            }),
          ]),
        );
        expect(body[0]).not.toHaveProperty('email');
      });

    await request(app.getHttpServer())
      .patch(
        `/spaces/${lifecycleSpaceId}/members/${viewerMembership.body.memberId}`,
      )
      .set('Authorization', `Bearer ${adminToken}`)
      .send({ role: 'ADMIN' })
      .expect(403);

    await request(app.getHttpServer())
      .patch(
        `/spaces/${lifecycleSpaceId}/members/${viewerMembership.body.memberId}`,
      )
      .set('Authorization', `Bearer ${adminToken}`)
      .send({ role: 'EDITOR' })
      .expect(200)
      .expect(({ body }) => expect(body.role).toBe('EDITOR'));

    await request(app.getHttpServer())
      .post(`/spaces/${lifecycleSpaceId}/leave`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .expect(409);

    await request(app.getHttpServer())
      .post(`/spaces/${lifecycleSpaceId}/ownership-transfer`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({ targetMemberId: editorMembership.body.memberId })
      .expect(201)
      .expect(({ body }) => {
        expect(body.previousOwner.role).toBe('ADMIN');
        expect(body.owner).toEqual(
          expect.objectContaining({
            memberId: editorMembership.body.memberId,
            role: 'OWNER',
          }),
        );
      });

    await request(app.getHttpServer())
      .delete(
        `/spaces/${lifecycleSpaceId}/members/${adminMembership.body.memberId}`,
      )
      .set('Authorization', `Bearer ${editorToken}`)
      .expect(200)
      .expect(({ body }) => expect(body.removed).toBe(true));

    await request(app.getHttpServer())
      .post(`/spaces/${lifecycleSpaceId}/leave`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .expect(201)
      .expect(({ body }) => expect(body.left).toBe(true));

    await request(app.getHttpServer())
      .get(`/spaces/${lifecycleSpaceId}/members`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .expect(403);

    await request(app.getHttpServer())
      .post(`/spaces/${lifecycleSpaceId}/leave`)
      .set('Authorization', `Bearer ${editorToken}`)
      .expect(409);

    await request(app.getHttpServer())
      .get('/changes')
      .set('Authorization', `Bearer ${editorToken}`)
      .query({ spaceId: lifecycleSpaceId, limit: 10 })
      .expect(200)
      .expect(({ body }) => {
        const membershipLogs = (
          body as Array<{
            entityType: string;
            operation: string;
            note: string;
          }>
        ).filter(
          (item: { entityType: string }) => item.entityType === 'MEMBERSHIP',
        );
        expect(
          membershipLogs.some(
            (item: { operation: string; note: string }) =>
              item.operation === 'UPDATE' &&
              item.note.includes('Transfer ownership'),
          ),
        ).toBe(true);
        expect(
          membershipLogs.some(
            (item: { operation: string; note: string }) =>
              item.operation === 'DELETE' &&
              item.note.includes('left Family Space'),
          ),
        ).toBe(true);
      });
  });

  it('audits an authorized person mutation and restricts export', async () => {
    const createPersonMutationId = randomUUID();
    const createPersonPayload = {
      spaceId,
      firstName: 'Budi',
      nickName: 'Budi',
      gender: 'MALE',
      clientMutationId: createPersonMutationId,
    };
    const firstPerson = await request(app.getHttpServer())
      .post('/persons')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send(createPersonPayload)
      .expect(201);
    personId = firstPerson.body.personId as string;
    await request(app.getHttpServer())
      .post('/persons')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send(createPersonPayload)
      .expect(201)
      .expect(({ body }) =>
        expect(body.personId).toBe(firstPerson.body.personId),
      );

    const mutationId = randomUUID();
    const lifeMutation = {
      spaceId,
      lifeStatus: 'UNKNOWN',
      expectedVersion: firstPerson.body.version,
      clientMutationId: mutationId,
    };
    const firstUpdate = await request(app.getHttpServer())
      .patch(`/persons/${firstPerson.body.personId}/life`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .send(lifeMutation)
      .expect(200);
    expect(firstUpdate.body.version).toBe(firstPerson.body.version + 1);

    await request(app.getHttpServer())
      .patch(`/persons/${firstPerson.body.personId}/life`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .send(lifeMutation)
      .expect(200)
      .expect(({ body }) =>
        expect(body.version).toBe(firstUpdate.body.version),
      );

    await request(app.getHttpServer())
      .patch(`/persons/${firstPerson.body.personId}/life`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        ...lifeMutation,
        clientMutationId: randomUUID(),
        lifeStatus: 'ALIVE',
      })
      .expect(409)
      .expect(({ body }) => {
        expect(body.code).toBe('CONFLICT');
        expect(body.details.version).toBe(firstUpdate.body.version);
      });

    const profileMutationId = randomUUID();
    const profileMutation = {
      spaceId,
      birthPlace: 'Bandung',
      notes: 'Profile edited from the offline-capable client',
      expectedVersion: firstUpdate.body.version,
      clientMutationId: profileMutationId,
    };
    const profileUpdate = await request(app.getHttpServer())
      .patch(`/persons/${firstPerson.body.personId}/profile`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .send(profileMutation)
      .expect(200);
    expect(profileUpdate.body).toEqual(
      expect.objectContaining({
        birthPlace: 'Bandung',
        notes: profileMutation.notes,
        version: firstUpdate.body.version + 1,
      }),
    );

    await request(app.getHttpServer())
      .patch(`/persons/${firstPerson.body.personId}/profile`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .send(profileMutation)
      .expect(200)
      .expect(({ body }) =>
        expect(body.version).toBe(profileUpdate.body.version),
      );

    await request(app.getHttpServer())
      .patch(`/persons/${firstPerson.body.personId}/profile`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({ ...profileMutation, clientMutationId: randomUUID() })
      .expect(409)
      .expect(({ body }) => {
        expect(body.details).toEqual(
          expect.objectContaining({
            version: profileUpdate.body.version,
            birthPlace: 'Bandung',
            notes: profileMutation.notes,
          }),
        );
      });

    const child = await request(app.getHttpServer())
      .post('/persons')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({ spaceId, firstName: 'Child', nickName: 'Child', gender: 'MALE' })
      .expect(201);
    const parentMutation = {
      spaceId,
      parentId: firstPerson.body.personId,
      childId: child.body.personId,
      meta: 'BIOLOGICAL',
      clientMutationId: randomUUID(),
    };
    const parentRelation = await request(app.getHttpServer())
      .post('/persons/parent-child')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send(parentMutation)
      .expect(201);
    await request(app.getHttpServer())
      .post('/persons/parent-child')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send(parentMutation)
      .expect(201)
      .expect(({ body }) =>
        expect(body.relationshipId).toBe(parentRelation.body.relationshipId),
      );

    const spouse = await request(app.getHttpServer())
      .post('/persons')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        spaceId,
        firstName: 'Spouse',
        nickName: 'Spouse',
        gender: 'FEMALE',
      })
      .expect(201);
    const spouseMutation = {
      spaceId,
      personAId: firstPerson.body.personId,
      personBId: spouse.body.personId,
      meta: 'MARRIED',
      startDate: '2020-01-01',
      clientMutationId: randomUUID(),
    };
    const spouseRelation = await request(app.getHttpServer())
      .post('/relationships/spouse')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send(spouseMutation)
      .expect(201);
    await request(app.getHttpServer())
      .post('/relationships/spouse')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send(spouseMutation)
      .expect(201)
      .expect(({ body }) =>
        expect(body.relationshipId).toBe(spouseRelation.body.relationshipId),
      );
    const spouseStatusMutation = {
      spaceId,
      meta: 'DIVORCED',
      startDate: '2020-01-01',
      endDate: '2025-06-01',
      clientMutationId: randomUUID(),
    };
    const divorcedRelation = await request(app.getHttpServer())
      .patch(`/relationships/${spouseRelation.body.relationshipId}/spouse`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .send(spouseStatusMutation)
      .expect(200);
    expect(divorcedRelation.body).toEqual(
      expect.objectContaining({
        meta: 'DIVORCED',
        startDate: '2020-01-01',
        endDate: '2025-06-01',
      }),
    );
    await request(app.getHttpServer())
      .patch(`/relationships/${spouseRelation.body.relationshipId}/spouse`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .send(spouseStatusMutation)
      .expect(200)
      .expect(({ body }) => expect(body.meta).toBe('DIVORCED'));
    await request(app.getHttpServer())
      .patch(`/relationships/${spouseRelation.body.relationshipId}/spouse`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        spaceId,
        meta: 'MARRIED',
        startDate: '2020-01-01',
        endDate: '2025-06-01',
        clientMutationId: randomUUID(),
      })
      .expect(200)
      .expect(({ body }) => {
        expect(body.meta).toBe('MARRIED');
        expect(body.endDate).toBeNull();
      });
    await request(app.getHttpServer())
      .post('/persons/parent-child')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        spaceId,
        parentId: firstPerson.body.personId,
        childId: spouse.body.personId,
        meta: 'BIOLOGICAL',
        clientMutationId: randomUUID(),
      })
      .expect(400)
      .expect(({ body }) =>
        expect(body.message).toContain('cannot be created between spouses'),
      );

    const femaleDescendant = await request(app.getHttpServer())
      .post('/persons')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        spaceId,
        firstName: 'Daughter',
        nickName: 'Daughter',
        gender: 'FEMALE',
      })
      .expect(201);
    await request(app.getHttpServer())
      .post('/persons/parent-child')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        spaceId,
        parentId: firstPerson.body.personId,
        childId: femaleDescendant.body.personId,
        meta: 'BIOLOGICAL',
        clientMutationId: randomUUID(),
      })
      .expect(201);
    await request(app.getHttpServer())
      .post('/relationships/spouse')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        spaceId,
        personAId: firstPerson.body.personId,
        personBId: femaleDescendant.body.personId,
        meta: 'MARRIED',
        startDate: '2024-01-01',
        clientMutationId: randomUUID(),
      })
      .expect(400)
      .expect(({ body }) =>
        expect(body.message).toContain('between ancestor and descendant'),
      );

    const nextGenerationRelative = await request(app.getHttpServer())
      .post('/persons')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        spaceId,
        firstName: 'Granddaughter',
        nickName: 'Granddaughter',
        gender: 'FEMALE',
      })
      .expect(201);
    await request(app.getHttpServer())
      .post('/persons/parent-child')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        spaceId,
        parentId: femaleDescendant.body.personId,
        childId: nextGenerationRelative.body.personId,
        meta: 'BIOLOGICAL',
        clientMutationId: randomUUID(),
      })
      .expect(201);
    await request(app.getHttpServer())
      .post('/relationships/spouse')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        spaceId,
        personAId: child.body.personId,
        personBId: nextGenerationRelative.body.personId,
        meta: 'MARRIED',
        startDate: '2024-01-01',
        clientMutationId: randomUUID(),
      })
      .expect(400)
      .expect(({ body }) =>
        expect(body.message).toContain('same lineage generation'),
      );
    await request(app.getHttpServer())
      .get('/relationships')
      .set('Authorization', `Bearer ${ownerToken}`)
      .query({ spaceId })
      .expect(200)
      .expect(({ body }) => {
        expect(body).toEqual(
          expect.arrayContaining([
            expect.objectContaining({ type: 'PARENT_CHILD' }),
            expect.objectContaining({ type: 'SPOUSE' }),
          ]),
        );
      });

    const secondSpace = await request(app.getHttpServer())
      .post('/spaces')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({ name: 'Other Family' })
      .expect(201);
    const outsider = await request(app.getHttpServer())
      .post('/persons')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        spaceId: secondSpace.body.spaceId,
        firstName: 'Other',
        nickName: 'Other',
        gender: 'UNKNOWN',
      })
      .expect(201);
    await request(app.getHttpServer())
      .post('/persons/parent-child')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        spaceId,
        parentId: firstPerson.body.personId,
        childId: outsider.body.personId,
        meta: 'BIOLOGICAL',
        clientMutationId: randomUUID(),
      })
      .expect(400)
      .expect(({ body }) => expect(body.code).toBe('VALIDATION_ERROR'));

    const changes = await request(app.getHttpServer())
      .get('/changes')
      .set('Authorization', `Bearer ${ownerToken}`)
      .query({ spaceId })
      .expect(200);
    expect(changes.body).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          entityType: 'PERSON',
          operation: 'CREATE',
          actorDisplayName: expect.any(String),
        }),
      ]),
    );

    await request(app.getHttpServer())
      .get('/changes/full')
      .set('Authorization', `Bearer ${editorToken}`)
      .query({ spaceId })
      .expect(403);

    const historyRequest = await request(app.getHttpServer())
      .post('/changes/history-access-requests')
      .set('Authorization', `Bearer ${editorToken}`)
      .send({ spaceId })
      .expect(201);
    expect(historyRequest.body.status).toBe('PENDING');

    await request(app.getHttpServer())
      .post(
        `/changes/history-access-requests/${historyRequest.body.requestId}/review`,
      )
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({ spaceId, approved: true })
      .expect(201)
      .expect(({ body }) => expect(body.status).toBe('APPROVED'));

    await request(app.getHttpServer())
      .get('/changes/full')
      .set('Authorization', `Bearer ${editorToken}`)
      .query({ spaceId, limit: 2 })
      .expect(200)
      .expect(({ body }) => {
        expect(body.items).toHaveLength(2);
        expect(body.nextCursor).toEqual(expect.any(String));
      });

    await request(app.getHttpServer())
      .get('/export/space')
      .set('Authorization', `Bearer ${viewerToken}`)
      .query({ spaceId })
      .expect(403);
    await request(app.getHttpServer())
      .get('/export/space')
      .set('Authorization', `Bearer ${ownerToken}`)
      .query({ spaceId })
      .expect(200);

    const gedcom = await request(app.getHttpServer())
      .get('/export/space/gedcom')
      .set('Authorization', `Bearer ${ownerToken}`)
      .query({ spaceId })
      .expect(200);
    expect(gedcom.body.content).toContain('0 HEAD');
    expect(gedcom.body.content).toContain('1 CHIL');

    await request(app.getHttpServer())
      .delete(`/relationships/${spouseRelation.body.relationshipId}`)
      .set('Authorization', `Bearer ${viewerToken}`)
      .query({ spaceId })
      .expect(403);
    const deleteRelationshipMutationId = randomUUID();
    await request(app.getHttpServer())
      .delete(`/relationships/${spouseRelation.body.relationshipId}`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .query({ spaceId, clientMutationId: deleteRelationshipMutationId })
      .expect(200)
      .expect(({ body }) => expect(body.deleted).toBe(true));
    await request(app.getHttpServer())
      .delete(`/relationships/${spouseRelation.body.relationshipId}`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .query({ spaceId, clientMutationId: deleteRelationshipMutationId })
      .expect(200)
      .expect(({ body }) => expect(body.deleted).toBe(true));
    await request(app.getHttpServer())
      .get('/relationships')
      .set('Authorization', `Bearer ${ownerToken}`)
      .query({ spaceId })
      .expect(200)
      .expect(({ body }) =>
        expect(body).not.toEqual(
          expect.arrayContaining([
            expect.objectContaining({
              relationshipId: spouseRelation.body.relationshipId,
            }),
          ]),
        ),
      );

    const gedcomTarget = await request(app.getHttpServer())
      .post('/spaces')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({ name: 'GEDCOM Restore' })
      .expect(201);
    await request(app.getHttpServer())
      .post('/export/space/gedcom/import')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        spaceId: gedcomTarget.body.spaceId,
        content: gedcom.body.content,
      })
      .expect(201)
      .expect(({ body }) => expect(body.personCount).toBeGreaterThan(0));

    const backup = await request(app.getHttpServer())
      .get('/export/space/backup')
      .set('Authorization', `Bearer ${ownerToken}`)
      .query({ spaceId })
      .expect(200);
    expect(backup.body).toEqual(
      expect.objectContaining({
        format: 'familyroot-backup',
        schemaVersion: 1,
      }),
    );
    const backupTarget = await request(app.getHttpServer())
      .post('/spaces')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({ name: 'Backup Restore' })
      .expect(201);
    await request(app.getHttpServer())
      .post('/export/space/backup/restore')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({ spaceId: backupTarget.body.spaceId, backup: backup.body })
      .expect(201)
      .expect(({ body }) => {
        expect(body.personCount).toBe(backup.body.persons.length);
        expect(body.relationshipCount).toBe(backup.body.relationships.length);
      });
    await request(app.getHttpServer())
      .post('/export/space/backup/restore')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({ spaceId: backupTarget.body.spaceId, backup: backup.body })
      .expect(400);
  });

  it('validates, stores, and authorizes private image access', async () => {
    const onePixelPng = Buffer.from(
      'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
      'base64',
    );

    await request(app.getHttpServer())
      .post(`/persons/${personId}/media/upload`)
      .set('Authorization', `Bearer ${viewerToken}`)
      .query({ spaceId })
      .field('label', 'Private family photo')
      .attach('file', onePixelPng, {
        filename: 'misleading.txt',
        contentType: 'text/plain',
      })
      .expect(403);

    const uploaded = await request(app.getHttpServer())
      .post(`/persons/${personId}/media/upload`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .query({ spaceId })
      .field('label', 'Private family photo')
      .attach('file', onePixelPng, {
        filename: 'misleading.txt',
        contentType: 'text/plain',
      })
      .expect(201);

    expect(uploaded.body).toEqual(
      expect.objectContaining({
        personId,
        kind: 'PHOTO',
        uri: expect.stringMatching(/^object:\/\//),
      }),
    );
    expect(storedObjects.size).toBe(1);

    await request(app.getHttpServer())
      .get(`/persons/${personId}/media/${uploaded.body.mediaId}/access`)
      .set('Authorization', `Bearer ${viewerToken}`)
      .query({ spaceId })
      .expect(404);

    await request(app.getHttpServer())
      .get(`/persons/${personId}/media/${uploaded.body.mediaId}/access`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .query({ spaceId })
      .expect(200)
      .expect(({ body }) => {
        expect(body.url).toMatch(/^https:\/\/storage\.example\.test\/signed\//);
        expect(body.expiresIn).toBe(60);
      });

    const replacement = await request(app.getHttpServer())
      .post(`/persons/${personId}/media/upload`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .query({ spaceId })
      .field('label', 'Replacement profile photo')
      .attach('file', onePixelPng, {
        filename: 'replacement.png',
        contentType: 'image/png',
      })
      .expect(201);

    expect(replacement.body.mediaId).not.toBe(uploaded.body.mediaId);
    expect(replacement.body.url).toMatch(
      /^https:\/\/storage\.example\.test\/signed\//,
    );
    expect(storedObjects.size).toBe(1);
    await request(app.getHttpServer())
      .get(`/persons/${personId}/media/${uploaded.body.mediaId}/access`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .query({ spaceId })
      .expect(404);
  });

  it('blocks unsafe person deletion and supports reviewed editor requests', async () => {
    await request(app.getHttpServer())
      .get(`/persons/${personId}/deletion-impact`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .query({ spaceId })
      .expect(200)
      .expect(({ body }) => {
        expect(body.canDelete).toBe(false);
        expect(body.relationshipCount).toBeGreaterThan(0);
        expect(body.mediaCount).toBeGreaterThan(0);
        expect(body.blockers).toEqual(
          expect.arrayContaining([
            expect.objectContaining({ code: 'RELATIONSHIPS' }),
            expect.objectContaining({ code: 'MEDIA' }),
          ]),
        );
      });

    await request(app.getHttpServer())
      .delete(`/persons/${personId}`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({ spaceId })
      .expect(409)
      .expect(({ body }) => {
        expect(body.code).toBe('CONFLICT');
        expect(body.details.impact.canDelete).toBe(false);
      });

    const directDeletePerson = await request(app.getHttpServer())
      .post('/persons')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        spaceId,
        firstName: 'Temporary',
        nickName: 'Temporary',
        gender: 'UNKNOWN',
      })
      .expect(201);

    await request(app.getHttpServer())
      .get(`/persons/${directDeletePerson.body.personId}/deletion-impact`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .query({ spaceId })
      .expect(200)
      .expect(({ body }) => expect(body.canDelete).toBe(true));

    await request(app.getHttpServer())
      .delete(`/persons/${directDeletePerson.body.personId}`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({ spaceId })
      .expect(200)
      .expect(({ body }) => expect(body.deleted).toBe(true));

    const requestedDeletePerson = await request(app.getHttpServer())
      .post('/persons')
      .set('Authorization', `Bearer ${editorToken}`)
      .send({
        spaceId,
        firstName: 'Editor request',
        nickName: 'Editor request',
        gender: 'UNKNOWN',
      })
      .expect(201);

    await request(app.getHttpServer())
      .delete(`/persons/${requestedDeletePerson.body.personId}`)
      .set('Authorization', `Bearer ${editorToken}`)
      .send({ spaceId })
      .expect(403);

    await request(app.getHttpServer())
      .post(`/persons/${requestedDeletePerson.body.personId}/deletion-requests`)
      .set('Authorization', `Bearer ${viewerToken}`)
      .send({ spaceId, reason: 'Viewer should not be allowed' })
      .expect(403);

    const proposal = await request(app.getHttpServer())
      .post(`/persons/${requestedDeletePerson.body.personId}/deletion-requests`)
      .set('Authorization', `Bearer ${editorToken}`)
      .send({ spaceId, reason: 'Data ujicoba sudah tidak diperlukan' })
      .expect(201);
    expect(proposal.body).toEqual(
      expect.objectContaining({
        field: 'DELETE_PERSON',
        status: 'PENDING',
      }),
    );

    await request(app.getHttpServer())
      .post(`/persons/${requestedDeletePerson.body.personId}/deletion-requests`)
      .set('Authorization', `Bearer ${editorToken}`)
      .send({ spaceId, reason: 'Duplicate request' })
      .expect(409);

    await request(app.getHttpServer())
      .post('/proposals/approve')
      .set('Authorization', `Bearer ${adminToken}`)
      .send({ spaceId, proposalId: proposal.body.proposalId })
      .expect(201)
      .expect(({ body }) => expect(body.status).toBe('APPROVED'));

    await request(app.getHttpServer())
      .get('/persons')
      .set('Authorization', `Bearer ${ownerToken}`)
      .query({ spaceId })
      .expect(200)
      .expect(({ body }) =>
        expect(body).not.toEqual(
          expect.arrayContaining([
            expect.objectContaining({
              personId: requestedDeletePerson.body.personId,
            }),
          ]),
        ),
      );

    await request(app.getHttpServer())
      .get('/changes/full')
      .set('Authorization', `Bearer ${ownerToken}`)
      .query({ spaceId, limit: 50 })
      .expect(200)
      .expect(({ body }) =>
        expect(body.items).toEqual(
          expect.arrayContaining([
            expect.objectContaining({
              entityType: 'PERSON',
              operation: 'DELETE',
              note: 'Soft delete person',
            }),
          ]),
        ),
      );
  });

  it('deletes an account without deleting its Person or family history', async () => {
    await request(app.getHttpServer())
      .get('/users/me/deletion-impact')
      .set('Authorization', `Bearer ${ownerToken}`)
      .expect(200)
      .expect(({ body }) => {
        expect(body.canDeleteAccount).toBe(false);
        expect(body.blockers).toContain('TRANSFER_OWNERSHIP');
        expect(body.ownedSpaces.length).toBeGreaterThan(0);
      });

    await request(app.getHttpServer())
      .delete('/users/me')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({ confirmation: 'HAPUS AKUN' })
      .expect(409);

    const retainedPerson = await request(app.getHttpServer())
      .post('/persons')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        spaceId,
        firstName: 'Profil',
        nickName: 'Tetap Ada',
        gender: 'UNKNOWN',
      })
      .expect(201);

    await request(app.getHttpServer())
      .post('/claims')
      .set('Authorization', `Bearer ${inviteeToken}`)
      .send({ spaceId, personId: retainedPerson.body.personId })
      .expect(201);

    await request(app.getHttpServer())
      .get('/claims/me')
      .set('Authorization', `Bearer ${inviteeToken}`)
      .query({ spaceId })
      .expect(200)
      .expect(({ body }) => {
        expect(body.claim).toEqual(
          expect.objectContaining({
            personId: retainedPerson.body.personId,
            personName: 'Profil',
            status: 'PENDING',
          }),
        );
      });

    await request(app.getHttpServer())
      .get(`/spaces/${spaceId}/profile-photos/me`)
      .set('Authorization', `Bearer ${inviteeToken}`)
      .expect(200)
      .expect(({ body }) => expect(body).toEqual({ photo: null }));

    await request(app.getHttpServer())
      .get('/users/me/deletion-impact')
      .set('Authorization', `Bearer ${inviteeToken}`)
      .expect(200)
      .expect(({ body }) => {
        expect(body.canDeleteAccount).toBe(true);
        expect(body.membershipCount).toBe(1);
        expect(body.claimCount).toBe(1);
        expect(body.activeSessionCount).toBeGreaterThan(0);
      });

    await request(app.getHttpServer())
      .delete('/users/me')
      .set('Authorization', `Bearer ${inviteeToken}`)
      .send({ confirmation: 'delete' })
      .expect(400);

    await request(app.getHttpServer())
      .delete('/users/me')
      .set('Authorization', `Bearer ${inviteeToken}`)
      .send({ confirmation: 'HAPUS AKUN' })
      .expect(200)
      .expect(({ body }) => expect(body.deleted).toBe(true));

    await request(app.getHttpServer())
      .get('/spaces')
      .set('Authorization', `Bearer ${inviteeToken}`)
      .expect(401);

    await request(app.getHttpServer())
      .post('/auth/refresh')
      .send({ refreshToken: inviteeRefreshToken })
      .expect(401);

    await request(app.getHttpServer())
      .get('/persons')
      .set('Authorization', `Bearer ${ownerToken}`)
      .query({ spaceId })
      .expect(200)
      .expect(({ body }) =>
        expect(body).toEqual(
          expect.arrayContaining([
            expect.objectContaining({
              personId: retainedPerson.body.personId,
              fullName: 'Profil',
            }),
          ]),
        ),
      );

    await request(app.getHttpServer())
      .get('/changes/full')
      .set('Authorization', `Bearer ${ownerToken}`)
      .query({ spaceId, limit: 50 })
      .expect(200)
      .expect(({ body }) =>
        expect(body.items).toEqual(
          expect.arrayContaining([
            expect.objectContaining({
              entityType: 'MEMBERSHIP',
              operation: 'DELETE',
              note: expect.stringContaining('Account deletion'),
            }),
          ]),
        ),
      );
  });

  it('archives, restores, and safely soft-deletes a Family Space', async () => {
    const disposableSpace = await request(app.getHttpServer())
      .post('/spaces')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({ name: 'Silsilah sementara' })
      .expect(201);
    const disposableSpaceId = disposableSpace.body.spaceId as string;

    await request(app.getHttpServer())
      .post('/persons')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        spaceId: disposableSpaceId,
        firstName: 'Data',
        nickName: 'Arsip',
        gender: 'UNKNOWN',
      })
      .expect(201);

    await request(app.getHttpServer())
      .get(`/spaces/${disposableSpaceId}/lifecycle-impact`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .expect(200)
      .expect(({ body }) => {
        expect(body.status).toBe('ACTIVE');
        expect(body.personCount).toBe(1);
        expect(body.canArchive).toBe(true);
        expect(body.canDelete).toBe(false);
      });

    await request(app.getHttpServer())
      .delete(`/spaces/${disposableSpaceId}`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        confirmation: 'Silsilah sementara',
        acknowledgeExport: true,
      })
      .expect(409);

    await request(app.getHttpServer())
      .post(`/spaces/${disposableSpaceId}/archive`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .expect(201)
      .expect(({ body }) => expect(body.status).toBe('ARCHIVED'));

    await request(app.getHttpServer())
      .post('/persons')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        spaceId: disposableSpaceId,
        firstName: 'Ditolak',
        nickName: 'Read only',
        gender: 'UNKNOWN',
      })
      .expect(409);

    await request(app.getHttpServer())
      .get('/persons')
      .set('Authorization', `Bearer ${ownerToken}`)
      .query({ spaceId: disposableSpaceId })
      .expect(200)
      .expect(({ body }) => expect(body).toHaveLength(1));

    await request(app.getHttpServer())
      .post(`/spaces/${disposableSpaceId}/restore`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .expect(201)
      .expect(({ body }) => expect(body.status).toBe('ACTIVE'));

    await request(app.getHttpServer())
      .post(`/spaces/${disposableSpaceId}/archive`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .expect(201);

    await request(app.getHttpServer())
      .delete(`/spaces/${disposableSpaceId}`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        confirmation: 'nama yang salah',
        acknowledgeExport: true,
      })
      .expect(400);

    await request(app.getHttpServer())
      .delete(`/spaces/${disposableSpaceId}`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        confirmation: 'Silsilah sementara',
        acknowledgeExport: true,
      })
      .expect(200)
      .expect(({ body }) => expect(body.deleted).toBe(true));

    await request(app.getHttpServer())
      .get('/spaces')
      .set('Authorization', `Bearer ${ownerToken}`)
      .expect(200)
      .expect(({ body }) =>
        expect(body).not.toEqual(
          expect.arrayContaining([
            expect.objectContaining({ spaceId: disposableSpaceId }),
          ]),
        ),
      );
  });

  it('can enforce version headers after a staged rollout', async () => {
    await request(app.getHttpServer())
      .put('/app-compatibility/android/policy')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        channel: 'PILOT',
        minimumSupportedVersionCode: 2,
        latestVersionCode: 2,
        apiContractVersion: 1,
        enforcementEnabled: true,
        updateUrl: 'https://familyroot.example.test/android',
        message: 'Perbarui aplikasi untuk melanjutkan.',
      })
      .expect(200);

    await request(app.getHttpServer())
      .get('/spaces')
      .set('Authorization', `Bearer ${ownerToken}`)
      .expect(426)
      .expect(({ body }) => expect(body.code).toBe('UPGRADE_REQUIRED'));

    await request(app.getHttpServer())
      .get('/spaces')
      .set('Authorization', `Bearer ${ownerToken}`)
      .set('X-App-Version-Code', '1')
      .set('X-Api-Contract-Version', '1')
      .set('X-Release-Channel', 'PILOT')
      .expect(426);

    await request(app.getHttpServer())
      .get('/spaces')
      .set('Authorization', `Bearer ${ownerToken}`)
      .set('X-App-Version-Code', '2')
      .set('X-Api-Contract-Version', '1')
      .set('X-Release-Channel', 'PILOT')
      .expect(200);
  });
});
