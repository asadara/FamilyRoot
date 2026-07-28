import {
  IsIn,
  IsOptional,
  IsString,
  IsUUID,
  Matches,
  MaxLength,
} from 'class-validator';

export class AddParentChildDto {
  @IsUUID()
  spaceId!: string;

  @IsUUID()
  parentId!: string;

  @IsUUID()
  childId!: string;

  @IsIn(['BIOLOGICAL', 'ADOPTIVE', 'STEP', 'FOSTER', 'GUARDIAN'])
  meta!: 'BIOLOGICAL' | 'ADOPTIVE' | 'STEP' | 'FOSTER' | 'GUARDIAN';

  @IsOptional()
  @Matches(/^\d{4}-\d{2}-\d{2}$/)
  startDate?: string | null;

  @IsOptional()
  @Matches(/^\d{4}-\d{2}-\d{2}$/)
  endDate?: string | null;

  @IsOptional()
  @IsString()
  @MaxLength(1000)
  careContext?: string | null;

  @IsUUID()
  clientMutationId!: string;
}
