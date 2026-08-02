/* eslint-disable @typescript-eslint/no-unsafe-assignment, @typescript-eslint/no-unsafe-member-access */
import { INestApplication, ValidationPipe } from '@nestjs/common';
import { Test, TestingModule } from '@nestjs/testing';
import request from 'supertest';
import { App } from 'supertest/types';
import { AppModule } from '../src/app.module';
import { OBJECT_STORAGE } from '../src/archive/storage/object-storage';

describe('Collective claim confirmation (e2e)', () => {
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
          Promise.resolve('https://storage.example.test/claim'),
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
        email: `claim-${label}@example.test`,
        displayName: `Claim ${label}`,
        password: `claim-${label}-password`,
      })
      .expect(201);
    return response.body.accessToken as string;
  }

  async function joinAsAdmin(
    ownerToken: string,
    memberToken: string,
    spaceId: string,
  ) {
    const invitation = await request(app.getHttpServer())
      .post('/spaces/invitations')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({ spaceId, role: 'ADMIN', expiresInDays: 1 })
      .expect(201);
    await request(app.getHttpServer())
      .post('/spaces/invitations/accept')
      .set('Authorization', `Bearer ${memberToken}`)
      .send({ token: invitation.body.token })
      .expect(201);
  }

  it('requires two independent reviewers and audits each recorded confirmation', async () => {
    const ownerToken = await register('owner');
    const claimantToken = await register('claimant');
    const reviewerToken = await register('reviewer');

    const space = await request(app.getHttpServer())
      .post('/spaces')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({ name: 'Collective Claim Family' })
      .expect(201);
    const spaceId = space.body.spaceId as string;

    await joinAsAdmin(ownerToken, claimantToken, spaceId);
    await joinAsAdmin(ownerToken, reviewerToken, spaceId);

    const person = await request(app.getHttpServer())
      .post('/persons')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        spaceId,
        firstName: 'Pemilik',
        nickName: 'Claim',
        gender: 'UNKNOWN',
      })
      .expect(201);
    const claim = await request(app.getHttpServer())
      .post('/claims')
      .set('Authorization', `Bearer ${claimantToken}`)
      .send({ spaceId, personId: person.body.personId })
      .expect(201);
    const claimId = claim.body.claimId as string;
    const onePixelPng = Buffer.from(
      'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
      'base64',
    );

    await request(app.getHttpServer())
      .post(`/persons/${person.body.personId}/media/upload`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .query({ spaceId })
      .field('label', 'Foto klaim pending')
      .attach('file', onePixelPng, {
        filename: 'pending.png',
        contentType: 'image/png',
      })
      .expect(201);

    await request(app.getHttpServer())
      .get(`/spaces/${spaceId}/profile-photos/me`)
      .set('Authorization', `Bearer ${claimantToken}`)
      .expect(200)
      .expect(({ body }) => {
        expect(body.photo).toEqual(
          expect.objectContaining({
            personId: person.body.personId,
            url: 'https://storage.example.test/claim',
          }),
        );
      });

    await request(app.getHttpServer())
      .post('/claims')
      .set('Authorization', `Bearer ${claimantToken}`)
      .send({ spaceId, personId: person.body.personId })
      .expect(201)
      .expect(({ body }) => {
        expect(body.claimId).toBe(claimId);
        expect(body.status).toBe('PENDING');
      });

    await request(app.getHttpServer())
      .post('/claims/verify')
      .set('Authorization', `Bearer ${claimantToken}`)
      .send({ claimId })
      .expect(403);

    await request(app.getHttpServer())
      .post('/claims/verify')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({ claimId })
      .expect(201)
      .expect(({ body }) => {
        expect(body.status).toBe('PENDING');
        expect(body.confirmationCount).toBe(1);
        expect(body.required).toBe(2);
        expect(body.confirmationRecorded).toBe(true);
      });

    await request(app.getHttpServer())
      .post('/claims/verify')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({ claimId })
      .expect(201)
      .expect(({ body }) => {
        expect(body.status).toBe('PENDING');
        expect(body.confirmationCount).toBe(1);
        expect(body.required).toBe(2);
        expect(body.confirmationRecorded).toBe(false);
      });

    await request(app.getHttpServer())
      .get('/claims')
      .set('Authorization', `Bearer ${ownerToken}`)
      .query({ spaceId })
      .expect(200)
      .expect(({ body }) =>
        expect(body).toEqual([
          expect.objectContaining({
            claimId,
            status: 'PENDING',
            confirmationCount: 1,
            required: 2,
          }),
        ]),
      );

    await request(app.getHttpServer())
      .post('/claims/verify')
      .set('Authorization', `Bearer ${reviewerToken}`)
      .send({ claimId })
      .expect(201)
      .expect(({ body }) => {
        expect(body.status).toBe('VERIFIED');
        expect(body.confirmationCount).toBe(2);
        expect(body.required).toBe(2);
        expect(body.confirmationRecorded).toBe(true);
      });

    const members = await request(app.getHttpServer())
      .get(`/spaces/${spaceId}/members`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .expect(200);
    const claimantMember = (
      members.body as Array<{
        memberId: string;
        displayName: string;
      }>
    ).find((member) => member.displayName === 'Claim claimant');
    expect(claimantMember).toBeDefined();
    await request(app.getHttpServer())
      .patch(`/spaces/${spaceId}/members/${claimantMember?.memberId}`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({ role: 'VIEWER' })
      .expect(200);

    const otherPerson = await request(app.getHttpServer())
      .post('/persons')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        spaceId,
        firstName: 'Profil Lain',
        nickName: 'Lain',
        gender: 'UNKNOWN',
      })
      .expect(201);
    await request(app.getHttpServer())
      .post(`/persons/${otherPerson.body.personId}/media/upload`)
      .set('Authorization', `Bearer ${claimantToken}`)
      .query({ spaceId })
      .field('label', 'Bukan foto saya')
      .attach('file', onePixelPng, {
        filename: 'other.png',
        contentType: 'image/png',
      })
      .expect(403);

    await request(app.getHttpServer())
      .post(`/persons/${person.body.personId}/media/upload`)
      .set('Authorization', `Bearer ${claimantToken}`)
      .query({ spaceId })
      .field('label', 'Foto profil saya')
      .attach('file', onePixelPng, {
        filename: 'self.png',
        contentType: 'image/png',
      })
      .expect(201)
      .expect(({ body }) => {
        expect(body.personId).toBe(person.body.personId);
        expect(body.url).toBe('https://storage.example.test/claim');
      });

    await request(app.getHttpServer())
      .get('/changes/full')
      .set('Authorization', `Bearer ${ownerToken}`)
      .query({ spaceId, limit: 20 })
      .expect(200)
      .expect(({ body }) => {
        const confirmations = (
          body.items as Array<{
            entityType: string;
            operation: string;
            note: string;
          }>
        ).filter(
          (change) =>
            change.entityType === 'CLAIM' &&
            change.operation === 'VERIFY' &&
            change.note.startsWith('Confirm claim'),
        );
        expect(confirmations).toHaveLength(2);
        expect(confirmations.map((change) => change.note)).toEqual(
          expect.arrayContaining([
            'Confirm claim (1/2)',
            'Confirm claim (2/2)',
          ]),
        );
      });
  });
});
