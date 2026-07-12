import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
} from 'typeorm';

@Entity('relationships')
@Index(['spaceId', 'type', 'fromPersonId', 'toPersonId'], { unique: true })
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
    | 'MARRIED'
    | 'DIVORCED'
    | 'WIDOWED'
    | null;

  @Column({ type: 'text', nullable: true })
  startDate!: string | null;

  @Column({ type: 'text', nullable: true })
  endDate!: string | null;

  @CreateDateColumn()
  createdAt!: Date;
}
