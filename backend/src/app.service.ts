import { Injectable } from '@nestjs/common';

@Injectable()
export class AppService {
  getHello(): string {
    return 'Hello World!';
  }

  getHealth() {
    return {
      status: 'ok',
      service: 'familyroot-api',
      version: process.env.npm_package_version ?? 'unknown',
      timestamp: new Date().toISOString(),
    };
  }
}
