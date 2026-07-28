import {
  Check,
  Column,
  CreateDateColumn,
  Entity,
  PrimaryGeneratedColumn,
} from 'typeorm';

@Entity('family_spaces')
@Check(
  'CHK_family_spaces_lifecycle',
  `(
    "status" = 'ACTIVE' AND "archivedAt" IS NULL AND "deletedAt" IS NULL
  ) OR (
    "status" = 'ARCHIVED' AND "archivedAt" IS NOT NULL AND "deletedAt" IS NULL
  ) OR (
    "status" = 'DELETED' AND "archivedAt" IS NOT NULL AND "deletedAt" IS NOT NULL
  )`,
)
export class FamilySpaceEntity {
  @PrimaryGeneratedColumn('uuid')
  spaceId!: string;

  @Column({ type: 'text' })
  name!: string;

  @Column({ type: 'uuid' })
  createdBy!: string; // userId

  @Column({ type: 'text', default: 'ACTIVE' })
  status!: 'ACTIVE' | 'ARCHIVED' | 'DELETED';

  @Column({ type: Date, nullable: true })
  archivedAt!: Date | null;

  @Column({ type: Date, nullable: true })
  deletedAt!: Date | null;

  @CreateDateColumn()
  createdAt!: Date;
}
