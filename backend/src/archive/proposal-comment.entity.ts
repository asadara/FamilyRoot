import {
  Column,
  CreateDateColumn,
  Entity,
  PrimaryGeneratedColumn,
} from 'typeorm';

@Entity('proposal_comments')
export class ProposalCommentEntity {
  @PrimaryGeneratedColumn('uuid')
  commentId!: string;

  @Column({ type: 'uuid' })
  spaceId!: string;

  @Column({ type: 'uuid' })
  proposalId!: string;

  @Column({ type: 'uuid' })
  authorUserId!: string;

  @Column({ type: 'text' })
  body!: string;

  @CreateDateColumn()
  createdAt!: Date;
}
