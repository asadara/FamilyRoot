import { Column, CreateDateColumn, Entity, PrimaryColumn } from 'typeorm';

@Entity('client_mutations')
export class ClientMutationEntity {
  @PrimaryColumn({ type: 'uuid' })
  clientMutationId!: string;

  @Column({ type: 'uuid' })
  actorUserId!: string;

  @Column({ type: 'uuid' })
  spaceId!: string;

  @Column({ type: 'text' })
  operation!: string;

  @Column({ type: 'text' })
  requestFingerprint!: string;

  @Column({ type: 'text' })
  responseJson!: string;

  @CreateDateColumn()
  createdAt!: Date;
}
