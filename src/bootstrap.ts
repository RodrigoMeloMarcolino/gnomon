import type { INestApplication } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { NestFactory } from '@nestjs/core';
import { Logger, PinoLogger } from 'nestjs-pino';

import { AppModule } from './app.module';
import type { Environment } from './config/environment';
import { configureApplication } from './http/configure-application';

export async function createApplication(): Promise<INestApplication> {
  const app = await NestFactory.create(AppModule, { bufferLogs: true });
  app.useLogger(app.get(Logger));
  const errorLogger = await app.resolve(PinoLogger);
  errorLogger.setContext('ApiExceptionFilter');
  configureApplication(app, { errorLogger });
  return app;
}

export async function bootstrap(): Promise<void> {
  const app = await createApplication();
  const config = app.get(ConfigService<Environment, true>);
  await app.listen(config.get('PORT', { infer: true }), config.get('HOST', { infer: true }));
}
