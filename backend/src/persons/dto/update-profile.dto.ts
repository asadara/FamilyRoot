import { IsInt, IsString, IsUUID, MaxLength, Min } from 'class-validator';

export class UpdateProfileDto {
  @IsUUID()
  spaceId!: string;

  @IsString()
  @MaxLength(200)
  birthPlace!: string;

  @IsString()
  @MaxLength(4000)
  notes!: string;

  @IsInt()
  @Min(1)
  expectedVersion!: number;

  @IsUUID()
  clientMutationId!: string;
}
