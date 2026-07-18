import { IsNotEmpty, IsString, IsUUID, MaxLength } from 'class-validator';

export class ImportGedcomDto {
  @IsUUID()
  spaceId!: string;

  @IsString()
  @IsNotEmpty()
  @MaxLength(5_000_000)
  content!: string;
}
