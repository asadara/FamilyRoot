import { IsIn, IsOptional, IsString, IsUUID, MaxLength } from 'class-validator';

export class CreateMediaDto {
  @IsUUID()
  spaceId!: string;

  @IsString()
  @MaxLength(120)
  label!: string;

  @IsIn(['PHOTO', 'DOCUMENT', 'AUDIO', 'OTHER'])
  kind!: 'PHOTO' | 'DOCUMENT' | 'AUDIO' | 'OTHER';

  @IsString()
  @MaxLength(1000)
  uri!: string;

  @IsOptional()
  @IsUUID()
  sourceId?: string | null;
}
