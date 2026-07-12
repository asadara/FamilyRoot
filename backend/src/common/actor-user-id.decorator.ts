import { createParamDecorator, ExecutionContext } from '@nestjs/common';
import { AuthUser } from '../auth/auth-user.interface';

export const ActorUserId = createParamDecorator(
  (_data: unknown, context: ExecutionContext): string =>
    context.switchToHttp().getRequest<{ user: AuthUser }>().user.userId,
);
