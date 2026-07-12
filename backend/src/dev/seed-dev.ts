import 'reflect-metadata';
import { DataSource, Repository } from 'typeorm';
import * as bcrypt from 'bcryptjs';
import { UserEntity } from '../users/user.entity';
import { FamilySpaceEntity } from '../spaces/family-space.entity';
import { SpaceMemberEntity } from '../spaces/space-member.entity';
import { PersonEntity } from '../persons/person.entity';
import { RelationshipEntity } from '../persons/relationship.entity';
import { UserPersonClaimEntity } from '../claims/user-person-claim.entity';
import { ChangeLogEntity } from '../changes/change-log.entity';
import { SpaceInvitationEntity } from '../spaces/space-invitation.entity';

const DEMO_PASSWORD = 'Test123456!';
const DEMO_SPACE_NAME = 'Keluarga Demo';

const demoUsers = [
  {
    key: 'ayah',
    email: 'ayah@example.test',
    displayName: 'Budi Santoso',
    role: 'OWNER' as const,
    person: {
      fullName: 'Budi Santoso',
      firstName: 'Budi',
      nickName: 'Ayah',
      gender: 'MALE',
      lifeStatus: 'ALIVE' as const,
      birthDate: '1985-04-12',
    },
  },
  {
    key: 'ibu',
    email: 'ibu@example.test',
    displayName: 'Siti Aminah',
    role: 'ADMIN' as const,
    person: {
      fullName: 'Siti Aminah',
      firstName: 'Siti',
      nickName: 'Ibu',
      gender: 'FEMALE',
      lifeStatus: 'ALIVE' as const,
      birthDate: '1987-08-21',
    },
  },
  {
    key: 'anak',
    email: 'anak@example.test',
    displayName: 'Raka Santoso',
    role: 'EDITOR' as const,
    person: {
      fullName: 'Raka Santoso',
      firstName: 'Raka',
      nickName: 'Anak Pertama',
      gender: 'MALE',
      lifeStatus: 'ALIVE' as const,
      birthDate: '2012-02-03',
    },
  },
  {
    key: 'kakek',
    email: 'kakek@example.test',
    displayName: 'Hadi Santoso',
    role: 'VIEWER' as const,
    person: {
      fullName: 'Hadi Santoso',
      firstName: 'Hadi',
      nickName: 'Kakek',
      gender: 'MALE',
      lifeStatus: 'ALIVE' as const,
      birthDate: '1958-11-10',
    },
  },
];

const dataSource = new DataSource({
  type: 'sqlite',
  database: process.env.DB_DATABASE ?? 'dev.sqlite',
  entities: [
    UserEntity,
    FamilySpaceEntity,
    SpaceMemberEntity,
    PersonEntity,
    RelationshipEntity,
    UserPersonClaimEntity,
    ChangeLogEntity,
    SpaceInvitationEntity,
  ],
  synchronize: process.env.NODE_ENV !== 'production',
});

async function findOrCreateUser(
  usersRepo: Repository<UserEntity>,
  email: string,
  displayName: string,
  passwordHash: string,
) {
  const existing = await usersRepo.findOne({ where: { email } });
  if (existing) {
    existing.displayName = displayName;
    existing.passwordHash = passwordHash;
    return usersRepo.save(existing);
  }
  return usersRepo.save(
    usersRepo.create({ email, phone: null, displayName, passwordHash }),
  );
}

async function findOrCreatePerson(
  personsRepo: Repository<PersonEntity>,
  spaceId: string,
  person: (typeof demoUsers)[number]['person'],
) {
  const existing = await personsRepo.findOne({
    where: { spaceId, fullName: person.fullName, isDeleted: false },
  });
  const entity = existing ?? personsRepo.create({ spaceId });
  Object.assign(entity, {
    ...person,
    title: null,
    lastName: null,
    suffix: null,
    birthPlace: null,
    deathDate: null,
    deathPlace: null,
    idNumber: null,
    notes: 'Demo seed profile',
    deceasedAt: null,
  });
  return personsRepo.save(entity);
}

async function ensureRelationship(
  relationshipsRepo: Repository<RelationshipEntity>,
  input: Pick<
    RelationshipEntity,
    'spaceId' | 'type' | 'fromPersonId' | 'toPersonId'
  > & { meta: Exclude<RelationshipEntity['meta'], null> },
) {
  const existing = await relationshipsRepo.findOne({ where: input });
  if (existing) return existing;
  return relationshipsRepo.save(
    relationshipsRepo.create({
      ...input,
      startDate: null,
      endDate: null,
    }),
  );
}

