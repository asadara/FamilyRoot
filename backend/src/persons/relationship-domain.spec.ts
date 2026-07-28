import { validate } from 'class-validator';
import { AddParentChildDto } from './dto/add-parent-child.dto';
import {
  isCareRelationshipMeta,
  isLineageParentChildMeta,
} from './relationship.entity';

describe('relationship domain', () => {
  it.each(['FOSTER', 'GUARDIAN'] as const)(
    'treats %s as care without lineage semantics',
    (meta) => {
      expect(isCareRelationshipMeta(meta)).toBe(true);
      expect(isLineageParentChildMeta(meta)).toBe(false);
    },
  );

  it.each(['BIOLOGICAL', 'ADOPTIVE', 'STEP'] as const)(
    'keeps %s in lineage semantics',
    (meta) => {
      expect(isCareRelationshipMeta(meta)).toBe(false);
      expect(isLineageParentChildMeta(meta)).toBe(true);
    },
  );

  it('accepts a dated guardian relationship with context', async () => {
    const dto = Object.assign(new AddParentChildDto(), {
      spaceId: '11111111-1111-4111-8111-111111111111',
      parentId: '22222222-2222-4222-8222-222222222222',
      childId: '33333333-3333-4333-8333-333333333333',
      meta: 'GUARDIAN',
      startDate: '2025-01-01',
      endDate: '2026-01-01',
      careContext: 'Pengasuhan keluarga sementara',
      clientMutationId: '44444444-4444-4444-8444-444444444444',
    });

    await expect(validate(dto)).resolves.toHaveLength(0);
  });

  it('rejects unsupported relationship metadata', async () => {
    const dto = Object.assign(new AddParentChildDto(), {
      spaceId: '11111111-1111-4111-8111-111111111111',
      parentId: '22222222-2222-4222-8222-222222222222',
      childId: '33333333-3333-4333-8333-333333333333',
      meta: 'DONOR',
      clientMutationId: '44444444-4444-4444-8444-444444444444',
    });

    expect(await validate(dto)).not.toHaveLength(0);
  });
});
