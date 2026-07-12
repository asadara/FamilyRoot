import { IsIn, IsInt, IsOptional, IsUUID, Max, Min } from 'class-validator';

export class CreateInvitationDto {
  @IsUUID()
  spaceId!: string;

  @IsIn(['ADMIN', 'EDITOR', 'VIEWER'])
  role!: 'ADMIN' | 'EDITOR' | 'VIEWER';

  @IsOptional()
  @IsInt()
  @Min(1)
  @Max(30)
  expiresInDays?: number;
}
