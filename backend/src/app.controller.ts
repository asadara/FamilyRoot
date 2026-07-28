import { Controller, Get } from '@nestjs/common';
import { AppService } from './app.service';
import { Public } from './auth/public.decorator';
import { SkipAppCompatibility } from './compatibility/skip-app-compatibility.decorator';

@Controller()
@SkipAppCompatibility()
export class AppController {
  constructor(private readonly appService: AppService) {}

  @Get()
  @Public()
  getHello(): string {
    return this.appService.getHello();
  }

  @Get('health')
  @Public()
  getHealth() {
    return this.appService.getHealth();
  }
}
