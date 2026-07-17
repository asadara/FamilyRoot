import { IsUUID } from 'class-validator';

export class MergePersonsDto {
  @IsUUID()
  spaceId!: string;

  @IsUUID()
  sourcePersonId!: string;

  @IsUUID()
  targetPersonId!: string;
}
