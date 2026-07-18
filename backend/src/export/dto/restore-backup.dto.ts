import { IsObject, IsUUID } from 'class-validator';

export class RestoreBackupDto {
  @IsUUID()
  spaceId!: string;

  @IsObject()
  backup!: Record<string, unknown>;
}
