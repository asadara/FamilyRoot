import { BadRequestException, ForbiddenException } from '@nestjs/common';
import { ChangesService } from './changes.service';

describe('ChangesService', () => {
  const changesRepo = { find: jest.fn() };
  const usersRepo = { find: jest.fn() };
  const membersRepo = { findOneBy: jest.fn() };
  const historyAccessRepo = {
    findOneBy: jest.fn(),
    find: jest.fn(),
    create: jest.fn(),
    save: jest.fn(),
  };
  const service = new ChangesService(
    changesRepo as never,
    usersRepo as never,
    membersRepo as never,
    historyAccessRepo as never,
  );

  beforeEach(() => jest.clearAllMocks());

  it('limits the default feed and resolves actor display names', async () => {
    changesRepo.find.mockResolvedValue([
      {
        changeId: 'change-1',
        actorUserId: 'user-1',
        createdAt: new Date('2026-07-30T10:00:00Z'),
        entityType: 'PERSON',
        operation: 'UPDATE',
        note: null,
      },
    ]);
    usersRepo.find.mockResolvedValue([
      { userId: 'user-1', displayName: 'Budi Santoso' },
    ]);

    const result = await service.findBySpace('space-1');

    expect(changesRepo.find).toHaveBeenCalledWith(
      expect.objectContaining({ take: 10 }),
    );
    expect(result[0]).toEqual(
      expect.objectContaining({ actorDisplayName: 'Budi Santoso' }),
    );
  });

  it('clamps legacy limits while rejecting abusive limits', async () => {
    changesRepo.find.mockResolvedValue([]);
    usersRepo.find.mockResolvedValue([]);

    await service.findBySpace('space-1', 50);
    expect(changesRepo.find).toHaveBeenCalledWith(
      expect.objectContaining({ take: 10 }),
    );
    await expect(service.findBySpace('space-1', 101)).rejects.toBeInstanceOf(
      BadRequestException,
    );
  });

  it('requires approval before a regular member can page full history', async () => {
    membersRepo.findOneBy.mockResolvedValue({ role: 'EDITOR' });
    historyAccessRepo.findOneBy.mockResolvedValue(null);

    await expect(
      service.findFullHistory('space-1', 'user-1'),
    ).rejects.toBeInstanceOf(ForbiddenException);
  });
});
