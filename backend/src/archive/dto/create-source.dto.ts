import { IsIn, IsOptional, IsString, IsUUID, MaxLength } from 'class-validator';

export class CreateSourceDto {
  @IsUUID()
  spaceId!: string;

  @IsString()
  @MaxLength(120)
  title!: string;

  @IsIn(['DOCUMENT', 'STORY', 'PHOTO', 'OTHER'])
  type!: 'DOCUMENT' | 'STORY' | 'PHOTO' | 'OTHER';

  @IsOptional()
  @IsString()
  @MaxLength(500)
  url?: string | null;

  @IsOptional()
  @IsString()
  @MaxLength(1000)
  note?: string | null;
}
