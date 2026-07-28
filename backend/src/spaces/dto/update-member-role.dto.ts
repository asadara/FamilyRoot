import { IsIn } from 'class-validator';
import type { SpaceRole } from '../../common/space-roles.decorator';

export class UpdateMemberRoleDto {
  @IsIn(['ADMIN', 'EDITOR', 'VIEWER'])
  role!: Exclude<SpaceRole, 'OWNER'>;
}
