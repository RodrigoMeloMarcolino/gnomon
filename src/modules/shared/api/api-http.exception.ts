import { HttpException } from '@nestjs/common';

export interface ApiErrorBody {
  error: {
    code: string;
    message: string;
    details: unknown;
  };
}

export class ApiHttpException extends HttpException {
  constructor(status: number, code: string, message: string, details: unknown = null) {
    super({ error: { code, message, details } } satisfies ApiErrorBody, status);
  }
}
