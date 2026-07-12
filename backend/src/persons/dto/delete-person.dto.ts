import { IsUUID } from 'class-validator';

export class DeletePersonDto {
  @IsUUID()
  spaceId!: string;
}
