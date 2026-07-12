import {
  Column,
  CreateDateColumn,
  Entity,
  PrimaryGeneratedColumn,
  UpdateDateColumn,
} from 'typeorm';

@Entity('users')
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

  @CreateDateColumn()
  createdAt!: Date;

  @UpdateDateColumn()
  updatedAt!: Date;
}
