/* eslint-disable @typescript-eslint/no-unsafe-assignment, @typescript-eslint/no-unsafe-member-access */
import { INestApplication, ValidationPipe } from '@nestjs/common';
import { Test, TestingModule } from '@nestjs/testing';
import request from 'supertest';
import { App } from 'supertest/types';
import { AppModule } from '../src/app.module';
import { randomUUID } from 'node:crypto';
import { OBJECT_STORAGE } from '../src/archive/storage/object-storage';

describe('Phase 1 security contract (e2e)', () => {
  let app: INestApplication<App>;
  let ownerToken: string;
  let viewerToken: string;
  let viewerId: string;
  let editorToken: string;
  let editorId: string;
  let adminToken: string;
  let adminId: string;
  let inviteeToken: string;
  let spaceId: string;
  let personId: string;
  const storedObjects = new Map<string, Buffer>();

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

  it('audits an authorized person mutation and restricts export', async () => {
    const firstPerson = await request(app.getHttpServer())
      .post('/persons')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({ spaceId, firstName: 'Budi', nickName: 'Budi', gender: 'MALE' })
      .expect(201);
    personId = firstPerson.body.personId as string;

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
        expect.objectContaining({ entityType: 'PERSON', operation: 'CREATE' }),
      ]),
    );

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
      .expect(200)
      .expect(({ body }) => {
        expect(body.url).toMatch(/^https:\/\/storage\.example\.test\/signed\//);
        expect(body.expiresIn).toBe(60);
      });
  });
});
