import {
  Body,
  Controller,
  Delete,
  Get,
  Param,
  Patch,
  Post,
  Query,
} from '@nestjs/common';
import { ActorUserId } from '../common/actor-user-id.decorator';
import { SpaceRoles } from '../common/space-roles.decorator';
import { AcceptInvitationDto } from './dto/accept-invitation.dto';
import { AddMemberDto } from './dto/add-member.dto';
import { CreateInvitationDto } from './dto/create-invitation.dto';
import { CreateSpaceDto } from './dto/create-space.dto';
import { ListInvitationsQueryDto } from './dto/list-invitations-query.dto';
import { TransferOwnershipDto } from './dto/transfer-ownership.dto';
import { UpdateMemberRoleDto } from './dto/update-member-role.dto';
import { DeleteSpaceDto } from './dto/delete-space.dto';
import { AllowArchivedSpaceMutation } from '../common/allow-archived-space-mutation.decorator';
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

  @Get(':spaceId/lifecycle-impact')
  @SpaceRoles('OWNER')
  lifecycleImpact(@Param('spaceId') spaceId: string) {
    return this.spacesService.lifecycleImpact(spaceId);
  }

  @Post(':spaceId/archive')
  @SpaceRoles('OWNER')
  archive(
    @ActorUserId() actorUserId: string,
    @Param('spaceId') spaceId: string,
  ) {
    return this.spacesService.archiveSpace(spaceId, actorUserId);
  }

  @Post(':spaceId/restore')
  @SpaceRoles('OWNER')
  @AllowArchivedSpaceMutation()
  restore(
    @ActorUserId() actorUserId: string,
    @Param('spaceId') spaceId: string,
  ) {
    return this.spacesService.restoreSpace(spaceId, actorUserId);
  }

  @Delete(':spaceId')
  @SpaceRoles('OWNER')
  @AllowArchivedSpaceMutation()
  deleteSpace(
    @ActorUserId() actorUserId: string,
    @Param('spaceId') spaceId: string,
    @Body() dto: DeleteSpaceDto,
  ) {
    return this.spacesService.deleteSpace(
      spaceId,
      actorUserId,
      dto.confirmation,
      dto.acknowledgeExport,
    );
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

  @Get(':spaceId/members')
  @SpaceRoles('OWNER', 'ADMIN', 'EDITOR', 'VIEWER')
  listMembers(
    @ActorUserId() actorUserId: string,
    @Param('spaceId') spaceId: string,
  ) {
    return this.spacesService.listMembers(spaceId, actorUserId);
  }

  @Patch(':spaceId/members/:memberId')
  @SpaceRoles('OWNER', 'ADMIN')
  updateMemberRole(
    @ActorUserId() actorUserId: string,
    @Param('spaceId') spaceId: string,
    @Param('memberId') memberId: string,
    @Body() dto: UpdateMemberRoleDto,
  ) {
    return this.spacesService.updateMemberRole(
      spaceId,
      memberId,
      dto.role,
      actorUserId,
    );
  }

  @Delete(':spaceId/members/:memberId')
  @SpaceRoles('OWNER', 'ADMIN')
  removeMember(
    @ActorUserId() actorUserId: string,
    @Param('spaceId') spaceId: string,
    @Param('memberId') memberId: string,
  ) {
    return this.spacesService.removeMember(spaceId, memberId, actorUserId);
  }

  @Post(':spaceId/ownership-transfer')
  @SpaceRoles('OWNER')
  transferOwnership(
    @ActorUserId() actorUserId: string,
    @Param('spaceId') spaceId: string,
    @Body() dto: TransferOwnershipDto,
  ) {
    return this.spacesService.transferOwnership(
      spaceId,
      dto.targetMemberId,
      actorUserId,
    );
  }

  @Post(':spaceId/leave')
  @SpaceRoles('OWNER', 'ADMIN', 'EDITOR', 'VIEWER')
  @AllowArchivedSpaceMutation()
  leaveSpace(
    @ActorUserId() actorUserId: string,
    @Param('spaceId') spaceId: string,
  ) {
    return this.spacesService.leaveSpace(spaceId, actorUserId);
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
      dto.targetEmail,
    );
  }

  @Get(':spaceId/invitations')
  @SpaceRoles('OWNER', 'ADMIN')
  listInvitations(
    @ActorUserId() actorUserId: string,
    @Param('spaceId') spaceId: string,
    @Query() query: ListInvitationsQueryDto,
  ) {
    return this.spacesService.listInvitations(
      spaceId,
      actorUserId,
      query.status,
    );
  }

  @Delete(':spaceId/invitations/:inviteId')
  @SpaceRoles('OWNER', 'ADMIN')
  revokeInvitation(
    @ActorUserId() actorUserId: string,
    @Param('spaceId') spaceId: string,
    @Param('inviteId') inviteId: string,
  ) {
    return this.spacesService.revokeInvitation(spaceId, inviteId, actorUserId);
  }

  @Get('invitations/:token')
  previewInvitation(
    @ActorUserId() actorUserId: string,
    @Param('token') token: string,
  ) {
    return this.spacesService.previewInvitation(token, actorUserId);
  }

  @Post('invitations/accept')
  acceptInvitation(
    @ActorUserId() actorUserId: string,
    @Body() dto: AcceptInvitationDto,
  ) {
    return this.spacesService.acceptInvitation(dto.token, actorUserId);
  }
}
