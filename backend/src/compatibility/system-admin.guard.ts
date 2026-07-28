import {
  CanActivate,
  ExecutionContext,
  ForbiddenException,
  Injectable,
} from '@nestjs/common';
import { AuthUser } from '../auth/auth-user.interface';

@Injectable()
export class SystemAdminGuard implements CanActivate {
  canActivate(context: ExecutionContext): boolean {
    const request = context.switchToHttp().getRequest<{ user: AuthUser }>();
    const allowedUserIds = new Set(
      (process.env.SYSTEM_ADMIN_USER_IDS ?? '')
        .split(',')
        .map((value) => value.trim())
        .filter(Boolean),
    );
    if (!allowedUserIds.has(request.user.userId)) {
      throw new ForbiddenException('System administrator access is required');
    }
    return true;
  }
}
