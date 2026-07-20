import {
  Column,
  CreateDateColumn,
  Entity,
  PrimaryGeneratedColumn,
  UpdateDateColumn,
  VersionColumn,
} from 'typeorm';

@Entity('persons')
export class PersonEntity {
  @PrimaryGeneratedColumn('uuid')
  personId!: string;

  @Column({ type: 'uuid' })
  spaceId!: string; // FamilySpace

  @Column({ type: 'text' })
  fullName!: string;

  @Column({ type: 'text', nullable: true })
  title!: string | null;

  @Column({ type: 'text', nullable: true })
  firstName!: string | null;

  @Column({ type: 'text', nullable: true })
  lastName!: string | null;

  @Column({ type: 'text', nullable: true })
  suffix!: string | null;

  @Column({ type: 'text', nullable: true })
  nickName!: string | null;

  @Column({ type: 'text', nullable: true })
  gender!: string | null;

  @Column({ type: 'date', nullable: true })
  birthDate!: string | null;

  @Column({ type: 'text', nullable: true })
  birthPlace!: string | null;

  @Column({ type: 'date', nullable: true })
  deathDate!: string | null;

  @Column({ type: 'text', nullable: true })
  deathPlace!: string | null;

  @Column({ type: 'text', nullable: true })
  idNumber!: string | null;

  @Column({ type: 'text', nullable: true })
  notes!: string | null;

  @Column({ type: 'text', default: 'ALIVE' })
  lifeStatus!: 'ALIVE' | 'DECEASED' | 'UNKNOWN';

  @Column({ type: 'date', nullable: true })
  deceasedAt!: string | null;

  @VersionColumn({ type: 'int', default: 1 })
  version!: number;

  @Column({ type: 'boolean', default: false })
  isDeleted!: boolean;

  @Column({ name: 'deleted_at', type: Date, nullable: true })
  deletedAt!: Date | null;

  @CreateDateColumn()
  createdAt!: Date;

  @UpdateDateColumn()
  updatedAt!: Date;
}
