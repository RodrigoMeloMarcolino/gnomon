import { join } from 'node:path';

import type { TypeOrmModuleOptions } from '@nestjs/typeorm';
import type { DataSourceOptions } from 'typeorm';

import type { Environment } from '../config/environment';

type DatabaseEnvironment = Pick<
  Environment,
  'DATABASE_URL' | 'DATABASE_SSL_MODE' | 'DATABASE_POOL_MAX'
>;

function sslOptions(
  mode: Environment['DATABASE_SSL_MODE'],
): false | { rejectUnauthorized: boolean } {
  if (mode === 'disable') {
    return false;
  }

  return { rejectUnauthorized: mode === 'verify-full' };
}

export function createDataSourceOptions(environment: DatabaseEnvironment): DataSourceOptions {
  return {
    type: 'postgres',
    url: environment.DATABASE_URL,
    ssl: sslOptions(environment.DATABASE_SSL_MODE),
    poolSize: environment.DATABASE_POOL_MAX,
    entities: [join(__dirname, '../modules/**/infrastructure/**/*.entity.{js,ts}')],
    migrations: [join(__dirname, 'migrations/*.{js,ts}')],
    migrationsTableName: 'schema_migrations',
    migrationsRun: false,
    migrationsTransactionMode: 'each',
    synchronize: false,
    logging: false,
  };
}

export function createNestTypeOrmOptions(environment: DatabaseEnvironment): TypeOrmModuleOptions {
  return {
    ...createDataSourceOptions(environment),
    retryAttempts: 5,
    retryDelay: 1_000,
  };
}
