import {
  BadRequestException,
  Body,
  Controller,
  Get,
  Param,
  Post,
  Query,
} from '@nestjs/common';
import { isUUID } from 'class-validator';
import { ChangesService } from './changes.service';
import { SpaceRoles } from '../common/space-roles.decorator';
import { ActorUserId } from '../common/actor-user-id.decorator';
import { RequestHistoryAccessDto } from './dto/request-history-access.dto';
import { ReviewHistoryAccessDto } from './dto/review-history-access.dto';

@Controller('changes')
export class ChangesController {
  constructor(private readonly changesService: ChangesService) {}

  @Get('full')
  @SpaceRoles('OWNER', 'ADMIN', 'EDITOR', 'VIEWER')
  fullHistory(
    @ActorUserId() actorUserId: string,
    @Query('spaceId') spaceId: string,
    @Query('limit') limit = '50',
    @Query('before') before?: string,
  ) {
    this.validateSpaceId(spaceId);
    return this.changesService.findFullHistory(
      spaceId,
      actorUserId,
      parseInt(limit, 10),
      before,
    );
  }

  @Post('history-access-requests')
  @SpaceRoles('OWNER', 'ADMIN', 'EDITOR', 'VIEWER')
  requestFullHistory(
    @ActorUserId() actorUserId: string,
    @Body() dto: RequestHistoryAccessDto,
  ) {
    return this.changesService.requestFullHistoryAccess(
      dto.spaceId,
      actorUserId,
    );
  }

  @Get('history-access-requests/me')
  @SpaceRoles('OWNER', 'ADMIN', 'EDITOR', 'VIEWER')
  myHistoryRequest(
    @ActorUserId() actorUserId: string,
    @Query('spaceId') spaceId: string,
  ) {
    this.validateSpaceId(spaceId);
    return this.changesService.findMyHistoryAccessRequest(spaceId, actorUserId);
  }

  @Get('history-access-requests')
  @SpaceRoles('OWNER', 'ADMIN')
  historyRequests(@Query('spaceId') spaceId: string) {
    this.validateSpaceId(spaceId);
    return this.changesService.listHistoryAccessRequests(spaceId);
  }

  @Post('history-access-requests/:requestId/review')
  @SpaceRoles('OWNER', 'ADMIN')
  reviewHistoryRequest(
    @ActorUserId() actorUserId: string,
    @Param('requestId') requestId: string,
    @Body() dto: ReviewHistoryAccessDto,
  ) {
    this.validateSpaceId(dto.spaceId);
    if (!isUUID(requestId)) {
      throw new BadRequestException('Invalid requestId');
    }
    return this.changesService.reviewHistoryAccessRequest(
      dto.spaceId,
      requestId,
      dto.approved,
      actorUserId,
    );
  }

  @Get()
  @SpaceRoles('OWNER', 'ADMIN', 'EDITOR', 'VIEWER')
  list(@Query('spaceId') spaceId: string, @Query('limit') limit = '10') {
    this.validateSpaceId(spaceId);
    const parsedLimit = parseInt(limit, 10);
    return this.changesService.findBySpace(spaceId, parsedLimit);
  }

  private validateSpaceId(spaceId: string) {
    if (!spaceId || !isUUID(spaceId)) {
      throw new BadRequestException('Invalid spaceId');
    }
  }
}
