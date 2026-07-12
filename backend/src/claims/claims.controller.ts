import { Body, Controller, Post } from '@nestjs/common';
import { ClaimsService } from './claims.service';
import { CreateClaimDto } from './dto/create-claim.dto';
import { VerifyClaimDto } from './dto/verify-claim.dto';
import { ActorUserId } from '../common/actor-user-id.decorator';
import { SpaceRoles } from '../common/space-roles.decorator';

@Controller('claims')
export class ClaimsController {
  constructor(private readonly claimsService: ClaimsService) {}

  @Post()
  @SpaceRoles('OWNER', 'ADMIN', 'EDITOR', 'VIEWER')
  create(@ActorUserId() actorUserId: string, @Body() dto: CreateClaimDto) {
    return this.claimsService.create(
      dto.spaceId,
      actorUserId,
      dto.personId,
      actorUserId,
    );
  }

  @Post('verify')
  @SpaceRoles('OWNER', 'ADMIN')
  verify(@ActorUserId() actorUserId: string, @Body() dto: VerifyClaimDto) {
    return this.claimsService.verify(dto.claimId, actorUserId);
  }
}
