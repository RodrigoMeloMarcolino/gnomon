import { randomUUID } from 'node:crypto';

import { PostgreSqlContainer, type StartedPostgreSqlContainer } from '@testcontainers/postgresql';
import { DataSource, type EntityManager } from 'typeorm';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';

import { EnableBtreeGist1787097600000 } from '../../src/database/migrations/1787097600000-enable-btree-gist';
import { translatePostgresError } from '../../src/modules/shared/infrastructure/postgres-error';

interface BookingInput {
  id: string;
  tenantId: string;
  calendarId: string;
  idempotencyKey: string;
  payloadHash: string;
  startAt: string;
  endAt: string;
}

async function insertAppointment(manager: EntityManager, input: BookingInput): Promise<void> {
  await manager.query(
    `INSERT INTO spike_appointments
      (id, tenant_id, calendar_id, idempotency_key, payload_hash, status, start_at, end_at)
     VALUES ($1, $2, $3, $4, $5, 'scheduled', $6, $7)`,
    [
      input.id,
      input.tenantId,
      input.calendarId,
      input.idempotencyKey,
      input.payloadHash,
      input.startAt,
      input.endAt,
    ],
  );
}

async function bookWithReplay(
  dataSource: DataSource,
  input: BookingInput,
): Promise<'created' | 'replayed' | 'idempotency_conflict'> {
  return dataSource.transaction(async (manager) => {
    const existing = await manager.query<{ payload_hash: string }[]>(
      `SELECT payload_hash FROM spike_appointments
       WHERE tenant_id = $1 AND idempotency_key = $2`,
      [input.tenantId, input.idempotencyKey],
    );

    if (existing[0]) {
      return existing[0].payload_hash === input.payloadHash ? 'replayed' : 'idempotency_conflict';
    }

    await insertAppointment(manager, input);
    return 'created';
  });
}

