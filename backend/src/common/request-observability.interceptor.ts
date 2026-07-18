import {
  CallHandler,
  ExecutionContext,
  Injectable,
  Logger,
  NestInterceptor,
} from '@nestjs/common';
import { randomUUID } from 'node:crypto';
import { Request, Response } from 'express';
import { Observable } from 'rxjs';

const SAFE_REQUEST_ID = /^[A-Za-z0-9._-]{8,128}$/;
const IDENTIFIER_SEGMENT = /^[0-9a-f]{8}-[0-9a-f-]{27,}$/i;

export function sanitizeObservedPath(request: Request): string {
  const route = request.route as unknown as { path?: unknown } | undefined;
  const routePath = route?.path;
  if (typeof routePath === 'string') {
    return `${request.baseUrl}${routePath}`;
  }
  return request.path
    .split('/')
    .map((segment) =>
      IDENTIFIER_SEGMENT.test(segment) || segment.length > 24
        ? ':identifier'
        : segment,
    )
    .join('/');
}

@Injectable()
export class RequestObservabilityInterceptor implements NestInterceptor {
  private readonly logger = new Logger('HTTP');

  intercept(context: ExecutionContext, next: CallHandler): Observable<unknown> {
    const request = context.switchToHttp().getRequest<Request>();
    const response = context.switchToHttp().getResponse<Response>();
    const suppliedId = request.header('x-request-id');
    const requestId =
      suppliedId && SAFE_REQUEST_ID.test(suppliedId)
        ? suppliedId
        : randomUUID();
    const startedAt = process.hrtime.bigint();

    response.setHeader('x-request-id', requestId);
    response.once('finish', () => {
      const durationMs =
        Number(process.hrtime.bigint() - startedAt) / 1_000_000;
      // Deliberately exclude query strings, bodies, auth headers, user IDs, and family data.
      this.logger.log(
        JSON.stringify({
          event: 'http_request',
          requestId,
          method: request.method,
          path: sanitizeObservedPath(request),
          statusCode: response.statusCode,
          durationMs: Number(durationMs.toFixed(1)),
        }),
      );
    });

    return next.handle();
  }
}
