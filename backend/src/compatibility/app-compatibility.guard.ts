import { CanActivate, ExecutionContext, Injectable } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { AppCompatibilityService } from './app-compatibility.service';
import { SKIP_APP_COMPATIBILITY_KEY } from './skip-app-compatibility.decorator';

@Injectable()
export class AppCompatibilityGuard implements CanActivate {
  constructor(
    private readonly reflector: Reflector,
    private readonly appCompatibilityService: AppCompatibilityService,
  ) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const skip = this.reflector.getAllAndOverride<boolean>(
      SKIP_APP_COMPATIBILITY_KEY,
      [context.getHandler(), context.getClass()],
    );
    if (skip) return true;

    const request = context.switchToHttp().getRequest<{
      headers: Record<string, string | string[] | undefined>;
    }>();
    const header = (name: string) => {
      const value = request.headers[name];
      return Array.isArray(value) ? value[0] : value;
    };
    await this.appCompatibilityService.enforceHeaders({
      versionCode: header('x-app-version-code'),
      apiContractVersion: header('x-api-contract-version'),
      channel: header('x-release-channel'),
    });
    return true;
  }
}