async function main() {
  if (process.env.NODE_ENV === 'production') {
    throw new Error('Refusing to run development seed in production');
  }

  await dataSource.initialize();
  try {
    const usersRepo = dataSource.getRepository(UserEntity);
    const spacesRepo = dataSource.getRepository(FamilySpaceEntity);
    const membersRepo = dataSource.getRepository(SpaceMemberEntity);
    const personsRepo = dataSource.getRepository(PersonEntity);
    const relationshipsRepo = dataSource.getRepository(RelationshipEntity);
    const claimsRepo = dataSource.getRepository(UserPersonClaimEntity);
    const changesRepo = dataSource.getRepository(ChangeLogEntity);

    const passwordHash = await bcrypt.hash(DEMO_PASSWORD, 10);
    const usersByKey = new Map<string, UserEntity>();
    for (const demo of demoUsers) {
      usersByKey.set(
        demo.key,
        await findOrCreateUser(
          usersRepo,
          demo.email,
          demo.displayName,
          passwordHash,
        ),
      );
    }

    const owner = usersByKey.get('ayah');
    if (!owner) throw new Error('Demo owner was not created');

    let space = await spacesRepo.findOne({
      where: { name: DEMO_SPACE_NAME, createdBy: owner.userId },
    });
    if (!space) {
      space = await spacesRepo.save(
        spacesRepo.create({ name: DEMO_SPACE_NAME, createdBy: owner.userId }),
      );
      await changesRepo.save(
        changesRepo.create({
          spaceId: space.spaceId,
          actorUserId: owner.userId,
          entityType: 'SPACE',
          entityId: space.spaceId,
          operation: 'CREATE',
          note: 'Seed demo Family Space',
          afterJson: JSON.stringify({ name: space.name }),
        }),
      );
    }

    const personsByKey = new Map<string, PersonEntity>();
    for (const demo of demoUsers) {
      const user = usersByKey.get(demo.key);
      if (!user) throw new Error(`Missing demo user ${demo.key}`);
      const membership = await membersRepo.findOne({
        where: { spaceId: space.spaceId, userId: user.userId },
      });
      if (!membership) {
        await membersRepo.save(
          membersRepo.create({
            spaceId: space.spaceId,
            userId: user.userId,
            role: demo.role,
          }),
        );
      }

      const person = await findOrCreatePerson(
        personsRepo,
        space.spaceId,
        demo.person,
      );
      personsByKey.set(demo.key, person);

      const claim = await claimsRepo.findOne({
        where: {
          spaceId: space.spaceId,
          userId: user.userId,
          personId: person.personId,
        },
      });
      if (!claim) {
        await claimsRepo.save(
          claimsRepo.create({
            spaceId: space.spaceId,
            userId: user.userId,
            personId: person.personId,
            status: 'VERIFIED',
          }),
        );
      } else if (claim.status !== 'VERIFIED') {
        claim.status = 'VERIFIED';
        await claimsRepo.save(claim);
      }
    }

    const ayah = personsByKey.get('ayah');
    const ibu = personsByKey.get('ibu');
    const anak = personsByKey.get('anak');
    const kakek = personsByKey.get('kakek');
    if (!ayah || !ibu || !anak || !kakek) {
      throw new Error('Missing demo persons');
    }

    await ensureRelationship(relationshipsRepo, {
      spaceId: space.spaceId,
      type: 'PARENT_CHILD',
      fromPersonId: kakek.personId,
      toPersonId: ayah.personId,
      meta: 'BIOLOGICAL',
    });
    await ensureRelationship(relationshipsRepo, {
      spaceId: space.spaceId,
      type: 'PARENT_CHILD',
      fromPersonId: ayah.personId,
      toPersonId: anak.personId,
      meta: 'BIOLOGICAL',
    });
    await ensureRelationship(relationshipsRepo, {
      spaceId: space.spaceId,
      type: 'PARENT_CHILD',
      fromPersonId: ibu.personId,
      toPersonId: anak.personId,
      meta: 'BIOLOGICAL',
    });
    await ensureRelationship(relationshipsRepo, {
      spaceId: space.spaceId,
      type: 'SPOUSE',
      fromPersonId: ayah.personId,
      toPersonId: ibu.personId,
      meta: 'MARRIED',
    });

    console.log('Development seed ready.');
    console.table(
      demoUsers.map((item) => ({
        role: item.key,
        email: item.email,
        password: DEMO_PASSWORD,
        space: DEMO_SPACE_NAME,
        membership: item.role,
      })),
    );
  } finally {
    await dataSource.destroy();
  }
}

main().catch((error: unknown) => {
  console.error(error);
  process.exitCode = 1;
});
