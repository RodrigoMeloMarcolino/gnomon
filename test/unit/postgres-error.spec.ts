import { describe, expect, it } from 'vitest';

import { translatePostgresError } from '../../src/modules/shared/infrastructure/postgres-error';

describe('translatePostgresError', () => {
  it('distinguishes unique and exclusion constraints', () => {
    expect(
      translatePostgresError({ code: '23505', constraint: 'uq_appointments_idempotency' }),
    ).toEqual({
      kind: 'unique_violation',
      code: '23505',
      constraint: 'uq_appointments_idempotency',
    });
    expect(translatePostgresError({ code: '23P01', constraint: 'ex_appointments_period' })).toEqual(
      {
        kind: 'exclusion_violation',
        code: '23P01',
        constraint: 'ex_appointments_period',
      },
    );
  });

  it('drops unsafe constraint metadata', () => {
    expect(translatePostgresError({ code: 'XX000', constraint: 'secret; DROP TABLE x' })).toEqual({
      kind: 'unknown',
      code: 'XX000',
      constraint: undefined,
    });
    expect(translatePostgresError(new Error('not a database error'))).toEqual({
      kind: 'unknown',
      code: undefined,
      constraint: undefined,
    });
  });
});
