import {
  IsIn,
  IsInt,
  IsNotEmpty,
  IsOptional,
  IsString,
  IsUUID,
  Matches,
  MaxLength,
  Min,
} from 'class-validator';

export class UpdateProfileDto {
  @IsUUID()
  spaceId!: string;

  @IsOptional()
  @IsString()
  @IsNotEmpty()
  @MaxLength(200)
  fullName?: string;

  @IsOptional()
  @IsString()
  @MaxLength(120)
  nickName?: string;

  @IsOptional()
  @IsIn(['MALE', 'FEMALE', 'UNKNOWN'])
  gender?: 'MALE' | 'FEMALE' | 'UNKNOWN';

  @IsOptional()
  @Matches(/^$|^\d{4}-\d{2}-\d{2}$/)
  birthDate?: string;

  @IsOptional()
  @IsString()
  @MaxLength(200)
  birthPlace?: string;

  @IsOptional()
  @IsString()
  @MaxLength(200)
  deathPlace?: string;

  @IsOptional()
  @IsString()
  @MaxLength(4000)
  notes?: string;

  @IsInt()
  @Min(1)
  expectedVersion!: number;

  @IsUUID()
  clientMutationId!: string;
}
