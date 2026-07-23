import { IsIn, IsOptional, IsUUID, Matches } from 'class-validator';

export class CreateSpouseDto {
  @IsUUID()
  spaceId!: string;

  @IsUUID()
  personAId!: string;

  @IsUUID()
  personBId!: string;

  @IsIn(['MARRIED', 'DIVORCED', 'WIDOWED'])
  meta!: 'MARRIED' | 'DIVORCED' | 'WIDOWED';

  @IsOptional()
  @Matches(/^\d{4}-\d{2}-\d{2}$/)
  startDate?: string | null;

  @IsOptional()
  @Matches(/^\d{4}-\d{2}-\d{2}$/)
  endDate?: string | null;

  @IsUUID()
  clientMutationId!: string;
}
