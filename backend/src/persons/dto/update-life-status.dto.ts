import { IsIn, IsInt, IsOptional, Matches, IsUUID, Min } from 'class-validator';

export class UpdateLifeStatusDto {
  @IsUUID()
  spaceId!: string;

  @IsIn(['ALIVE', 'DECEASED', 'UNKNOWN'])
  lifeStatus!: 'ALIVE' | 'DECEASED' | 'UNKNOWN';

  @IsOptional()
  @Matches(/^\d{4}-\d{2}-\d{2}$/)
  deceasedAt?: string | null;

  @IsInt()
  @Min(1)
  expectedVersion!: number;

  @IsUUID()
  clientMutationId!: string;
}
