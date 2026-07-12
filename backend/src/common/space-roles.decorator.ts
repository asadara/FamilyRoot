import { SetMetadata } from '@nestjs/common';

export type SpaceRole = 'OWNER' | 'ADMIN' | 'EDITOR' | 'VIEWER';
export const SPACE_ROLES_KEY = 'spaceRoles';
export const SpaceRoles = (...roles: SpaceRole[]) =>
  SetMetadata(SPACE_ROLES_KEY, roles);
