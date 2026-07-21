/* eslint-disable @typescript-eslint/no-unsafe-assignment, @typescript-eslint/no-unsafe-member-access */
import { INestApplication, ValidationPipe } from '@nestjs/common';
import { Test, TestingModule } from '@nestjs/testing';
import request from 'supertest';
import { App } from 'supertest/types';
import { AppModule } from '../src/app.module';
import { OBJECT_STORAGE } from '../src/archive/storage/object-storage';

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
    process.stdout.write(
      'VIEWER_INVITE_JOIN_READ_AND_PROPOSE_WITH_DIRECT_EDIT_BLOCKED: PASS\n',
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
});
