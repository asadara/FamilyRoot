/* eslint-disable @typescript-eslint/no-unsafe-assignment, @typescript-eslint/no-unsafe-member-access */
import { INestApplication, ValidationPipe } from '@nestjs/common';
import { Test, TestingModule } from '@nestjs/testing';
import request from 'supertest';
import { App } from 'supertest/types';
import { AppModule } from '../src/app.module';
import { OBJECT_STORAGE } from '../src/archive/storage/object-storage';

type Role = 'ADMIN' | 'EDITOR' | 'VIEWER';

describe('Person privacy pilot (e2e)', () => {
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
          Promise.resolve('https://storage.example.test/private'),
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
        email: `privacy-${label}@example.test`,
        displayName: `Privacy ${label}`,
        password: `privacy-${label}-password`,
      })
      .expect(201);
    return response.body.accessToken as string;
  }

  async function join(
    ownerToken: string,
    memberToken: string,
    spaceId: string,
    role: Role,
  ) {
    const invitation = await request(app.getHttpServer())
      .post('/spaces/invitations')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({ spaceId, role, expiresInDays: 1 })
      .expect(201);
    await request(app.getHttpServer())
      .post('/spaces/invitations/accept')
      .set('Authorization', `Bearer ${memberToken}`)
      .send({ token: invitation.body.token })
      .expect(201);
  }

  it('redacts server responses and gives verified claimants final privacy control', async () => {
    const ownerToken = await register('owner');
    const reviewerToken = await register('reviewer');
    const claimantToken = await register('claimant');
    const viewerToken = await register('viewer');
    const editorToken = await register('editor');

    const space = await request(app.getHttpServer())
      .post('/spaces')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({ name: 'Privacy Family' })
      .expect(201);
    const spaceId = space.body.spaceId as string;
    await join(ownerToken, reviewerToken, spaceId, 'ADMIN');
    await join(ownerToken, claimantToken, spaceId, 'VIEWER');
    await join(ownerToken, viewerToken, spaceId, 'VIEWER');
    await join(ownerToken, editorToken, spaceId, 'EDITOR');

    const person = await request(app.getHttpServer())
      .post('/persons')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        spaceId,
        firstName: 'Rahasia',
        nickName: 'Rara',
        gender: 'FEMALE',
        birthDate: '2015-02-03',
        birthPlace: 'Bandung',
        lifeStatus: 'ALIVE',
      })
      .expect(201);
    const personId = person.body.personId as string;
    expect(person.body.visibility).toBe('LIMITED');

    const editorPerson = await request(app.getHttpServer())
      .post('/persons')
      .set('Authorization', `Bearer ${editorToken}`)
      .send({
        spaceId,
        firstName: 'Kontributor',
        nickName: 'Editor',
        gender: 'UNKNOWN',
        birthPlace: 'Surabaya',
        lifeStatus: 'ALIVE',
        clientMutationId: '11111111-1111-4111-8111-111111111111',
      })
      .expect(201);
    expect(editorPerson.body).toEqual(
      expect.objectContaining({
        visibility: 'LIMITED',
        privacyAccess: 'FULL',
      }),
    );
    const editedPerson = await request(app.getHttpServer())
      .patch(`/persons/${editorPerson.body.personId as string}/profile`)
      .set('Authorization', `Bearer ${editorToken}`)
      .send({
        spaceId,
        fullName: 'Kontributor Diperbarui',
        nickName: 'Editor',
        birthPlace: 'Surabaya',
        expectedVersion: editorPerson.body.version,
        clientMutationId: '22222222-2222-4222-8222-222222222222',
      })
      .expect(200);
    expect(editedPerson.body.fullName).toBe('Kontributor Diperbarui');
    await request(app.getHttpServer())
      .post('/persons/parent-child')
      .set('Authorization', `Bearer ${editorToken}`)
      .send({
        spaceId,
        parentId: editorPerson.body.personId,
        childId: personId,
        meta: 'BIOLOGICAL',
        clientMutationId: '33333333-3333-4333-8333-333333333333',
      })
      .expect(201);

    const sourceMutationId = '55555555-5555-4555-8555-555555555555';
    const source = await request(app.getHttpServer())
      .post(`/persons/${personId}/sources`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        spaceId,
        clientMutationId: sourceMutationId,
        title: 'Arsip keluarga',
        type: 'STORY',
        note: 'Catatan privat',
      })
      .expect(201);
    await request(app.getHttpServer())
      .post(`/persons/${personId}/sources`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        spaceId,
        clientMutationId: sourceMutationId,
        title: 'Arsip keluarga',
        type: 'STORY',
        note: 'Catatan privat',
      })
      .expect(201)
      .expect(({ body }) => expect(body.sourceId).toBe(source.body.sourceId));
    await request(app.getHttpServer())
      .post(`/persons/${personId}/sources`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        spaceId,
        clientMutationId: sourceMutationId,
        title: 'Sumber berbeda',
        type: 'STORY',
        note: 'Catatan privat',
      })
      .expect(409);
    await request(app.getHttpServer())
      .post(`/persons/${personId}/media`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        spaceId,
        label: 'Foto privat',
        kind: 'PHOTO',
        uri: 'https://media.example.test/private.jpg',
      })
      .expect(201);

    await request(app.getHttpServer())
      .get('/persons')
      .set('Authorization', `Bearer ${viewerToken}`)
      .query({ spaceId })
      .expect(200)
      .expect(({ body }) => {
        const people = body as Array<{ personId: string }>;
        const limited = people.find((item) => item.personId === personId);
        expect(limited).toEqual(
          expect.objectContaining({
            fullName: 'Rahasia',
            nickName: 'Rara',
            birthDate: null,
            birthPlace: null,
            gender: null,
            visibility: 'LIMITED',
            privacyAccess: 'STRUCTURE',
            canManageVisibility: false,
          }),
        );
      });
    await request(app.getHttpServer())
      .get(`/persons/${personId}`)
      .set('Authorization', `Bearer ${viewerToken}`)
      .query({ spaceId })
      .expect(200)
      .expect(({ body }) => {
        expect(body.birthDate).toBeNull();
        expect(body.notes).toBeNull();
      });
    await request(app.getHttpServer())
      .get(`/persons/${personId}/sources`)
      .set('Authorization', `Bearer ${viewerToken}`)
      .query({ spaceId })
      .expect(200)
      .expect([]);
    await request(app.getHttpServer())
      .get(`/persons/${personId}/media`)
      .set('Authorization', `Bearer ${viewerToken}`)
      .query({ spaceId })
      .expect(200)
      .expect([]);

    const ownerView = await request(app.getHttpServer())
      .get(`/persons/${personId}`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .query({ spaceId })
      .expect(200);
    expect(ownerView.body).toEqual(
      expect.objectContaining({
        birthDate: '2015-02-03',
        privacyAccess: 'FULL',
        canManageVisibility: true,
      }),
    );
    const privateProposal = await request(app.getHttpServer())
      .post('/proposals')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        spaceId,
        personId,
        field: 'birthPlace',
        proposedValue: 'Jakarta',
        reason: 'Usulan sebelum person diklaim',
      })
      .expect(201);
    const privateComment = await request(app.getHttpServer())
      .post(`/proposals/${privateProposal.body.proposalId as string}/comments`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        spaceId,
        body: 'Konteks privat sebelum claim diverifikasi',
      })
      .expect(201);
    const familyVisibility = await request(app.getHttpServer())
      .patch(`/persons/${personId}/visibility`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        spaceId,
        visibility: 'FAMILY',
        expectedVersion: ownerView.body.version,
      })
      .expect(200);
    expect(familyVisibility.body.visibility).toBe('FAMILY');

    const claim = await request(app.getHttpServer())
      .post('/claims')
      .set('Authorization', `Bearer ${claimantToken}`)
      .send({ spaceId, personId })
      .expect(201);
    await request(app.getHttpServer())
      .post('/claims/verify')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({ claimId: claim.body.claimId })
      .expect(201);
    await request(app.getHttpServer())
      .post('/claims/verify')
      .set('Authorization', `Bearer ${reviewerToken}`)
      .send({ claimId: claim.body.claimId })
      .expect(201)
      .expect(({ body }) => expect(body.status).toBe('VERIFIED'));

    await request(app.getHttpServer())
      .patch(`/persons/${personId}/visibility`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        spaceId,
        visibility: 'PRIVATE',
        expectedVersion: familyVisibility.body.version,
      })
      .expect(403);

    const privateVisibility = await request(app.getHttpServer())
      .patch(`/persons/${personId}/visibility`)
      .set('Authorization', `Bearer ${claimantToken}`)
      .send({
        spaceId,
        visibility: 'PRIVATE',
        expectedVersion: familyVisibility.body.version,
      })
      .expect(200);
    expect(privateVisibility.body).toEqual(
      expect.objectContaining({
        visibility: 'PRIVATE',
        privacyAccess: 'FULL',
        canManageVisibility: true,
      }),
    );

    await request(app.getHttpServer())
      .get(`/persons/${personId}`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .query({ spaceId })
      .expect(200)
      .expect(({ body }) =>
        expect(body).toEqual(
          expect.objectContaining({
            fullName: 'Anggota keluarga',
            nickName: null,
            birthDate: null,
            visibility: 'PRIVATE',
            privacyAccess: 'MINIMUM',
            canManageVisibility: false,
          }),
        ),
      );
    await request(app.getHttpServer())
      .get(`/persons/${personId}/media`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .query({ spaceId })
      .expect(200)
      .expect([]);
    await request(app.getHttpServer())
      .get(`/persons/${personId}/media`)
      .set('Authorization', `Bearer ${claimantToken}`)
      .query({ spaceId })
      .expect(200)
      .expect(({ body }) => expect(body).toHaveLength(1));

    await request(app.getHttpServer())
      .patch(`/persons/${personId}/profile`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        spaceId,
        fullName: 'Tidak boleh terbaca',
        nickName: 'Ditolak',
        expectedVersion: privateVisibility.body.version,
        clientMutationId: '44444444-4444-4444-8444-444444444444',
      })
      .expect(403);

    await request(app.getHttpServer())
      .get('/relationships/path')
      .set('Authorization', `Bearer ${ownerToken}`)
      .query({
        spaceId,
        fromPersonId: editorPerson.body.personId,
        toPersonId: personId,
      })
      .expect(200)
      .expect(({ body }) => {
        expect(body.people).toEqual(
          expect.arrayContaining([
            expect.objectContaining({
              personId,
              fullName: 'Anggota keluarga',
            }),
          ]),
        );
      });

    await request(app.getHttpServer())
      .get('/claims')
      .set('Authorization', `Bearer ${ownerToken}`)
      .query({ spaceId })
      .expect(200)
      .expect(({ body }) =>
        expect(body).toEqual(
          expect.arrayContaining([
            expect.objectContaining({
              claimId: claim.body.claimId,
              personName: 'Anggota keluarga',
            }),
          ]),
        ),
      );

    await request(app.getHttpServer())
      .get('/export/space')
      .set('Authorization', `Bearer ${ownerToken}`)
      .query({ spaceId })
      .expect(200)
      .expect(({ body }) => {
        const exportedPerson = (
          body.persons as Array<{ personId: string; fullName: string }>
        ).find((item) => item.personId === personId);
        expect(exportedPerson?.fullName).toBe('Anggota keluarga');
        expect(
          (body.media as Array<{ personId: string }>).some(
            (item) => item.personId === personId,
          ),
        ).toBe(false);
        expect(
          (body.sources as Array<{ personId: string }>).some(
            (item) => item.personId === personId,
          ),
        ).toBe(false);
        expect(
          (body.proposals as Array<{ proposalId: string }>).some(
            (item) => item.proposalId === privateProposal.body.proposalId,
          ),
        ).toBe(false);
        expect(
          (body.proposalComments as Array<{ commentId: string }>).some(
            (item) => item.commentId === privateComment.body.commentId,
          ),
        ).toBe(false);
      });
    await request(app.getHttpServer())
      .get('/export/space/gedcom')
      .set('Authorization', `Bearer ${ownerToken}`)
      .query({ spaceId })
      .expect(200)
      .expect(({ body }) => {
        expect(body.content).not.toContain('Rahasia');
        expect(body.content).not.toContain('Bandung');
      });

    await request(app.getHttpServer())
      .get('/changes')
      .set('Authorization', `Bearer ${viewerToken}`)
      .query({ spaceId })
      .expect(200)
      .expect(({ body }) => {
        expect(
          (body as Array<Record<string, unknown>>).every(
            (change) => !('beforeJson' in change) && !('afterJson' in change),
          ),
        ).toBe(true);
      });
    await request(app.getHttpServer())
      .get('/proposals')
      .set('Authorization', `Bearer ${ownerToken}`)
      .query({ spaceId })
      .expect(200)
      .expect(({ body }) => {
        expect(body).not.toEqual(
          expect.arrayContaining([
            expect.objectContaining({
              proposalId: privateProposal.body.proposalId,
            }),
          ]),
        );
      });
    await request(app.getHttpServer())
      .get(`/proposals/${privateProposal.body.proposalId as string}/comments`)
      .set('Authorization', `Bearer ${ownerToken}`)
      .query({ spaceId })
      .expect(403);
    await request(app.getHttpServer())
      .get('/proposals')
      .set('Authorization', `Bearer ${claimantToken}`)
      .query({ spaceId })
      .expect(200)
      .expect(({ body }) => {
        expect(body).toEqual(
          expect.arrayContaining([
            expect.objectContaining({
              proposalId: privateProposal.body.proposalId,
              currentValue: 'Bandung',
              proposedValue: 'Jakarta',
            }),
          ]),
        );
      });
    await request(app.getHttpServer())
      .get(`/proposals/${privateProposal.body.proposalId as string}/comments`)
      .set('Authorization', `Bearer ${claimantToken}`)
      .query({ spaceId })
      .expect(200)
      .expect(({ body }) => {
        expect(body).toEqual(
          expect.arrayContaining([
            expect.objectContaining({
              commentId: privateComment.body.commentId,
              body: 'Konteks privat sebelum claim diverifikasi',
            }),
          ]),
        );
      });
    await request(app.getHttpServer())
      .post('/proposals/approve')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        spaceId,
        proposalId: privateProposal.body.proposalId,
      })
      .expect(403);

    const deceased = await request(app.getHttpServer())
      .post('/persons')
      .set('Authorization', `Bearer ${ownerToken}`)
      .send({
        spaceId,
        firstName: 'Leluhur',
        nickName: 'Leluhur',
        gender: 'UNKNOWN',
        birthDate: '1940-01-01',
        deathDate: '2000-01-01',
        lifeStatus: 'DECEASED',
      })
      .expect(201);
    expect(deceased.body.visibility).toBe('FAMILY');
    await request(app.getHttpServer())
      .get(`/persons/${deceased.body.personId as string}`)
      .set('Authorization', `Bearer ${viewerToken}`)
      .query({ spaceId })
      .expect(200)
      .expect(({ body }) => {
        expect(body.birthDate).toBe('1940-01-01');
        expect(body.privacyAccess).toBe('FULL');
      });
  });
});
