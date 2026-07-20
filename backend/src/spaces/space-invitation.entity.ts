import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
} from 'typeorm';

@Entity('space_invitations')
@Index(['token'], { unique: true })
export class SpaceInvitationEntity {
  @PrimaryGeneratedColumn('uuid')
  inviteId!: string;

  @Column({ type: 'uuid' })
  spaceId!: string;

  @Column({ type: 'text' })
  token!: string;

  @Column({ type: 'text' })
  role!: 'ADMIN' | 'EDITOR' | 'VIEWER';

  @Column({ type: 'uuid' })
  createdBy!: string;

  @Column({ type: Date })
  expiresAt!: Date;

  @Column({ type: 'uuid', nullable: true })
  acceptedBy?: string | null;

  @Column({ type: Date, nullable: true })
  acceptedAt?: Date | null;

  @Column({ type: Date, nullable: true })
  revokedAt?: Date | null;

  @CreateDateColumn()
  createdAt!: Date;
}
