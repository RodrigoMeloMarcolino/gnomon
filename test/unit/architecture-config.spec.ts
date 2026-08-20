import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { describe, expect, it } from 'vitest';

describe('architecture gate configuration', () => {
  it('enforces cycles and every hexagonal layer boundary', () => {
    const configuration = readFileSync(resolve(process.cwd(), '.dependency-cruiser.cjs'), 'utf8');
    for (const rule of [
      'no-circular',
      'domain-is-pure',
      'domain-does-not-depend-on-outer-layers',
      'application-does-not-depend-on-api-or-infrastructure',
      'api-does-not-depend-on-infrastructure',
      'infrastructure-does-not-depend-on-api-or-application',
    ]) {
      expect(configuration).toContain(`name: '${rule}'`);
    }
    expect(configuration).toContain('circular: true');
  });
});
