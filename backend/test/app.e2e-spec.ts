/* eslint-disable @typescript-eslint/no-unsafe-assignment, @typescript-eslint/no-unsafe-member-access */
import { INestApplication, ValidationPipe } from '@nestjs/common';
import { Test, TestingModule } from '@nestjs/testing';
import request from 'supertest';
import { App } from 'supertest/types';
import { AppModule } from '../src/app.module';

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

  beforeAll(async () => {
    const moduleFixture: TestingModule = await Test.createTestingModule({
      imports: [AppModule],
    }).compile();
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
    await request(app.getHttpServer()).get('/').expect(200);
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
  });
});
