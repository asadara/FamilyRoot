import { TypeOrmModuleOptions } from '@nestjs/typeorm';
import { postgresUrlWithRequiredSsl } from './environment';

export function createDatabaseOptions(): TypeOrmModuleOptions {
  const databaseUrl = process.env.DATABASE_URL;

  if (databaseUrl) {
    return {
      type: 'postgres',
      url: postgresUrlWithRequiredSsl(databaseUrl),
      autoLoadEntities: true,
      synchronize: false,
      migrationsRun: false,
      migrationsTableName: 'familyroot_migrations',
      extra: {
        max: Number(process.env.DB_POOL_MAX ?? 5),
        connectionTimeoutMillis: 10_000,
        idleTimeoutMillis: 30_000,
      },
    };
  }

  if (process.env.NODE_ENV === 'production') {
    throw new Error('DATABASE_URL is required in production');
  }

  return {
    type: 'sqlite',
    database: process.env.DB_DATABASE ?? 'dev.sqlite',
    autoLoadEntities: true,
    synchronize: true,
  };
}
