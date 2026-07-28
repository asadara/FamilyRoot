import { IsOptional, IsString, IsUUID, MaxLength } from 'class-validator';

export class ReviewProposalDto {
  @IsUUID()
  spaceId!: string;

  @IsUUID()
  proposalId!: string;

  @IsOptional()
  @IsString()
  @MaxLength(1000)
  reviewReason?: string;
}
