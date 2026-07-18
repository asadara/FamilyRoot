import {
  BadRequestException,
  Body,
  Controller,
  Get,
  Post,
  Query,
} from '@nestjs/common';
import { isUUID } from 'class-validator';
import { ExportService } from './export.service';
import { SpaceRoles } from '../common/space-roles.decorator';
import { ActorUserId } from '../common/actor-user-id.decorator';
import { ImportGedcomDto } from './dto/import-gedcom.dto';
import { RestoreBackupDto } from './dto/restore-backup.dto';

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

  @Get('space/gedcom')
  @SpaceRoles('OWNER', 'ADMIN')
  exportGedcom(@Query('spaceId') spaceId: string) {
    this.validateSpaceId(spaceId);
    return this.exportService.exportGedcom(spaceId);
  }

  @Post('space/gedcom/import')
  @SpaceRoles('OWNER', 'ADMIN')
  importGedcom(
    @Body() dto: ImportGedcomDto,
    @ActorUserId() actorUserId: string,
  ) {
    return this.exportService.importGedcom(
      dto.spaceId,
      dto.content,
      actorUserId,
    );
  }

  @Get('space/backup')
  @SpaceRoles('OWNER', 'ADMIN')
  createBackup(@Query('spaceId') spaceId: string) {
    this.validateSpaceId(spaceId);
    return this.exportService.createBackup(spaceId);
  }

  @Post('space/backup/restore')
  @SpaceRoles('OWNER', 'ADMIN')
  restoreBackup(
    @Body() dto: RestoreBackupDto,
    @ActorUserId() actorUserId: string,
  ) {
    return this.exportService.restoreBackup(
      dto.spaceId,
      dto.backup,
      actorUserId,
    );
  }

  private validateSpaceId(spaceId: string) {
    if (!spaceId || !isUUID(spaceId)) {
      throw new BadRequestException('Invalid spaceId');
    }
  }
}
