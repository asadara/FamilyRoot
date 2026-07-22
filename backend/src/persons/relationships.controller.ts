import {
  BadRequestException,
  Body,
  Controller,
  Delete,
  Get,
  Post,
  Param,
  Query,
} from '@nestjs/common';
import { isUUID } from 'class-validator';
import { RelationshipsService } from './relationships.service';
import { CreateSpouseDto } from './dto/create-spouse.dto';
import { ActorUserId } from '../common/actor-user-id.decorator';
import { SpaceRoles } from '../common/space-roles.decorator';

@Controller('relationships')
export class RelationshipsController {
  constructor(private readonly relationshipsService: RelationshipsService) {}

  @Get('path')
  @SpaceRoles('OWNER', 'ADMIN', 'EDITOR', 'VIEWER')
  path(
    @Query('spaceId') spaceId: string,
    @Query('fromPersonId') fromPersonId: string,
    @Query('toPersonId') toPersonId: string,
  ) {
    if (!spaceId || !isUUID(spaceId)) {
      throw new BadRequestException('Invalid spaceId');
    }
    if (!fromPersonId || !isUUID(fromPersonId)) {
      throw new BadRequestException('Invalid fromPersonId');
    }
    if (!toPersonId || !isUUID(toPersonId)) {
      throw new BadRequestException('Invalid toPersonId');
    }
    return this.relationshipsService.findPath(
      spaceId,
      fromPersonId,
      toPersonId,
    );
  }

  @Get()
  @SpaceRoles('OWNER', 'ADMIN', 'EDITOR', 'VIEWER')
  list(
    @Query('spaceId') spaceId: string,
    @Query('personId') personId?: string,
  ) {
    if (!spaceId || !isUUID(spaceId)) {
      throw new BadRequestException('Invalid spaceId');
    }
    if (personId && !isUUID(personId)) {
      throw new BadRequestException('Invalid personId');
    }

    if (!personId) {
      return this.relationshipsService.findAll(spaceId);
    }

    return this.relationshipsService.findByPerson(spaceId, personId);
  }

  @Post('spouse')
  @SpaceRoles('OWNER', 'ADMIN', 'EDITOR')
  createSpouse(
    @ActorUserId() actorUserId: string,
    @Body() dto: CreateSpouseDto,
  ) {
    return this.relationshipsService.createSpouse(
      dto.spaceId,
      dto.personAId,
      dto.personBId,
      dto.meta,
      dto.startDate,
      dto.endDate ?? null,
      actorUserId,
      dto.clientMutationId,
    );
  }

  @Delete(':relationshipId')
  @SpaceRoles('OWNER', 'ADMIN', 'EDITOR')
  remove(
    @ActorUserId() actorUserId: string,
    @Param('relationshipId') relationshipId: string,
    @Query('spaceId') spaceId: string,
  ) {
    if (!spaceId || !isUUID(spaceId)) {
      throw new BadRequestException('Invalid spaceId');
    }
    if (!relationshipId || !isUUID(relationshipId)) {
      throw new BadRequestException('Invalid relationshipId');
    }
    return this.relationshipsService.remove(
      spaceId,
      relationshipId,
      actorUserId,
    );
  }
}
