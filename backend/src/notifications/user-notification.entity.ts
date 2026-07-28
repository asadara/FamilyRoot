import {
  Column,
  CreateDateColumn,
  Entity,
  PrimaryGeneratedColumn,
} from 'typeorm';

@Entity('user_notifications')
export class UserNotificationEntity {
  @PrimaryGeneratedColumn('uuid')
  notificationId!: string;

  @Column({ type: 'uuid' })
  userId!: string;

  @Column({ type: 'uuid', nullable: true })
  spaceId!: string | null;

  @Column({ type: 'text' })
  kind!: 'SUCCESS' | 'WARNING' | 'ERROR' | 'INFO';

  @Column({ type: 'text' })
  code!: string;

  @Column({ type: 'text' })
  title!: string;

  @Column({ type: 'text' })
  message!: string;

  @Column({ type: Date, nullable: true })
  readAt!: Date | null;

  @CreateDateColumn()
  createdAt!: Date;
}
