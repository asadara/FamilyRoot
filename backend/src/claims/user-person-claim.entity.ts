import {
  Column,
  CreateDateColumn,
  Entity,
  PrimaryGeneratedColumn,
} from 'typeorm';

@Entity('user_person_claims')
export class UserPersonClaimEntity {
  @PrimaryGeneratedColumn('uuid')
  claimId!: string;

  @Column({ type: 'uuid' })
  spaceId!: string;

  @Column({ type: 'uuid' })
  userId!: string;

  @Column({ type: 'uuid' })
  personId!: string;

  @Column({ type: 'text', default: 'PENDING' })
  status!: 'PENDING' | 'VERIFIED' | 'REJECTED';

  @CreateDateColumn()
  requestedAt!: Date;
}
