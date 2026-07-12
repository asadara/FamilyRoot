import { IsIn, IsUUID } from 'class-validator';
import { SpaceRole } from '../../common/space-roles.decorator';

export class AddMemberDto {
  @IsUUID()
  spaceId!: string;

  @IsUUID()
  userId!: string;

  @IsIn(['ADMIN', 'EDITOR', 'VIEWER'])
  role!: Exclude<SpaceRole, 'OWNER'>;
}
