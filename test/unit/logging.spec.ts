import pino from 'pino';
import { describe, expect, it } from 'vitest';

import { LOG_REDACTION, serializeRequest } from '../../src/logging/logging.module';
import { resolveRequestId } from '../../src/logging/request-id';

describe('safe logging', () => {
  it('keeps only request id and method in request serialization', () => {
    expect(
      serializeRequest({
        id: 'request-id',
        method: 'POST',
        headers: { authorization: 'Bearer secret' },
        body: { phone: '+5585000000000' },
      } as never),
    ).toEqual({ id: 'request-id', method: 'POST' });
  });

  it('redacts sensitive nested values', () => {
    let output = '';
    const logger = pino(
      { redact: LOG_REDACTION },
      {
        write: (line: string) => {
          output += line;
        },
      },
    );
    logger.info({
      req: { headers: { authorization: 'Bearer secret' }, body: { phone: 'private' } },
      context: { idempotencyKey: 'private-key' },
    });

    expect(output).not.toContain('Bearer secret');
    expect(output).not.toContain('private-key');
    expect(output).not.toContain('private');
    expect(output).toContain('[Redacted]');
  });

  it('accepts only UUID request ids', () => {
    const valid = 'A0EBCF25-8338-4F95-9A80-4D9A85E74F9A';
    expect(resolveRequestId(valid)).toBe(valid.toLowerCase());
    expect(resolveRequestId('attacker-controlled')).toMatch(/^[0-9a-f-]{36}$/);
  });
});
