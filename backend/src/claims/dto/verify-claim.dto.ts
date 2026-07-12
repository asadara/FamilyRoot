import { IsUUID } from 'class-validator';

export class VerifyClaimDto {
  @IsUUID()
  claimId!: string;
}
