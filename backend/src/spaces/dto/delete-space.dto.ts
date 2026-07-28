import { Equals, IsString, MaxLength, MinLength } from 'class-validator';

export class DeleteSpaceDto {
  @IsString()
  @MinLength(1)
  @MaxLength(160)
  confirmation!: string;

  @Equals(true)
  acknowledgeExport!: true;
}
