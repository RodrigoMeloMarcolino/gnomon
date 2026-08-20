import { Body, Controller, Get, Module, Post } from '@nestjs/common';
import type { INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { IsString } from 'class-validator';
import request from 'supertest';
import { afterAll, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

import { ConfigModule } from '../../src/config/config.module';
import { configureApplication } from '../../src/http/configure-application';
import { LoggingModule } from '../../src/logging/logging.module';
import { HealthModule, READINESS_PROBE } from '../../src/modules/health/health.module';

class InputDto {
  @IsString()
  name!: string;
}

@Controller('test')
class TestController {
  @Post('validation')
  validate(@Body() input: InputDto): InputDto {
    return input;
  }

  @Get('boom')
  boom(): never {
    throw new Error('database failed with private-value');
  }
}

const readinessProbe = { check: vi.fn<() => Promise<boolean>>() };
const errorLogger = { error: vi.fn() };

@Module({
  imports: [
    ConfigModule,
    LoggingModule,
    HealthModule.register({ provide: READINESS_PROBE, useValue: readinessProbe }),
  ],
  controllers: [TestController],
})
class TestAppModule {}

describe('HTTP foundation', () => {
  let app: INestApplication;

  beforeAll(async () => {
    process.env.NODE_ENV = 'test';
    process.env.DATABASE_URL = 'postgres://gnomon:secret@localhost:5432/gnomon';
    process.env.CORS_ORIGINS = 'http://localhost:3000';

    const moduleRef = await Test.createTestingModule({ imports: [TestAppModule] }).compile();
    app = moduleRef.createNestApplication({ logger: false });
    configureApplication(app, { errorLogger });
    await app.init();
  });

  beforeEach(() => {
    readinessProbe.check.mockReset().mockResolvedValue(true);
    errorLogger.error.mockReset();
  });

  afterAll(async () => {
    await app.close();
  });

  it('publishes liveness and readiness under /v1', async () => {
    await request(app.getHttpServer()).get('/v1/health').expect(200, { status: 'ok' });
    await request(app.getHttpServer())
      .get('/v1/ready')
      .expect(200, {
        status: 'ok',
        checks: { database: 'up' },
      });
  });

  it('returns a canonical 503 envelope when PostgreSQL is unavailable', async () => {
    readinessProbe.check.mockResolvedValue(false);
    await request(app.getHttpServer())
      .get('/v1/ready')
      .expect(503, {
        error: {
          code: 'service_unavailable',
          message: 'Service unavailable',
          details: { checks: { database: 'down' } },
        },
      });
  });

  it('normalizes validation and not-found errors', async () => {
    const invalid = await request(app.getHttpServer())
      .post('/v1/test/validation')
      .send({ unknown: true })
      .expect(400);
    expect(invalid.body).toMatchObject({
      error: { code: 'validation_error', message: 'Request validation failed' },
    });

    await request(app.getHttpServer())
      .get('/v1/missing')
      .expect(404, {
        error: { code: 'not_found', message: 'Resource not found', details: null },
      });
  });

  it('does not leak unexpected errors to clients', async () => {
    const response = await request(app.getHttpServer()).get('/v1/test/boom').expect(500);
    expect(response.body).toEqual({
      error: {
        code: 'internal_error',
        message: 'An unexpected error occurred',
        details: null,
      },
    });
    expect(JSON.stringify(response.body)).not.toContain('private-value');
    expect(errorLogger.error).toHaveBeenCalledOnce();
  });

  it('echoes valid request ids and replaces unsafe values', async () => {
    const requestId = 'a0ebcf25-8338-4f95-9a80-4d9a85e74f9a';
    await request(app.getHttpServer())
      .get('/v1/health')
      .set('x-request-id', requestId)
      .expect('x-request-id', requestId);

    const response = await request(app.getHttpServer())
      .get('/v1/health')
      .set('x-request-id', 'unsafe');
    expect(response.headers['x-request-id']).toMatch(/^[0-9a-f-]{36}$/);
  });

  it('applies the CORS allowlist', async () => {
    await request(app.getHttpServer())
      .options('/v1/health')
      .set('origin', 'http://localhost:3000')
      .set('access-control-request-method', 'GET')
      .expect('access-control-allow-origin', 'http://localhost:3000');

    const denied = await request(app.getHttpServer())
      .options('/v1/health')
      .set('origin', 'https://attacker.example')
      .set('access-control-request-method', 'GET');
    expect(denied.headers['access-control-allow-origin']).toBeUndefined();
  });

  it('publishes OpenAPI as JSON and YAML', async () => {
    const json = await request(app.getHttpServer()).get('/v3/api-docs').expect(200);
    expect(json.body.paths).toHaveProperty('/v1/health');
    expect(json.body.paths).toHaveProperty('/v1/ready');

    const yaml = await request(app.getHttpServer()).get('/v3/api-docs.yaml').expect(200);
    expect(yaml.text).toContain('openapi: 3.0.0');
    expect(yaml.text).toContain('/v1/health:');
  });
});
