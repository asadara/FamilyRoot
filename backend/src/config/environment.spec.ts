import { postgresUrlWithRequiredSsl, validateEnvironment } from './environment';
import { normalizedSupabaseProjectUrl } from './environment';

describe('environment validation', () => {
  it('adds sslmode=require to a PostgreSQL URL', () => {
    const result = postgresUrlWithRequiredSsl(
      'postgresql://user:password@db.example.test:5432/postgres',
    );

    expect(new URL(result).searchParams.get('sslmode')).toBe('require');
    expect(new URL(result).searchParams.get('uselibpqcompat')).toBe('true');
  });

  it('normalizes a copied Supabase REST URL to the project origin', () => {
    expect(
      normalizedSupabaseProjectUrl(
        'https://example-project.supabase.co/rest/v1/',
      ),
    ).toBe('https://example-project.supabase.co');
  });

  it('rejects production without a database URL', () => {
    expect(() =>
      validateEnvironment({
        NODE_ENV: 'production',
        JWT_SECRET: 'x'.repeat(32),
      }),
    ).toThrow('DATABASE_URL is required in production');
  });

  it('rejects an unsafe production JWT secret', () => {
    expect(() =>
      validateEnvironment({
        NODE_ENV: 'production',
        DATABASE_URL: 'postgresql://user:password@db.example.test/postgres',
        JWT_SECRET: 'too-short',
      }),
    ).toThrow('JWT_SECRET must contain at least 32 characters');
  });

  it('rejects a wildcard production CORS origin', () => {
    expect(() =>
      validateEnvironment({
        NODE_ENV: 'production',
        DATABASE_URL: 'postgresql://user:password@db.example.test/postgres',
        JWT_SECRET: 'x'.repeat(32),
        CORS_ORIGINS: '*',
      }),
    ).toThrow('CORS_ORIGINS must not contain * in production');
  });

  it('caps the initial free-tier pool size', () => {
    expect(() =>
      validateEnvironment({ NODE_ENV: 'development', DB_POOL_MAX: '11' }),
    ).toThrow('DB_POOL_MAX must not exceed 10');
  });

  it('rejects malformed Google OAuth client IDs', () => {
    expect(() =>
      validateEnvironment({
        NODE_ENV: 'development',
        GOOGLE_OAUTH_CLIENT_ID: 'not-a-google-client',
      }),
    ).toThrow('GOOGLE_OAUTH_CLIENT_ID must be a Google Web client ID');
  });

  it('requires server-side storage credentials in production', () => {
    expect(() =>
      validateEnvironment({
        NODE_ENV: 'production',
        DATABASE_URL: 'postgresql://user:password@db.example.test/postgres',
        JWT_SECRET: 'x'.repeat(32),
      }),
    ).toThrow('SUPABASE_URL is required in production');
  });
});
