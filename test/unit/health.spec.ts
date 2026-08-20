import { describe, expect, it, vi } from 'vitest';

import { GetLiveness } from '../../src/modules/health/application/get-liveness';
import { GetReadiness } from '../../src/modules/health/application/get-readiness';
import { TypeOrmReadinessProbe } from '../../src/modules/health/infrastructure/typeorm-readiness.probe';

describe('health use cases', () => {
  it('reports process liveness', () => {
    expect(new GetLiveness().execute()).toEqual({ status: 'ok' });
  });

  it.each([
    [true, { status: 'ok', checks: { database: 'up' } }],
    [false, { status: 'error', checks: { database: 'down' } }],
  ] as const)('maps database probe result %s', async (available, expected) => {
    const useCase = new GetReadiness({ check: vi.fn().mockResolvedValue(available) });
    await expect(useCase.execute()).resolves.toEqual(expected);
  });

  it('checks PostgreSQL with SELECT 1 and fails closed', async () => {
    const query = vi.fn().mockResolvedValue([{ '?column?': 1 }]);
    const probe = new TypeOrmReadinessProbe({ query } as never);
    await expect(probe.check()).resolves.toBe(true);
    expect(query).toHaveBeenCalledWith('SELECT 1');

    query.mockRejectedValueOnce(new Error('connection failed'));
    await expect(probe.check()).resolves.toBe(false);
  });
});
