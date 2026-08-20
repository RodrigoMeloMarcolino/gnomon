import { ValidationPipe } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import type { INestApplication } from '@nestjs/common';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';

import type { Environment } from '../config/environment';
import { ApiExceptionFilter } from '../modules/shared/api/api-exception.filter';
import type { ErrorLogger } from '../modules/shared/api/api-exception.filter';
import { TelemetryInterceptor } from '../telemetry/telemetry.interceptor';

export interface ConfigureApplicationOptions {
  errorLogger: ErrorLogger;
}

export function configureApplication(
  app: INestApplication,
  options: ConfigureApplicationOptions,
): void {
  const config = app.get(ConfigService<Environment, true>);
  const environment = config.get('NODE_ENV', { infer: true });

  app.enableShutdownHooks();
  app.enableCors({
    credentials: true,
    origin: config.get('CORS_ORIGINS', { infer: true }),
  });
  app.setGlobalPrefix('v1');
  app.useGlobalFilters(new ApiExceptionFilter(options.errorLogger));
  app.useGlobalInterceptors(new TelemetryInterceptor());
  app.useGlobalPipes(
    new ValidationPipe({
      forbidNonWhitelisted: true,
      transform: true,
      whitelist: true,
    }),
  );

  const openApiConfig = new DocumentBuilder()
    .setTitle('Gnomon API')
    .setDescription('Sun Catcher scheduling API')
    .setVersion('1.0')
    .addBearerAuth({ type: 'http', scheme: 'bearer', bearerFormat: 'JWT' }, 'keycloak')
    .build();
  const document = SwaggerModule.createDocument(app, openApiConfig);
  SwaggerModule.setup('docs', app, document, {
    jsonDocumentUrl: '/v3/api-docs',
    yamlDocumentUrl: '/v3/api-docs.yaml',
    ui: environment !== 'production',
  });
}
