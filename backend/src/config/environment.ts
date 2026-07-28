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

  if (
    environment.GOOGLE_OAUTH_CLIENT_ID &&
    !/^[0-9]+-[a-z0-9-]+\.apps\.googleusercontent\.com$/i.test(
      environment.GOOGLE_OAUTH_CLIENT_ID.trim(),
    )
  ) {
    throw new Error('GOOGLE_OAUTH_CLIENT_ID must be a Google Web client ID');
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

  const systemAdminUserIds = (environment.SYSTEM_ADMIN_USER_IDS ?? '')
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean);
  if (
    systemAdminUserIds.some(
      (value) =>
        !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(
          value,
        ),
    )
  ) {
    throw new Error('SYSTEM_ADMIN_USER_IDS must contain comma-separated UUIDs');
  }
  environment.SYSTEM_ADMIN_USER_IDS = systemAdminUserIds.join(',');

  const apiContractVersion = readPositiveInteger(
    environment.ANDROID_API_CONTRACT_VERSION,
    1,
    'ANDROID_API_CONTRACT_VERSION',
  );
  environment.ANDROID_API_CONTRACT_VERSION = String(apiContractVersion);
  const acceptedReleaseChannels = (
    environment.ANDROID_ACCEPTED_RELEASE_CHANNELS ?? 'DEBUG,PILOT,PRODUCTION'
  )
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean);
  if (
    acceptedReleaseChannels.length === 0 ||
    acceptedReleaseChannels.some(
      (value) => !['DEBUG', 'PILOT', 'PRODUCTION'].includes(value),
    )
  ) {
    throw new Error(
      'ANDROID_ACCEPTED_RELEASE_CHANNELS must contain DEBUG, PILOT, and/or PRODUCTION',
    );
  }
  environment.ANDROID_ACCEPTED_RELEASE_CHANNELS =
    acceptedReleaseChannels.join(',');
  for (const channel of ['DEBUG', 'PILOT', 'PRODUCTION']) {
    const minimumKey = `ANDROID_${channel}_MIN_SUPPORTED_VERSION_CODE`;
    const latestKey = `ANDROID_${channel}_LATEST_VERSION_CODE`;
    const updateUrlKey = `ANDROID_${channel}_UPDATE_URL`;
    const enforcementKey = `ANDROID_${channel}_ENFORCEMENT_ENABLED`;
    const minimum = readPositiveInteger(environment[minimumKey], 1, minimumKey);
    const latest = readPositiveInteger(
      environment[latestKey],
      minimum,
      latestKey,
    );
    if (latest < minimum) {
      throw new Error(
        `${latestKey} must be greater than or equal to ${minimumKey}`,
      );
    }
    const updateUrl = environment[updateUrlKey]?.trim();
    if (updateUrl) {
      let parsed: URL;
      try {
        parsed = new URL(updateUrl);
      } catch {
        throw new Error(`${updateUrlKey} must be a valid HTTPS URL`);
      }
      if (parsed.protocol !== 'https:') {
        throw new Error(`${updateUrlKey} must be a valid HTTPS URL`);
      }
      environment[updateUrlKey] = parsed.toString();
    }
    const enforcement = environment[enforcementKey]?.trim().toLowerCase();
    if (enforcement && !['true', 'false'].includes(enforcement)) {
      throw new Error(`${enforcementKey} must be true or false`);
    }
    environment[enforcementKey] = enforcement ?? 'false';
    environment[minimumKey] = String(minimum);
    environment[latestKey] = String(latest);
  }

  return environment;
}
