import { IsNotEmpty, IsString, MaxLength } from 'class-validator';

export class CreateSpaceDto {
  @IsString()
  @IsNotEmpty()
  @MaxLength(120)
  name!: string;
}
