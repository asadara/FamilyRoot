import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
} from 'typeorm';

@Entity('claim_confirmations')
@Index(['claimId', 'confirmedBy'], { unique: true })
export class ClaimConfirmationEntity {
  @PrimaryGeneratedColumn('uuid')
  confirmationId!: string;

  @Column({ type: 'uuid' })
  claimId!: string;

  @Column({ type: 'uuid' })
  confirmedBy!: string;

  @CreateDateColumn()
  confirmedAt!: Date;
}
