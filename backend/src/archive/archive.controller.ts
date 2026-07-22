import {
  BadRequestException,
  Body,
  Controller,
  Get,
  Param,
  Post,
  Query,
  UploadedFile,
  UseInterceptors,
} from '@nestjs/common';
import { FileInterceptor } from '@nestjs/platform-express';
import { isUUID } from 'class-validator';
import { ActorUserId } from '../common/actor-user-id.decorator';
import { SpaceRoles } from '../common/space-roles.decorator';
import { ArchiveService } from './archive.service';
import { CreateMediaDto } from './dto/create-media.dto';
import { CreateProposalDto } from './dto/create-proposal.dto';
import { CreateSourceDto } from './dto/create-source.dto';
import { ReviewProposalDto } from './dto/review-proposal.dto';
import { UploadImageDto } from './dto/upload-image.dto';
import { MAX_IMAGE_UPLOAD_BYTES } from './image-processor';

@Controller()
export class ArchiveController {
  constructor(private readonly archiveService: ArchiveService) {}

  @Get('persons/:personId/sources')
  @SpaceRoles('OWNER', 'ADMIN', 'EDITOR', 'VIEWER')
  listSources(
    @Param('personId') personId: string,
    @Query('spaceId') spaceId: string,
  ) {
    this.validateIds(spaceId, personId);
    return this.archiveService.listSources(spaceId, personId);
  }

  @Post('persons/:personId/sources')
  @SpaceRoles('OWNER', 'ADMIN', 'EDITOR')
  createSource(
    @Param('personId') personId: string,
    @ActorUserId() actorUserId: string,
    @Body() dto: CreateSourceDto,
  ) {
    this.validateIds(dto.spaceId, personId);
    return this.archiveService.createSource(
      dto.spaceId,
      personId,
      dto,
      actorUserId,
    );
  }

  @Get('persons/:personId/media')
  @SpaceRoles('OWNER', 'ADMIN', 'EDITOR', 'VIEWER')
  listMedia(
    @Param('personId') personId: string,
    @Query('spaceId') spaceId: string,
  ) {
    this.validateIds(spaceId, personId);
    return this.archiveService.listMedia(spaceId, personId);
  }

  @Get('spaces/:spaceId/profile-photos')
  @SpaceRoles('OWNER', 'ADMIN', 'EDITOR', 'VIEWER')
  listProfilePhotos(@Param('spaceId') spaceId: string) {
    if (!spaceId || !isUUID(spaceId))
      throw new BadRequestException('Invalid spaceId');
    return this.archiveService.listProfilePhotos(spaceId);
  }

  @Post('persons/:personId/media')
  @SpaceRoles('OWNER', 'ADMIN', 'EDITOR')
  createMedia(
    @Param('personId') personId: string,
    @ActorUserId() actorUserId: string,
    @Body() dto: CreateMediaDto,
  ) {
    this.validateIds(dto.spaceId, personId);
    return this.archiveService.createMedia(
      dto.spaceId,
      personId,
      dto,
      actorUserId,
    );
  }

  @Post('persons/:personId/media/upload')
  @SpaceRoles('OWNER', 'ADMIN', 'EDITOR')
  @UseInterceptors(
    FileInterceptor('file', {
      limits: { files: 1, fileSize: MAX_IMAGE_UPLOAD_BYTES },
    }),
  )
  uploadImage(
    @Param('personId') personId: string,
    @ActorUserId() actorUserId: string,
    @Query('spaceId') spaceId: string,
    @Body() dto: UploadImageDto,
    @UploadedFile() file?: Express.Multer.File,
  ) {
    this.validateIds(spaceId, personId);
    if (!file) throw new BadRequestException('Image file is required');
    return this.archiveService.uploadImage(
      spaceId,
      personId,
      dto,
      file.buffer,
      actorUserId,
    );
  }

  @Get('persons/:personId/media/:mediaId/access')
  @SpaceRoles('OWNER', 'ADMIN', 'EDITOR', 'VIEWER')
  getMediaAccess(
    @Param('personId') personId: string,
    @Param('mediaId') mediaId: string,
    @Query('spaceId') spaceId: string,
  ) {
    this.validateIds(spaceId, personId);
    if (!isUUID(mediaId)) throw new BadRequestException('Invalid mediaId');
    return this.archiveService.getMediaAccess(spaceId, personId, mediaId);
  }

  @Get('proposals')
  @SpaceRoles('OWNER', 'ADMIN', 'EDITOR', 'VIEWER')
  listProposals(@Query('spaceId') spaceId: string) {
    if (!spaceId || !isUUID(spaceId))
      throw new BadRequestException('Invalid spaceId');
    return this.archiveService.listProposals(spaceId);
  }

  @Post('proposals')
  @SpaceRoles('OWNER', 'ADMIN', 'EDITOR', 'VIEWER')
  createProposal(
    @ActorUserId() actorUserId: string,
    @Body() dto: CreateProposalDto,
  ) {
    return this.archiveService.createProposal(dto, actorUserId);
  }

  @Post('proposals/approve')
  @SpaceRoles('OWNER', 'ADMIN')
  approveProposal(
    @ActorUserId() actorUserId: string,
    @Body() dto: ReviewProposalDto,
  ) {
    return this.archiveService.approveProposal(
      dto.spaceId,
      dto.proposalId,
      actorUserId,
    );
  }

  @Post('proposals/reject')
  @SpaceRoles('OWNER', 'ADMIN')
  rejectProposal(
    @ActorUserId() actorUserId: string,
    @Body() dto: ReviewProposalDto,
  ) {
    return this.archiveService.rejectProposal(
      dto.spaceId,
      dto.proposalId,
      actorUserId,
    );
  }

  private validateIds(spaceId: string, personId: string) {
    if (!spaceId || !isUUID(spaceId))
      throw new BadRequestException('Invalid spaceId');
    if (!personId || !isUUID(personId))
      throw new BadRequestException('Invalid personId');
  }
}
