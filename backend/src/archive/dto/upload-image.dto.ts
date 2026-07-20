import { IsOptional, IsString, IsUUID, MaxLength } from 'class-validator';

export class UploadImageDto {
  @IsString()
  @MaxLength(120)
  label!: string;

  @IsOptional()
  @IsUUID()
  sourceId?: string;
}
