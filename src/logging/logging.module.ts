import { Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { LoggerModule } from 'nestjs-pino';

import type { Environment } from '../config/environment';
import { resolveRequestId } from './request-id';

export const LOG_REDACTION: { paths: string[]; censor: string } = {
  paths: [
    'req.headers',
    'req.body',
    'req.query',
    'request.headers',
    'request.body',
    'request.query',
    '*.authorization',
    '*.cookie',
    '*.idempotency-key',
    '*.idempotencyKey',
  ],
  censor: '[Redacted]',
};

export function serializeRequest(request: { id?: string; method?: string }): {
  id?: string;
  method?: string;
} {
  return { id: request.id, method: request.method };
}

@Module({
  imports: [
    LoggerModule.forRootAsync({
      inject: [ConfigService],
      useFactory: (config: ConfigService<Environment, true>) => ({
        pinoHttp: {
          level: config.get('LOG_LEVEL', { infer: true }),
          autoLogging: true,
          quietReqLogger: true,
          genReqId: (request, response) => {
            const requestId = resolveRequestId(request.headers['x-request-id']);
            response.setHeader('x-request-id', requestId);
            return requestId;
          },
          serializers: {
            req: serializeRequest,
            res: (response: { statusCode?: number }) => ({
              statusCode: response.statusCode,
            }),
          },
          redact: LOG_REDACTION,
          transport:
            config.get('NODE_ENV', { infer: true }) === 'development'
              ? {
                  target: 'pino-pretty',
                  options: { colorize: true, singleLine: true, translateTime: 'SYS:standard' },
                }
              : undefined,
        },
      }),
    }),
  ],
  exports: [LoggerModule],
})
export class LoggingModule {}
