const MINIMUM_PRODUCTION_SECRET_LENGTH = 32;

function readPositiveInteger(
  value: string | undefined,
  fallback: number,
  name: string,
): number {
  if (value === undefined || value.trim() === '') {
    return fallback;
  }

  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed <= 0) {
    throw new Error(`${name} must be a positive integer`);
  }

  return parsed;
}

export function postgresUrlWithRequiredSsl(rawUrl: string): string {
  let databaseUrl: URL;
  try {
    databaseUrl = new URL(rawUrl);
  } catch {
    throw new Error('DATABASE_URL must be a valid PostgreSQL URL');
  }

  if (!['postgres:', 'postgresql:'].includes(databaseUrl.protocol)) {
    throw new Error(
      'DATABASE_URL must use the postgres:// or postgresql:// scheme',
    );
  }

  databaseUrl.searchParams.set('sslmode', 'require');
  databaseUrl.searchParams.set('uselibpqcompat', 'true');
  return databaseUrl.toString();
}

export function normalizedSupabaseProjectUrl(rawUrl: string): string {
  let projectUrl: URL;
  try {
    projectUrl = new URL(rawUrl);
  } catch {
    throw new Error('SUPABASE_URL must be a valid HTTPS project URL');
  }

  if (
    projectUrl.protocol !== 'https:' ||
    !projectUrl.hostname.endsWith('.supabase.co')
  ) {
    throw new Error('SUPABASE_URL must be an HTTPS *.supabase.co project URL');
  }
  return projectUrl.origin;
}

export function validateEnvironment(
  raw: Record<string, unknown>,
): Record<string, unknown> {
  const environment = { ...raw } as Record<string, string | undefined>;
  const isProduction = environment.NODE_ENV === 'production';

  if (environment.DATABASE_URL) {
    environment.DATABASE_URL = postgresUrlWithRequiredSsl(
      environment.DATABASE_URL,
    );
  } else if (isProduction) {
    throw new Error('DATABASE_URL is required in production');
  }

  if (environment.SUPABASE_URL) {
    environment.SUPABASE_URL = normalizedSupabaseProjectUrl(
      environment.SUPABASE_URL,
    );
  }

  const poolMax = readPositiveInteger(
    environment.DB_POOL_MAX,
    5,
    'DB_POOL_MAX',
  );
  if (poolMax > 10) {
    throw new Error('DB_POOL_MAX must not exceed 10 on the free-tier profile');
  }
  environment.DB_POOL_MAX = String(poolMax);

  if (isProduction) {
    if (
      !environment.JWT_SECRET ||
      environment.JWT_SECRET.length < MINIMUM_PRODUCTION_SECRET_LENGTH
    ) {
      throw new Error(
        `JWT_SECRET must contain at least ${MINIMUM_PRODUCTION_SECRET_LENGTH} characters in production`,
      );
    }

    const origins = (environment.CORS_ORIGINS ?? '')
      .split(',')
      .map((origin) => origin.trim())
      .filter(Boolean);
    if (origins.includes('*')) {
      throw new Error('CORS_ORIGINS must not contain * in production');
    }

    if (!environment.SUPABASE_URL) {
      throw new Error('SUPABASE_URL is required in production');
    }
    if (
      !environment.SUPABASE_SECRET_KEY &&
      !environment.SUPABASE_SERVICE_ROLE_KEY
    ) {
      throw new Error('SUPABASE_SECRET_KEY is required in production');
    }
  }

  environment.SUPABASE_STORAGE_BUCKET ??= 'family-media';

  return environment;
}
