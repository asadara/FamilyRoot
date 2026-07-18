import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
} from 'typeorm';

@Entity('refresh_sessions')
@Index(['familyId'])
@Index(['userId'])
export class RefreshSessionEntity {
  @PrimaryGeneratedColumn('uuid')
  sessionId!: string;

  @Column({ type: 'uuid' })
  userId!: string;

  @Column({ type: 'uuid' })
  familyId!: string;

  @Column({ type: 'text', unique: true, select: false })
  tokenHash!: string;

  @Column({ type: 'datetime' })
  expiresAt!: Date;

  @Column({ type: 'datetime', nullable: true })
  revokedAt!: Date | null;

  @Column({ type: 'uuid', nullable: true })
  replacedBySessionId!: string | null;

  @CreateDateColumn()
  createdAt!: Date;
}
