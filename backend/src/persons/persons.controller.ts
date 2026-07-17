import {
  BadRequestException,
  Body,
  Controller,
  Get,
  Patch,
  Post,
  Query,
  Param,
  Delete,
} from '@nestjs/common';
import { PersonsService } from './persons.service';
import { CreatePersonDto } from './dto/create-person.dto';
import { isUUID } from 'class-validator';
import { AddParentChildDto } from './dto/add-parent-child.dto';
import { UpdateLifeStatusDto } from './dto/update-life-status.dto';
import { DeletePersonDto } from './dto/delete-person.dto';
import { MergePersonsDto } from './dto/merge-persons.dto';
import { ActorUserId } from '../common/actor-user-id.decorator';
import { SpaceRoles } from '../common/space-roles.decorator';

@Controller('persons')
export class PersonsController {
  constructor(private readonly personsService: PersonsService) {}

  @Get()
  @SpaceRoles('OWNER', 'ADMIN', 'EDITOR', 'VIEWER')
  list(@Query('spaceId') spaceId: string) {
    if (!spaceId || !isUUID(spaceId)) {
      throw new BadRequestException('Invalid spaceId');
    }
    return this.personsService.findBySpace(spaceId);
  }

  @Post()
  @SpaceRoles('OWNER', 'ADMIN', 'EDITOR')
  create(@ActorUserId() actorUserId: string, @Body() dto: CreatePersonDto) {
    return this.personsService.create(dto, actorUserId);
  }

  @Get('duplicates')
  @SpaceRoles('OWNER', 'ADMIN', 'EDITOR', 'VIEWER')
  duplicates(@Query('spaceId') spaceId: string) {
    if (!spaceId || !isUUID(spaceId)) {
      throw new BadRequestException('Invalid spaceId');
    }
    return this.personsService.findDuplicateCandidates(spaceId);
  }

  @Post('merge')
  @SpaceRoles('OWNER', 'ADMIN')
  merge(@ActorUserId() actorUserId: string, @Body() dto: MergePersonsDto) {
    return this.personsService.mergePersons(
      dto.spaceId,
      dto.sourcePersonId,
      dto.targetPersonId,
      actorUserId,
    );
  }

  @Post('parent-child')
  @SpaceRoles('OWNER', 'ADMIN', 'EDITOR')
  addParentChild(
    @ActorUserId() actorUserId: string,
    @Body() dto: AddParentChildDto,
  ) {
    return this.personsService.addParentChild(
      dto.spaceId,
      dto.parentId,
      dto.childId,
      dto.meta,
      actorUserId,
    );
  }

  @Patch(':personId/life')
  @SpaceRoles('OWNER', 'ADMIN', 'EDITOR')
  updateLifeStatus(
    @Param('personId') personId: string,
    @ActorUserId() actorUserId: string,
    @Body() dto: UpdateLifeStatusDto,
  ) {
    if (!isUUID(personId)) {
      throw new BadRequestException('Invalid personId');
    }
    return this.personsService.updateLifeStatus(
      dto.spaceId,
      personId,
      dto.lifeStatus,
      dto.deceasedAt ?? null,
      actorUserId,
    );
  }

  @Delete(':personId')
  @SpaceRoles('OWNER', 'ADMIN')
  softDelete(
    @Param('personId') personId: string,
    @ActorUserId() actorUserId: string,
    @Body() dto: DeletePersonDto,
  ) {
    if (!isUUID(personId)) {
      throw new BadRequestException('Invalid personId');
    }
    return this.personsService.softDelete(dto.spaceId, personId, actorUserId);
  }
}
