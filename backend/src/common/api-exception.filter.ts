import {
  ArgumentsHost,
  Catch,
  ExceptionFilter,
  HttpException,
  HttpStatus,
} from '@nestjs/common';
import { Response } from 'express';

const codes: Record<number, string> = {
  400: 'VALIDATION_ERROR',
  401: 'UNAUTHENTICATED',
  403: 'FORBIDDEN',
  404: 'NOT_FOUND',
  409: 'CONFLICT',
};

@Catch()
export class ApiExceptionFilter implements ExceptionFilter {
  catch(exception: unknown, host: ArgumentsHost) {
    const response = host.switchToHttp().getResponse<Response>();
    const status =
      exception instanceof HttpException
        ? exception.getStatus()
        : HttpStatus.INTERNAL_SERVER_ERROR;
    const payload =
      exception instanceof HttpException
        ? exception.getResponse()
        : 'Internal server error';
    const rawMessage =
      typeof payload === 'string'
        ? payload
        : (payload as { message?: string | string[] }).message;
    const details =
      typeof payload === 'object' && payload !== null
        ? (payload as { details?: unknown }).details
        : undefined;
    response.status(status).json({
      statusCode: status,
      code: codes[status] ?? 'INTERNAL_ERROR',
      message: rawMessage ?? 'Request failed',
      ...(details === undefined ? {} : { details }),
      timestamp: new Date().toISOString(),
    });
  }
}
