process.env.NODE_ENV = 'test';
process.env.DB_DATABASE = ':memory:';
process.env.JWT_SECRET = 'e2e-only-secret-that-is-never-used-in-production';
delete process.env.DATABASE_URL;
delete process.env.SUPABASE_URL;
delete process.env.SUPABASE_SECRET_KEY;
delete process.env.SUPABASE_SERVICE_ROLE_KEY;
