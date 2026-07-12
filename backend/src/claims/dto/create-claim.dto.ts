import { IsUUID } from 'class-validator';

export class CreateClaimDto {
  @IsUUID()
  spaceId!: string;

  @IsUUID()
  personId!: string;
}
