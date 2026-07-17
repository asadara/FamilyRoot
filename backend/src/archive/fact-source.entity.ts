import {
  Column,
  CreateDateColumn,
  Entity,
  PrimaryGeneratedColumn,
} from 'typeorm';

@Entity('fact_sources')
export class FactSourceEntity {
  @PrimaryGeneratedColumn('uuid')
  sourceId!: string;

  @Column({ type: 'uuid' })
  spaceId!: string;

  @Column({ type: 'uuid' })
  personId!: string;

  @Column({ type: 'text' })
  title!: string;

  @Column({ type: 'text' })
  type!: 'DOCUMENT' | 'STORY' | 'PHOTO' | 'OTHER';

  @Column({ type: 'text', nullable: true })
  url!: string | null;

  @Column({ type: 'text', nullable: true })
  note!: string | null;

  @CreateDateColumn()
  createdAt!: Date;
}
