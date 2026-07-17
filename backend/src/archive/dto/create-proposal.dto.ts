import { IsIn, IsOptional, IsString, IsUUID, MaxLength } from 'class-validator';

export class CreateProposalDto {
  @IsUUID()
  spaceId!: string;

  @IsUUID()
  personId!: string;

  @IsIn(['notes', 'birthPlace', 'deathPlace'])
  field!: 'notes' | 'birthPlace' | 'deathPlace';

  @IsString()
  @MaxLength(1000)
  proposedValue!: string;

  @IsOptional()
  @IsString()
  @MaxLength(1000)
  reason?: string | null;
}
