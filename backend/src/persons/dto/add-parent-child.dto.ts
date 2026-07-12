import { IsIn, IsUUID } from 'class-validator';

export class AddParentChildDto {
  @IsUUID()
  spaceId!: string;

  @IsUUID()
  parentId!: string;

  @IsUUID()
  childId!: string;

  @IsIn(['BIOLOGICAL', 'ADOPTIVE', 'STEP'])
  meta!: 'BIOLOGICAL' | 'ADOPTIVE' | 'STEP';
}
