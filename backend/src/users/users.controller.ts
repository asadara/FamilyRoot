import { Body, Controller, Delete, Get } from '@nestjs/common';
import { ActorUserId } from '../common/actor-user-id.decorator';
import { DeleteAccountDto } from './dto/delete-account.dto';
import { UsersService } from './users.service';

@Controller('users')
export class UsersController {
  constructor(private readonly usersService: UsersService) {}

  @Get('health')
  health() {
    return { ok: true };
  }

  @Get('me/deletion-impact')
  deletionImpact(@ActorUserId() actorUserId: string) {
    return this.usersService.accountDeletionImpact(actorUserId);
  }

  @Delete('me')
  deleteAccount(
    @ActorUserId() actorUserId: string,
    @Body() dto: DeleteAccountDto,
  ) {
    return this.usersService.deleteAccount(actorUserId, dto.confirmation);
  }
}
