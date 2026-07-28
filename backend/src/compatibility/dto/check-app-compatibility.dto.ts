import { Transform } from 'class-transformer';
import { IsIn, IsInt, IsOptional, IsString, Min } from 'class-validator';
import type { ReleaseChannel } from '../app-release-policy.entity';

export class CheckAppCompatibilityDto {
  @Transform(({ value }) => Number(value))
  @IsInt()
  @Min(1)
  versionCode!: number;

  @IsOptional()
  @IsString()
  versionName?: string;

  @Transform(({ value }) => Number(value))
  @IsInt()
  @Min(1)
  apiContractVersion!: number;

  @IsIn(['DEBUG', 'PILOT', 'PRODUCTION'])
  channel!: ReleaseChannel;
}
