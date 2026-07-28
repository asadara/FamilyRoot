import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
} from 'typeorm';

@Entity('relationships')
@Index(
  'UQ_relationships_spouse_identity',
  ['spaceId', 'fromPersonId', 'toPersonId'],
  {
    unique: true,
    where: `"type" = 'SPOUSE'`,
  },
)
@Index(
  'UQ_relationships_parentage_identity',
  ['spaceId', 'fromPersonId', 'toPersonId', 'meta'],
  { unique: true, where: `"type" = 'PARENT_CHILD'` },
)
export class RelationshipEntity {
  @PrimaryGeneratedColumn('uuid')
  relationshipId!: string;

  @Column({ type: 'uuid' })
  spaceId!: string;

  @Column({ type: 'text' })
  type!: 'PARENT_CHILD' | 'SPOUSE';

  @Column({ type: 'uuid' })
  fromPersonId!: string;

  @Column({ type: 'uuid' })
  toPersonId!: string;

  @Column({ type: 'text', nullable: true })
  meta!:
    | 'BIOLOGICAL'
    | 'ADOPTIVE'
    | 'STEP'
    | 'FOSTER'
    | 'GUARDIAN'
    | 'MARRIED'
    | 'DIVORCED'
    | 'WIDOWED'
    | null;

  @Column({ type: 'text', nullable: true })
  startDate!: string | null;

  @Column({ type: 'text', nullable: true })
  endDate!: string | null;

  @Column({ type: 'text', nullable: true })
  careContext!: string | null;

  @CreateDateColumn()
  createdAt!: Date;
}

export type ParentChildMeta =
  | 'BIOLOGICAL'
  | 'ADOPTIVE'
  | 'STEP'
  | 'FOSTER'
  | 'GUARDIAN';

export const isCareRelationshipMeta = (
  meta: RelationshipEntity['meta'],
): meta is 'FOSTER' | 'GUARDIAN' => meta === 'FOSTER' || meta === 'GUARDIAN';

export const isLineageParentChildMeta = (
  meta: RelationshipEntity['meta'],
): boolean =>
  meta == null ||
  meta === 'BIOLOGICAL' ||
  meta === 'ADOPTIVE' ||
  meta === 'STEP';
