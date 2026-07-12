import {
  Column,
  CreateDateColumn,
  Entity,
  PrimaryGeneratedColumn,
} from 'typeorm';

@Entity('family_spaces')
export class FamilySpaceEntity {
  @PrimaryGeneratedColumn('uuid')
  spaceId!: string;

  @Column({ type: 'text' })
  name!: string;

  @Column({ type: 'uuid' })
  createdBy!: string; // userId

  @CreateDateColumn()
  createdAt!: Date;
}
