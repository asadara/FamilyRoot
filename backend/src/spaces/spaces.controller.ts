import { Body, Controller, Get, Param, Post } from '@nestjs/common';
import { ActorUserId } from '../common/actor-user-id.decorator';
import { SpaceRoles } from '../common/space-roles.decorator';
import { AcceptInvitationDto } from './dto/accept-invitation.dto';
import { AddMemberDto } from './dto/add-member.dto';
import { CreateInvitationDto } from './dto/create-invitation.dto';
import { CreateSpaceDto } from './dto/create-space.dto';
import { SpacesService } from './spaces.service';

@Controller('spaces')
export class SpacesController {
  constructor(private readonly spacesService: SpacesService) {}

  @Get()
  listMine(@ActorUserId() actorUserId: string) {
    return this.spacesService.findForUser(actorUserId);
  }

  @Post()
  create(@ActorUserId() actorUserId: string, @Body() dto: CreateSpaceDto) {
    return this.spacesService.create(dto.name, actorUserId);
  }

  @Post('members')
  @SpaceRoles('OWNER', 'ADMIN')
  addMember(@ActorUserId() actorUserId: string, @Body() dto: AddMemberDto) {
    return this.spacesService.addMember(
      dto.spaceId,
      dto.userId,
      dto.role,
      actorUserId,
    );
  }

  @Post('invitations')
  @SpaceRoles('OWNER', 'ADMIN')
  createInvitation(
    @ActorUserId() actorUserId: string,
    @Body() dto: CreateInvitationDto,
  ) {
    return this.spacesService.createInvitation(
      dto.spaceId,
      dto.role,
      actorUserId,
      dto.expiresInDays,
    );
  }

  @Get('invitations/:token')
  previewInvitation(@Param('token') token: string) {
    return this.spacesService.previewInvitation(token);
  }

  @Post('invitations/accept')
  acceptInvitation(
    @ActorUserId() actorUserId: string,
    @Body() dto: AcceptInvitationDto,
  ) {
    return this.spacesService.acceptInvitation(dto.token, actorUserId);
  }
}
