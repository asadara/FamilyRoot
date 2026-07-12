import {
  IsIn,
  IsNotEmpty,
  IsOptional,
  IsString,
  IsUUID,
  Matches,
} from 'class-validator';

export class CreatePersonDto {
  @IsUUID()
  spaceId!: string;

  @IsOptional()
  @IsString()
  title?: string | null;

  @IsNotEmpty()
  firstName!: string;

  @IsOptional()
  @IsString()
  lastName?: string | null;

  @IsOptional()
  @IsString()
  suffix?: string | null;

  @IsNotEmpty()
  nickName!: string;

  @IsNotEmpty()
  @IsIn(['MALE', 'FEMALE', 'UNKNOWN'])
  gender!: 'MALE' | 'FEMALE' | 'UNKNOWN';

  @IsOptional()
  @Matches(/^\d{4}-\d{2}-\d{2}$/)
  birthDate?: string | null;

  @IsOptional()
  @IsString()
  birthPlace?: string | null;

  @IsOptional()
  @Matches(/^\d{4}-\d{2}-\d{2}$/)
  deathDate?: string | null;

  @IsOptional()
  @IsString()
  deathPlace?: string | null;

  @IsOptional()
  @IsString()
  idNumber?: string | null;

  @IsOptional()
  @IsIn(['ALIVE', 'DECEASED', 'UNKNOWN'])
  lifeStatus?: 'ALIVE' | 'DECEASED' | 'UNKNOWN';
}
