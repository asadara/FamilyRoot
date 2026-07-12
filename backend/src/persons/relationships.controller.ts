import {
  BadRequestException,
  Body,
  Controller,
  Get,
  Post,
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
    );
  }
}
