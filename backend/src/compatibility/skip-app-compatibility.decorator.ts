import { SetMetadata } from '@nestjs/common';

export const SKIP_APP_COMPATIBILITY_KEY = 'skipAppCompatibility';
export const SkipAppCompatibility = () =>
  SetMetadata(SKIP_APP_COMPATIBILITY_KEY, true);
