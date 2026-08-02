import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
  UpdateDateColumn,
} from 'typeorm';

@Entity('history_access_requests')
@Index(['spaceId', 'userId'], { unique: true })
export class HistoryAccessRequestEntity {
  @PrimaryGeneratedColumn('uuid')
  requestId!: string;

  @Column({ type: 'uuid' })
  spaceId!: string;

  @Column({ type: 'uuid' })
  userId!: string;

  @Column({ type: 'text', default: 'PENDING' })
  status!: 'PENDING' | 'APPROVED' | 'REJECTED';

  @Column({ type: 'uuid', nullable: true })
  reviewedByUserId!: string | null;

  @Column({ type: Date, nullable: true })
  reviewedAt!: Date | null;

  @CreateDateColumn()
  createdAt!: Date;

  @UpdateDateColumn()
  updatedAt!: Date;
}
