import {
  Column,
  CreateDateColumn,
  Entity,
  PrimaryGeneratedColumn,
} from 'typeorm';

@Entity('edit_proposals')
export class EditProposalEntity {
  @PrimaryGeneratedColumn('uuid')
  proposalId!: string;

  @Column({ type: 'uuid' })
  spaceId!: string;

  @Column({ type: 'uuid' })
  personId!: string;

  @Column({ type: 'text' })
  field!: 'notes' | 'birthPlace' | 'deathPlace' | 'DELETE_PERSON';

  @Column({ type: 'text' })
  proposedValue!: string;

  @Column({ type: 'text', nullable: true })
  beforeValue!: string | null;

  @Column({ type: 'text', nullable: true })
  reason!: string | null;

  @Column({ type: 'text', default: 'PENDING' })
  status!: 'PENDING' | 'APPROVED' | 'REJECTED';

  @Column({ type: 'uuid', nullable: true })
  reviewedByUserId!: string | null;

  @Column({ type: Date, nullable: true })
  reviewedAt!: Date | null;

  @Column({ type: 'text', nullable: true })
  reviewReason!: string | null;

  @CreateDateColumn()
  createdAt!: Date;
}
