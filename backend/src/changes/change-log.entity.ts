import { Column } from 'typeorm';
import { CreateDateColumn } from 'typeorm';
import { Entity } from 'typeorm';
import { PrimaryGeneratedColumn } from 'typeorm';

@Entity('change_log')
export class ChangeLogEntity {
  @PrimaryGeneratedColumn('uuid')
  changeId!: string;

  @Column({ type: 'uuid' })
  spaceId!: string;

  @Column({ type: 'uuid' })
  actorUserId!: string;

  @Column({ type: 'text' })
  entityType!:
    | 'SPACE'
    | 'MEMBERSHIP'
    | 'INVITATION'
    | 'PERSON'
    | 'RELATIONSHIP'
    | 'CLAIM'
    | 'SOURCE'
    | 'MEDIA'
    | 'PROPOSAL';

  @Column({ type: 'uuid' })
  entityId!: string;

  @Column({ type: 'text' })
  operation!: 'CREATE' | 'UPDATE' | 'DELETE' | 'VERIFY';

  @Column({ type: 'text', nullable: true })
  note!: string | null;

  @Column({ type: 'text', nullable: true })
  beforeJson!: string | null;

  @Column({ type: 'text', nullable: true })
  afterJson!: string | null;

  @CreateDateColumn()
  createdAt!: Date;
}
