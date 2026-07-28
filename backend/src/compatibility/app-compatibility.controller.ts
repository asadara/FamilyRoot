import { Body, Controller, Get, Put, Query, UseGuards } from '@nestjs/common';
import { ActorUserId } from '../common/actor-user-id.decorator';
import { Public } from '../auth/public.decorator';
import { AppCompatibilityService } from './app-compatibility.service';
import { CheckAppCompatibilityDto } from './dto/check-app-compatibility.dto';
import { UpdateAppReleasePolicyDto } from './dto/update-app-release-policy.dto';
import { SystemAdminGuard } from './system-admin.guard';
import { SkipAppCompatibility } from './skip-app-compatibility.decorator';

@Controller('app-compatibility/android')
@SkipAppCompatibility()
export class AppCompatibilityController {
  constructor(
    private readonly appCompatibilityService: AppCompatibilityService,
  ) {}

  @Get()
  @Public()
  check(@Query() query: CheckAppCompatibilityDto) {
    return this.appCompatibilityService.check(query);
  }

  @Put('policy')
  @UseGuards(SystemAdminGuard)
  updatePolicy(
    @ActorUserId() actorUserId: string,
    @Body() dto: UpdateAppReleasePolicyDto,
  ) {
    return this.appCompatibilityService.updatePolicy(dto, actorUserId);
  }
}
