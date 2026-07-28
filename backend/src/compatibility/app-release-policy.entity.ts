import { Column, Entity, PrimaryColumn, UpdateDateColumn } from 'typeorm';

export type ReleaseChannel = 'DEBUG' | 'PILOT' | 'PRODUCTION';

@Entity('app_release_policies')
export class AppReleasePolicyEntity {
  @PrimaryColumn({ type: 'text' })
  channel!: ReleaseChannel;

  @Column({ type: 'int' })
  minimumSupportedVersionCode!: number;

  @Column({ type: 'int' })
  latestVersionCode!: number;

  @Column({ type: 'int' })
  apiContractVersion!: number;

  @Column({ type: 'boolean', default: false })
  enforcementEnabled!: boolean;

  @Column({ type: 'text', nullable: true })
  updateUrl!: string | null;

  @Column({ type: 'text', nullable: true })
  message!: string | null;

  @Column({ type: 'uuid', nullable: true })
  updatedByUserId!: string | null;

  @UpdateDateColumn()
  updatedAt!: Date;
}
