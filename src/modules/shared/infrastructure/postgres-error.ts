import { QueryFailedError } from 'typeorm';

export type PostgresConstraintViolation =
  | { kind: 'unique_violation'; code: '23505'; constraint?: string }
  | { kind: 'exclusion_violation'; code: '23P01'; constraint?: string }
  | { kind: 'unknown'; code?: string; constraint?: string };

const SAFE_CONSTRAINT = /^[a-z][a-z0-9_]{0,62}$/;

function safeString(value: unknown): string | undefined {
  return typeof value === 'string' && SAFE_CONSTRAINT.test(value) ? value : undefined;
}

export function translatePostgresError(error: unknown): PostgresConstraintViolation {
  const driverError =
    error instanceof QueryFailedError
      ? (error.driverError as Record<string, unknown>)
      : error && typeof error === 'object'
        ? (error as Record<string, unknown>)
        : {};

  const code = typeof driverError.code === 'string' ? driverError.code : undefined;
  const constraint = safeString(driverError.constraint);

  if (code === '23505') {
    return { kind: 'unique_violation', code, constraint };
  }
  if (code === '23P01') {
    return { kind: 'exclusion_violation', code, constraint };
  }
  return { kind: 'unknown', code, constraint };
}
