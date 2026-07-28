import {
  IsIn,
  IsBoolean,
  IsInt,
  IsOptional,
  IsString,
  IsUrl,
  MaxLength,
  Min,
} from 'class-validator';
import type { ReleaseChannel } from '../app-release-policy.entity';

export class UpdateAppReleasePolicyDto {
  @IsIn(['DEBUG', 'PILOT', 'PRODUCTION'])
  channel!: ReleaseChannel;

  @IsInt()
  @Min(1)
  minimumSupportedVersionCode!: number;

  @IsInt()
  @Min(1)
  latestVersionCode!: number;

  @IsInt()
  @Min(1)
  apiContractVersion!: number;

  @IsBoolean()
  enforcementEnabled!: boolean;

  @IsOptional()
  @IsUrl({ protocols: ['https'], require_protocol: true })
  updateUrl?: string | null;

  @IsOptional()
  @IsString()
  @MaxLength(500)
  message?: string | null;
}
