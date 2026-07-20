import 'dotenv/config';
import { DataSource, DataSourceOptions } from 'typeorm';
import { postgresUrlWithRequiredSsl } from '../config/environment';

function createDataSourceOptions(): DataSourceOptions {
  if (process.env.DATABASE_URL) {
    return {
      type: 'postgres',
      url: postgresUrlWithRequiredSsl(process.env.DATABASE_URL),
      entities: [`${__dirname}/../**/*.entity{.ts,.js}`],
      migrations: [`${__dirname}/migrations/*{.ts,.js}`],
      migrationsTableName: 'familyroot_migrations',
      synchronize: false,
      extra: {
        max: Number(process.env.DB_POOL_MAX ?? 5),
        connectionTimeoutMillis: 10_000,
        idleTimeoutMillis: 30_000,
      },
    };
  }

  throw new Error('DATABASE_URL is required to run PostgreSQL migrations');
}

export default new DataSource(createDataSourceOptions());
