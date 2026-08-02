import { IsBoolean, IsUUID } from 'class-validator';

export class ReviewHistoryAccessDto {
  @IsUUID()
  spaceId!: string;

  @IsBoolean()
  approved!: boolean;
}
