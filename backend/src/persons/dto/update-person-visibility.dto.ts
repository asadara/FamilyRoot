import { IsIn, IsInt, IsUUID, Min } from 'class-validator';

export class UpdatePersonVisibilityDto {
  @IsUUID()
  spaceId!: string;

  @IsIn(['FAMILY', 'LIMITED', 'PRIVATE'])
  visibility!: 'FAMILY' | 'LIMITED' | 'PRIVATE';

  @IsInt()
  @Min(1)
  expectedVersion!: number;
}
