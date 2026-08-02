import { IsUUID } from 'class-validator';

export class RequestHistoryAccessDto {
  @IsUUID()
  spaceId!: string;
}
