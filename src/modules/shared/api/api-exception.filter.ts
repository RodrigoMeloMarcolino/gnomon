import { ArgumentsHost, Catch, HttpException, HttpStatus } from '@nestjs/common';
import type { ExceptionFilter } from '@nestjs/common';
import type { Request, Response } from 'express';

import type { ApiErrorBody } from './api-http.exception';

export interface ErrorLogger {
  error(bindings: Record<string, unknown>, message: string): void;
}

const STATUS_CODES: Partial<Record<number, string>> = {
  [HttpStatus.BAD_REQUEST]: 'validation_error',
  [HttpStatus.UNAUTHORIZED]: 'unauthorized',
  [HttpStatus.FORBIDDEN]: 'forbidden',
  [HttpStatus.NOT_FOUND]: 'not_found',
  [HttpStatus.CONFLICT]: 'conflict',
  [HttpStatus.TOO_MANY_REQUESTS]: 'too_many_requests',
  [HttpStatus.SERVICE_UNAVAILABLE]: 'service_unavailable',
};

const STATUS_MESSAGES: Partial<Record<number, string>> = {
  [HttpStatus.BAD_REQUEST]: 'Request validation failed',
  [HttpStatus.UNAUTHORIZED]: 'Authentication is required',
  [HttpStatus.FORBIDDEN]: 'Access denied',
  [HttpStatus.NOT_FOUND]: 'Resource not found',
  [HttpStatus.CONFLICT]: 'Request conflicts with the current state',
  [HttpStatus.TOO_MANY_REQUESTS]: 'Too many requests',
  [HttpStatus.SERVICE_UNAVAILABLE]: 'Service unavailable',
};

function isApiErrorBody(value: unknown): value is ApiErrorBody {
  if (!value || typeof value !== 'object' || !('error' in value)) {
    return false;
  }

  const error = (value as { error?: unknown }).error;
  return Boolean(
    error &&
    typeof error === 'object' &&
    typeof (error as { code?: unknown }).code === 'string' &&
    typeof (error as { message?: unknown }).message === 'string',
  );
}

function httpExceptionBody(exception: HttpException): ApiErrorBody {
  const status = exception.getStatus();
  const response = exception.getResponse();
  if (isApiErrorBody(response)) {
    return response;
  }

  const messages =
    response && typeof response === 'object' && 'message' in response
      ? (response as { message?: unknown }).message
      : undefined;

  return {
    error: {
      code: STATUS_CODES[status] ?? 'http_error',
      message: STATUS_MESSAGES[status] ?? 'Request failed',
      details: Array.isArray(messages) ? { issues: messages } : null,
    },
  };
}

@Catch()
export class ApiExceptionFilter implements ExceptionFilter {
  constructor(private readonly logger: ErrorLogger) {}

  catch(exception: unknown, host: ArgumentsHost): void {
    const http = host.switchToHttp();
    const request = http.getRequest<Request & { id?: string }>();
    const response = http.getResponse<Response>();

    if (exception instanceof HttpException) {
      response.status(exception.getStatus()).json(httpExceptionBody(exception));
      return;
    }

    this.logger.error(
      {
        err: exception,
        requestId: request.id,
        method: request.method,
      },
      'Unhandled request error',
    );
    response.status(HttpStatus.INTERNAL_SERVER_ERROR).json({
      error: {
        code: 'internal_error',
        message: 'An unexpected error occurred',
        details: null,
      },
    } satisfies ApiErrorBody);
  }
}
