import { IsNotEmpty, IsString, IsUUID, MaxLength } from 'class-validator';

export class RequestPersonDeletionDto {
  @IsUUID()
  spaceId!: string;

  @IsString()
  @IsNotEmpty()
  @MaxLength(1000)
  reason!: string;
}
