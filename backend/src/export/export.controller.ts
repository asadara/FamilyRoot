import { BadRequestException, Controller, Get, Query } from '@nestjs/common';
import { isUUID } from 'class-validator';
import { ExportService } from './export.service';
import { SpaceRoles } from '../common/space-roles.decorator';

@Controller('export')
export class ExportController {
  constructor(private readonly exportService: ExportService) {}

  @Get('space')
  @SpaceRoles('OWNER', 'ADMIN')
  exportSpace(@Query('spaceId') spaceId: string) {
    if (!spaceId || !isUUID(spaceId)) {
      throw new BadRequestException('Invalid spaceId');
    }
    return this.exportService.exportSpace(spaceId);
  }
}
