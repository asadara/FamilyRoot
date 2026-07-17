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
  field!: 'notes' | 'birthPlace' | 'deathPlace';

  @Column({ type: 'text' })
  proposedValue!: string;

  @Column({ type: 'text', nullable: true })
  reason!: string | null;

  @Column({ type: 'text', default: 'PENDING' })
  status!: 'PENDING' | 'APPROVED' | 'REJECTED';

  @Column({ type: 'uuid', nullable: true })
  reviewedByUserId!: string | null;

  @Column({ type: 'datetime', nullable: true })
  reviewedAt!: Date | null;

  @CreateDateColumn()
  createdAt!: Date;
}
