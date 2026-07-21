import { Column, CreateDateColumn, Entity, PrimaryColumn } from 'typeorm';

@Entity('user_google_identities')
export class GoogleIdentityEntity {
  @PrimaryColumn({ type: 'text' })
  googleSubject!: string;

  @Column({ type: 'uuid', unique: true })
  userId!: string;

  @Column({ type: 'text' })
  emailAtLink!: string;

  @CreateDateColumn()
  createdAt!: Date;
}