describe('TypeORM PostgreSQL spike', () => {
  let container: StartedPostgreSqlContainer;
  let dataSource: DataSource;

  const tenantId = randomUUID();
  const otherTenantId = randomUUID();
  const calendarId = randomUUID();

  beforeAll(async () => {
    container = await new PostgreSqlContainer('postgres:17-alpine')
      .withDatabase('gnomon_test')
      .withUsername('gnomon')
      .withPassword('test-only-password')
      .start();

    dataSource = new DataSource({
      type: 'postgres',
      url: container.getConnectionUri(),
      synchronize: false,
      migrationsRun: false,
      migrationsTableName: 'schema_migrations',
      migrations: [EnableBtreeGist1787097600000],
      entities: [],
    });
    await dataSource.initialize();
    await dataSource.runMigrations({ transaction: 'each' });
  });

  afterAll(async () => {
    if (dataSource.isInitialized) {
      await dataSource.query('DROP TABLE IF EXISTS spike_appointments');
      await dataSource.undoLastMigration({ transaction: 'each' });
      await dataSource.destroy();
    }
    await container.stop();
  });

  it('applies the reviewed migration and leaves no ORM metadata drift', async () => {
    const extension = await dataSource.query<{ extname: string }[]>(
      "SELECT extname FROM pg_extension WHERE extname = 'btree_gist'",
    );
    expect(extension).toEqual([{ extname: 'btree_gist' }]);
    await expect(dataSource.showMigrations()).resolves.toBe(false);

    const drift = await dataSource.driver.createSchemaBuilder().log();
    expect(drift.upQueries).toHaveLength(0);
  });

  it('creates a SQL-only spike schema with independent named constraints', async () => {
    await dataSource.query(`
      CREATE TABLE spike_appointments (
        id uuid PRIMARY KEY,
        tenant_id uuid NOT NULL,
        calendar_id uuid NOT NULL,
        idempotency_key uuid NOT NULL,
        payload_hash text NOT NULL,
        status text NOT NULL,
        start_at timestamptz NOT NULL,
        end_at timestamptz NOT NULL,
        CONSTRAINT ck_spike_appointments_period CHECK (end_at > start_at),
        CONSTRAINT uq_spike_appointments_idempotency UNIQUE (tenant_id, idempotency_key)
      )
    `);
    await dataSource.query(`
      ALTER TABLE spike_appointments
      ADD CONSTRAINT ex_spike_appointments_period
      EXCLUDE USING gist (
        tenant_id WITH =,
        calendar_id WITH =,
        tstzrange(start_at, end_at, '[)') WITH &&
      ) WHERE (status = 'scheduled')
    `);

    const constraints = await dataSource.query<{ conname: string }[]>(`
      SELECT conname FROM pg_constraint
      WHERE conrelid = 'spike_appointments'::regclass
    `);
    expect(constraints.map(({ conname }) => conname)).toEqual(
      expect.arrayContaining(['uq_spike_appointments_idempotency', 'ex_spike_appointments_period']),
    );
  });

  it('handles create, replay and payload mismatch inside short transactions', async () => {
    const input: BookingInput = {
      id: randomUUID(),
      tenantId,
      calendarId,
      idempotencyKey: randomUUID(),
      payloadHash: 'hash-a',
      startAt: '2030-01-01T09:00:00Z',
      endAt: '2030-01-01T10:00:00Z',
    };

    await expect(bookWithReplay(dataSource, input)).resolves.toBe('created');
    await expect(bookWithReplay(dataSource, input)).resolves.toBe('replayed');
    await expect(bookWithReplay(dataSource, { ...input, payloadHash: 'hash-b' })).resolves.toBe(
      'idempotency_conflict',
    );
  });

  it('allows adjacent intervals and rejects concurrent overlaps with 23P01', async () => {
    const base = {
      tenantId,
      calendarId,
      payloadHash: 'hash',
    };
    await bookWithReplay(dataSource, {
      ...base,
      id: randomUUID(),
      idempotencyKey: randomUUID(),
      startAt: '2030-01-01T10:00:00Z',
      endAt: '2030-01-01T11:00:00Z',
    });

    const overlapping = [
      {
        ...base,
        id: randomUUID(),
        idempotencyKey: randomUUID(),
        startAt: '2030-01-01T12:00:00Z',
        endAt: '2030-01-01T13:00:00Z',
      },
      {
        ...base,
        id: randomUUID(),
        idempotencyKey: randomUUID(),
        startAt: '2030-01-01T12:30:00Z',
        endAt: '2030-01-01T13:30:00Z',
      },
    ];
    const results = await Promise.allSettled(
      overlapping.map((input) =>
        dataSource.transaction((manager) => insertAppointment(manager, input)),
      ),
    );
    expect(results.filter((result) => result.status === 'fulfilled')).toHaveLength(1);
    const rejected = results.find((result) => result.status === 'rejected');
    expect(rejected?.status).toBe('rejected');
    if (rejected?.status === 'rejected') {
      expect(translatePostgresError(rejected.reason)).toEqual({
        kind: 'exclusion_violation',
        code: '23P01',
        constraint: 'ex_spike_appointments_period',
      });
    }
  });

  it('keeps unique violations distinct from temporal conflicts', async () => {
    const key = randomUUID();
    const first: BookingInput = {
      id: randomUUID(),
      tenantId,
      calendarId,
      idempotencyKey: key,
      payloadHash: 'hash',
      startAt: '2030-01-02T09:00:00Z',
      endAt: '2030-01-02T10:00:00Z',
    };
    await dataSource.transaction((manager) => insertAppointment(manager, first));

    try {
      await dataSource.transaction((manager) =>
        insertAppointment(manager, {
          ...first,
          id: randomUUID(),
          startAt: '2030-01-02T10:00:00Z',
          endAt: '2030-01-02T11:00:00Z',
        }),
      );
      throw new Error('expected unique violation');
    } catch (error) {
      expect(translatePostgresError(error)).toEqual({
        kind: 'unique_violation',
        code: '23505',
        constraint: 'uq_spike_appointments_idempotency',
      });
    }
  });

  it('paginates ranges with an explicit tenant scope', async () => {
    for (const [index, currentTenantId] of [tenantId, tenantId, otherTenantId].entries()) {
      const hour = 9 + index;
      await dataSource.transaction((manager) =>
        insertAppointment(manager, {
          id: randomUUID(),
          tenantId: currentTenantId,
          calendarId,
          idempotencyKey: randomUUID(),
          payloadHash: 'hash',
          startAt: `2030-02-03T${String(hour).padStart(2, '0')}:00:00Z`,
          endAt: `2030-02-03T${String(hour + 1).padStart(2, '0')}:00:00Z`,
        }),
      );
    }

    const rows = await dataSource.query<{ tenant_id: string }[]>(
      `SELECT tenant_id FROM spike_appointments
       WHERE tenant_id = $1
       ORDER BY start_at, id
       LIMIT $2 OFFSET $3`,
      [tenantId, 2, 0],
    );
    expect(rows).toHaveLength(2);
    expect(rows.every((row) => row.tenant_id === tenantId)).toBe(true);
  });
});
