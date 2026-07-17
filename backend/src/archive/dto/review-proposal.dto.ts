import { IsUUID } from 'class-validator';

export class ReviewProposalDto {
  @IsUUID()
  spaceId!: string;

  @IsUUID()
  proposalId!: string;
}
