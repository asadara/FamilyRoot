import { BadRequestException, Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { ChangeLogEntity } from './change-log.entity';

@Injectable()
export class ChangesService {
  constructor(
    @InjectRepository(ChangeLogEntity)
    private readonly changesRepo: Repository<ChangeLogEntity>,
  ) {}

  findBySpace(spaceId: string, limit = 50) {
    if (!Number.isInteger(limit) || limit <= 0) {
      throw new BadRequestException('Invalid limit');
    }

    return this.changesRepo.find({
      where: { spaceId },
      order: { createdAt: 'DESC' },
      take: limit,
      select: [
        'changeId',
        'createdAt',
        'actorUserId',
        'entityType',
        'operation',
        'note',
      ],
    });
  }
}
