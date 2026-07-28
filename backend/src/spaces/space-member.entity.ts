import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
} from 'typeorm';

@Entity('space_members')
@Index(['spaceId', 'userId'], { unique: true })
@Index('UQ_space_members_single_owner', ['spaceId'], {
  unique: true,
  where: `"role" = 'OWNER'`,
})
export class SpaceMemberEntity {
  @PrimaryGeneratedColumn('uuid')
  memberId!: string;

  @Column({ type: 'uuid' })
  spaceId!: string;

  @Column({ type: 'uuid' })
  userId!: string;

  @Column({ type: 'text' })
  role!: 'OWNER' | 'ADMIN' | 'EDITOR' | 'VIEWER';

  @CreateDateColumn()
  joinedAt!: Date;
}
