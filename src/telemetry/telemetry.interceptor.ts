import { trace, SpanStatusCode } from '@opentelemetry/api';
import type { CallHandler, ExecutionContext, NestInterceptor } from '@nestjs/common';
import type { Request, Response } from 'express';
import type { Observable } from 'rxjs';
import { defer } from 'rxjs';
import { catchError, finalize, tap } from 'rxjs/operators';

export class TelemetryInterceptor implements NestInterceptor {
  intercept(context: ExecutionContext, next: CallHandler): Observable<unknown> {
    if (context.getType() !== 'http') {
      return next.handle();
    }

    const request = context.switchToHttp().getRequest<Request>();
    const response = context.switchToHttp().getResponse<Response>();
    const handler = `${context.getClass().name}.${context.getHandler().name}`;
    const spanName = `${request.method} ${handler}`;

    return defer(() =>
      trace.getTracer('gnomon-http').startActiveSpan(spanName, (span) =>
        next.handle().pipe(
          tap(() => {
            span.setAttributes({
              'http.request.method': request.method,
              'nestjs.handler': handler,
              'http.response.status_code': response.statusCode,
            });
          }),
          catchError((error: unknown) => {
            span.setStatus({ code: SpanStatusCode.ERROR });
            throw error;
          }),
          finalize(() => {
            span.end();
          }),
        ),
      ),
    );
  }
}
