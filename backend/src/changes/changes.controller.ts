import { BadRequestException, Controller, Get, Query } from '@nestjs/common';
import { isUUID } from 'class-validator';
import { ChangesService } from './changes.service';
import { SpaceRoles } from '../common/space-roles.decorator';

@Controller('changes')
export class ChangesController {
  constructor(private readonly changesService: ChangesService) {}

  @Get()
  @SpaceRoles('OWNER', 'ADMIN', 'EDITOR', 'VIEWER')
  list(@Query('spaceId') spaceId: string, @Query('limit') limit = '50') {
    if (!spaceId || !isUUID(spaceId)) {
      throw new BadRequestException('Invalid spaceId');
    }
    const parsedLimit = parseInt(limit, 10);
    return this.changesService.findBySpace(spaceId, parsedLimit);
  }
}
