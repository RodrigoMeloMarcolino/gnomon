import { describe, expect, it } from 'vitest';

import {
  createDataSourceOptions,
  createNestTypeOrmOptions,
} from '../../src/database/typeorm-options';

const baseEnvironment = {
  DATABASE_URL: 'postgres://gnomon:secret@localhost:5432/gnomon',
  DATABASE_SSL_MODE: 'disable' as const,
  DATABASE_POOL_MAX: 7,
};

describe('TypeORM options', () => {
  it('never enables schema synchronization or automatic migrations', () => {
    expect(createDataSourceOptions(baseEnvironment)).toMatchObject({
      type: 'postgres',
      synchronize: false,
      migrationsRun: false,
      migrationsTableName: 'schema_migrations',
      poolSize: 7,
      ssl: false,
    });
  });

  it('requires certificate validation only in verify-full mode', () => {
    const required = createNestTypeOrmOptions({
      ...baseEnvironment,
      DATABASE_SSL_MODE: 'require',
    }) as { ssl?: unknown };
    const verified = createNestTypeOrmOptions({
      ...baseEnvironment,
      DATABASE_SSL_MODE: 'verify-full',
    }) as { ssl?: unknown };
    expect(required.ssl).toEqual({ rejectUnauthorized: false });
    expect(verified.ssl).toEqual({ rejectUnauthorized: true });
  });
});
