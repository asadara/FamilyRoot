import { BadRequestException, HttpException, Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import {
  AppReleasePolicyEntity,
  ReleaseChannel,
} from './app-release-policy.entity';
import { AppReleasePolicyAuditEntity } from './app-release-policy-audit.entity';
import { CheckAppCompatibilityDto } from './dto/check-app-compatibility.dto';
import { UpdateAppReleasePolicyDto } from './dto/update-app-release-policy.dto';

export type CompatibilityStatus =
  | 'COMPATIBLE'
  | 'UPDATE_AVAILABLE'
  | 'APP_TOO_OLD'
  | 'APP_TOO_NEW'
  | 'API_CONTRACT_MISMATCH';

@Injectable()
export class AppCompatibilityService {
  constructor(
    @InjectRepository(AppReleasePolicyEntity)
    private readonly policyRepo: Repository<AppReleasePolicyEntity>,
    @InjectRepository(AppReleasePolicyAuditEntity)
    private readonly auditRepo: Repository<AppReleasePolicyAuditEntity>,
  ) {}

  async check(input: CheckAppCompatibilityDto) {
    const policy = await this.getPolicy(input.channel);

    let status: CompatibilityStatus = 'COMPATIBLE';
    if (input.apiContractVersion !== policy.apiContractVersion) {
      status = 'API_CONTRACT_MISMATCH';
    } else if (input.versionCode < policy.minimumSupportedVersionCode) {
      status = 'APP_TOO_OLD';
    } else if (input.versionCode > policy.latestVersionCode) {
      status = 'APP_TOO_NEW';
    } else if (input.versionCode < policy.latestVersionCode) {
      status = 'UPDATE_AVAILABLE';
    }

    return {
      status,
      blocking: !['COMPATIBLE', 'UPDATE_AVAILABLE'].includes(status),
      message:
        status === 'UPDATE_AVAILABLE' || status === 'APP_TOO_OLD'
          ? (policy.message ?? this.defaultMessage(status))
          : this.defaultMessage(status),
      channel: policy.channel,
      minimumSupportedVersionCode: policy.minimumSupportedVersionCode,
      latestVersionCode: policy.latestVersionCode,
      backendApiContractVersion: policy.apiContractVersion,
      enforcementEnabled: policy.enforcementEnabled,
      updateUrl: policy.updateUrl,
      policyUpdatedAt: policy.updatedAt?.toISOString() ?? null,
      checkedAt: new Date().toISOString(),
    };
  }

  async enforceHeaders(input: {
    versionCode?: string;
    apiContractVersion?: string;
    channel?: string;
  }) {
    if (!input.versionCode || !input.apiContractVersion || !input.channel) {
      const enforcementEnabled =
        (await this.policyRepo.exists({
          where: { enforcementEnabled: true },
        })) ||
        ['DEBUG', 'PILOT', 'PRODUCTION'].some(
          (channel) =>
            process.env[
              `ANDROID_${channel}_ENFORCEMENT_ENABLED`
            ]?.toLowerCase() === 'true',
        );
      if (enforcementEnabled) {
        this.throwUpgradeRequired(
          'Aplikasi ini belum mengirimkan identitas versi yang diwajibkan.',
        );
      }
      return;
    }
    if (!['DEBUG', 'PILOT', 'PRODUCTION'].includes(input.channel)) {
      this.throwUpgradeRequired('Channel aplikasi tidak dikenali.');
    }
    const channel = input.channel as ReleaseChannel;
    const acceptedChannels = (
      process.env.ANDROID_ACCEPTED_RELEASE_CHANNELS ?? 'DEBUG,PILOT,PRODUCTION'
    )
      .split(',')
      .map((value) => value.trim())
      .filter(Boolean);
    if (!acceptedChannels.includes(channel)) {
      this.throwUpgradeRequired(
        'Channel aplikasi tidak diterima oleh deployment ini.',
      );
    }
    const policy = await this.getPolicy(channel);
    if (!policy.enforcementEnabled) return;
    const versionCode = Number(input.versionCode);
    const apiContractVersion = Number(input.apiContractVersion);
    if (
      !Number.isInteger(versionCode) ||
      versionCode < 1 ||
      !Number.isInteger(apiContractVersion) ||
      apiContractVersion < 1
    ) {
      this.throwUpgradeRequired('Identitas versi aplikasi tidak valid.');
    }
    const result = await this.check({
      versionCode,
      apiContractVersion,
      channel,
    });
    if (result.enforcementEnabled && result.blocking) {
      this.throwUpgradeRequired(result.message, result);
    }
  }

  async updatePolicy(input: UpdateAppReleasePolicyDto, actorUserId: string) {
    if (input.latestVersionCode < input.minimumSupportedVersionCode) {
      throw new BadRequestException(
        'latestVersionCode must be greater than or equal to minimumSupportedVersionCode',
      );
    }

    return this.policyRepo.manager.transaction(async (manager) => {
      const existing = await manager.findOne(AppReleasePolicyEntity, {
        where: { channel: input.channel },
      });
      const saved = await manager.save(
        manager.create(AppReleasePolicyEntity, {
          channel: input.channel,
          minimumSupportedVersionCode: input.minimumSupportedVersionCode,
          latestVersionCode: input.latestVersionCode,
          apiContractVersion: input.apiContractVersion,
          enforcementEnabled: input.enforcementEnabled,
          updateUrl: input.updateUrl?.trim() || null,
          message: input.message?.trim() || null,
          updatedByUserId: actorUserId,
        }),
      );
      await manager.save(
        manager.create(AppReleasePolicyAuditEntity, {
          channel: input.channel,
          actorUserId,
          beforeJson: existing ? JSON.stringify(existing) : null,
          afterJson: JSON.stringify(saved),
        }),
      );
      return saved;
    });
  }

  private environmentPolicy(channel: ReleaseChannel): AppReleasePolicyEntity {
    const prefix = `ANDROID_${channel}`;
    const minimumSupportedVersionCode = this.positiveInteger(
      process.env[`${prefix}_MIN_SUPPORTED_VERSION_CODE`],
      1,
    );
    const latestVersionCode = this.positiveInteger(
      process.env[`${prefix}_LATEST_VERSION_CODE`],
      minimumSupportedVersionCode,
    );
    if (latestVersionCode < minimumSupportedVersionCode) {
      throw new Error(
        `${prefix}_LATEST_VERSION_CODE must be greater than or equal to minimum supported version`,
      );
    }
    return this.policyRepo.create({
      channel,
      minimumSupportedVersionCode,
      latestVersionCode,
      apiContractVersion: this.positiveInteger(
        process.env.ANDROID_API_CONTRACT_VERSION,
        1,
      ),
      enforcementEnabled:
        process.env[`${prefix}_ENFORCEMENT_ENABLED`]?.toLowerCase() === 'true',
      updateUrl: process.env[`${prefix}_UPDATE_URL`]?.trim() || null,
      message: null,
      updatedByUserId: null,
    });
  }

  private async getPolicy(channel: ReleaseChannel) {
    return (
      (await this.policyRepo.findOneBy({ channel })) ??
      this.environmentPolicy(channel)
    );
  }

  private throwUpgradeRequired(message: string, details?: unknown): never {
    throw new HttpException(
      {
        message,
        details,
      },
      426,
    );
  }

  private positiveInteger(value: string | undefined, fallback: number) {
    const parsed = value ? Number(value) : fallback;
    if (!Number.isInteger(parsed) || parsed < 1) {
      throw new Error(
        'Android release policy values must be positive integers',
      );
    }
    return parsed;
  }

  private defaultMessage(status: CompatibilityStatus): string {
    switch (status) {
      case 'UPDATE_AVAILABLE':
        return 'Versi aplikasi yang lebih baru tersedia.';
      case 'APP_TOO_OLD':
        return 'Versi aplikasi ini sudah tidak didukung.';
      case 'APP_TOO_NEW':
        return 'Backend belum mendukung versi aplikasi ini.';
      case 'API_CONTRACT_MISMATCH':
        return 'Versi aplikasi dan layanan belum kompatibel.';
      default:
        return 'Versi aplikasi kompatibel.';
    }
  }
}
