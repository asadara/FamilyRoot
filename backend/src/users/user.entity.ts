import {
  Check,
  Column,
  CreateDateColumn,
  Entity,
  PrimaryGeneratedColumn,
  UpdateDateColumn,
} from 'typeorm';

@Entity('users')
@Check(
  'CHK_users_account_state',
  `(
    "accountStatus" = 'ACTIVE'
    AND ("email" IS NOT NULL OR "phone" IS NOT NULL)
    AND "deletedAt" IS NULL
  ) OR (
    "accountStatus" = 'DELETED'
    AND "email" IS NULL
    AND "phone" IS NULL
    AND "passwordHash" IS NULL
    AND "deletedAt" IS NOT NULL
  )`,
)
export class UserEntity {
  @PrimaryGeneratedColumn('uuid')
  userId!: string;

  @Column({ type: 'text', unique: true, nullable: true })
  email!: string | null;

  @Column({ type: 'text', unique: true, nullable: true })
  phone!: string | null;

  @Column({ type: 'text' })
  displayName!: string;

  @Column({ type: 'text', nullable: true, select: false })
  passwordHash!: string | null;

  @Column({ type: 'text', default: 'ACTIVE' })
  accountStatus!: 'ACTIVE' | 'DELETED';

  @Column({ type: Date, nullable: true })
  deletedAt!: Date | null;

  @CreateDateColumn()
  createdAt!: Date;

  @UpdateDateColumn()
  updatedAt!: Date;
}
