import {
  Column,
  CreateDateColumn,
  Entity,
  PrimaryGeneratedColumn,
} from 'typeorm';

@Entity('account_lifecycle_audit')
export class AccountLifecycleAuditEntity {
  @PrimaryGeneratedColumn('uuid')
  auditId!: string;

  @Column({ type: 'uuid' })
  userId!: string;

  @Column({ type: 'text' })
  operation!: 'DELETE';

  @Column({ type: 'text' })
  impactJson!: string;

  @CreateDateColumn()
  createdAt!: Date;
}
