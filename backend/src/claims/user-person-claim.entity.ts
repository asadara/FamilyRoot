import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
} from 'typeorm';

@Entity('user_person_claims')
@Index(
  'UQ_claims_active_space_user_person',
  ['spaceId', 'userId', 'personId'],
  {
    unique: true,
    where: `"status" IN ('PENDING', 'VERIFIED')`,
  },
)
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
