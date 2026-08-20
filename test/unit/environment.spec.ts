import { describe, expect, it } from 'vitest';

import { validateEnvironment } from '../../src/config/environment';

describe('validateEnvironment', () => {
  it('applies safe development defaults and parses typed values', () => {
    const result = validateEnvironment({
      DATABASE_URL: 'postgres://gnomon:secret@localhost:5432/gnomon',
      PORT: '9090',
      CORS_ORIGINS: 'http://localhost:3000,https://umbra.example.com',
    });

    expect(result).toMatchObject({
      NODE_ENV: 'development',
      HOST: '0.0.0.0',
      PORT: 9090,
      DATABASE_SSL_MODE: 'disable',
      DATABASE_POOL_MAX: 10,
      CORS_ORIGINS: ['http://localhost:3000', 'https://umbra.example.com'],
    });
  });

  it('fails fast without exposing values', () => {
    expect(() =>
      validateEnvironment({
        CORS_ORIGINS: 'http://localhost:3000',
        DATABASE_URL: 'not-a-url',
        PORT: '0',
      }),
    ).toThrow('Invalid environment configuration: PORT, DATABASE_URL');
  });
});
