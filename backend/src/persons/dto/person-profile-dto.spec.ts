import { plainToInstance } from 'class-transformer';
import { validate } from 'class-validator';
import { CreateSpouseDto } from './create-spouse.dto';
import { UpdateProfileDto } from './update-profile.dto';

describe('person profile DTOs', () => {
  it('accepts a spouse relationship without a known marriage date', async () => {
    const dto = plainToInstance(CreateSpouseDto, {
      spaceId: '11111111-1111-4111-8111-111111111111',
      personAId: '22222222-2222-4222-8222-222222222222',
      personBId: '33333333-3333-4333-8333-333333333333',
      meta: 'MARRIED',
      clientMutationId: '44444444-4444-4444-8444-444444444444',
    });

    expect(await validate(dto)).toHaveLength(0);
  });

  it('accepts Indonesian full-name profile fields and optional dates', async () => {
    const dto = plainToInstance(UpdateProfileDto, {
      spaceId: '11111111-1111-4111-8111-111111111111',
      fullName: 'Raden Ajeng Kartini',
      nickName: 'Kartini',
      gender: 'FEMALE',
      birthDate: '1879-04-21',
      birthPlace: 'Jepara',
      deathPlace: 'Rembang',
      notes: 'Tokoh keluarga',
      expectedVersion: 1,
      clientMutationId: '44444444-4444-4444-8444-444444444444',
    });

    expect(await validate(dto)).toHaveLength(0);
  });
});
