export const DEMO_PASSWORD = 'Test123456!';
export const DEMO_SPACE_NAME = 'Keluarga Demo';

export type DemoPersonSeed = {
  fullName: string;
  firstName: string;
  nickName: string;
  gender: string;
  lifeStatus: 'ALIVE' | 'DECEASED';
  birthDate: string;
};

export const demoUsers = [
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
      birthDate: '2001-02-03',
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

export const demoRelatives: Array<{
  key: string;
  person: DemoPersonSeed;
}> = [
  {
    key: 'nenek_maternal',
    person: {
      fullName: 'Nur Aisyah',
      firstName: 'Nur',
      nickName: 'Ibu Siti',
      gender: 'FEMALE',
      lifeStatus: 'ALIVE',
      birthDate: '1962-06-15',
    },
  },
  {
    key: 'istri_anak',
    person: {
      fullName: 'Alya Putri',
      firstName: 'Alya',
      nickName: 'Istri Raka',
      gender: 'FEMALE',
      lifeStatus: 'ALIVE',
      birthDate: '2002-09-18',
    },
  },
];
