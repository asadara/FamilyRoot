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

    await request(app.getHttpServer())
      .get('/changes')
      .set('Authorization', `Bearer ${ownerToken}`)
      .query({ spaceId, limit: 20 })
      .expect(200)
      .expect(({ body }) => {
        const confirmations = (
          body as Array<{
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
