import { SetMetadata } from '@nestjs/common';

export const ALLOW_ARCHIVED_SPACE_MUTATION_KEY = 'allowArchivedSpaceMutation';

export const AllowArchivedSpaceMutation = () =>
  SetMetadata(ALLOW_ARCHIVED_SPACE_MUTATION_KEY, true);
