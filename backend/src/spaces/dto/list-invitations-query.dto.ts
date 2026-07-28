import { IsIn, IsOptional } from 'class-validator';

export const invitationStatuses = [
  'ACTIVE',
  'ACCEPTED',
  'REVOKED',
  'EXPIRED',
] as const;

export type InvitationStatus = (typeof invitationStatuses)[number];

export class ListInvitationsQueryDto {
  @IsOptional()
  @IsIn(invitationStatuses)
  status?: InvitationStatus;
}
