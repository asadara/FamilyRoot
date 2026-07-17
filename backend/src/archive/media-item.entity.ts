import {
  Column,
  CreateDateColumn,
  Entity,
  PrimaryGeneratedColumn,
} from 'typeorm';

@Entity('media_items')
export class MediaItemEntity {
  @PrimaryGeneratedColumn('uuid')
  mediaId!: string;

  @Column({ type: 'uuid' })
  spaceId!: string;

  @Column({ type: 'uuid' })
  personId!: string;

  @Column({ type: 'text' })
  label!: string;

  @Column({ type: 'text' })
  kind!: 'PHOTO' | 'DOCUMENT' | 'AUDIO' | 'OTHER';

  @Column({ type: 'text' })
  uri!: string;

  @Column({ type: 'uuid', nullable: true })
  sourceId!: string | null;

  @CreateDateColumn()
  createdAt!: Date;
}
