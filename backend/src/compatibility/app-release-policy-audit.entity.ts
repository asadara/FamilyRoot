import {
  Column,
  CreateDateColumn,
  Entity,
  PrimaryGeneratedColumn,
} from 'typeorm';
import type { ReleaseChannel } from './app-release-policy.entity';

@Entity('app_release_policy_audit')
export class AppReleasePolicyAuditEntity {
  @PrimaryGeneratedColumn('uuid')
  auditId!: string;

  @Column({ type: 'text' })
  channel!: ReleaseChannel;

  @Column({ type: 'uuid' })
  actorUserId!: string;

  @Column({ type: 'text', nullable: true })
  beforeJson!: string | null;

  @Column({ type: 'text' })
  afterJson!: string;

  @CreateDateColumn()
  createdAt!: Date;
}
