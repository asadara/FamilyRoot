import {
  CallHandler,
  ExecutionContext,
  Injectable,
  NestInterceptor,
} from '@nestjs/common';
import { Request } from 'express';
import {
  catchError,
  from,
  map,
  mergeMap,
  Observable,
  of,
  throwError,
} from 'rxjs';
import { isUUID } from 'class-validator';
import { AuthUser } from '../auth/auth-user.interface';
import { sanitizeObservedPath } from '../common/request-observability.interceptor';
import { MutationReceipt, NotificationsService } from './notifications.service';

type ReceiptCopy = Omit<MutationReceipt, 'userId' | 'spaceId'>;

export function mutationReceiptCopy(
  method: string,
  path: string,
  statusCode: number,
): ReceiptCopy | null {
  if (!['POST', 'PATCH', 'DELETE'].includes(method.toUpperCase())) return null;
  if (
    path.startsWith('/auth') ||
    path.startsWith('/app-compatibility') ||
    path.startsWith('/notifications') ||
    path === '/users/me'
  ) {
    return null;
  }

  if (statusCode >= 400) {
    if (statusCode === 409) {
      return {
        kind: 'WARNING',
        code: 'ACTION_CONFLICT',
        title: 'Perubahan belum disimpan',
        message:
          'Data telah berubah atau masih memiliki konflik. Tinjau lalu coba lagi.',
      };
    }
    if (statusCode === 403) {
      return {
        kind: 'WARNING',
        code: 'ACTION_FORBIDDEN',
        title: 'Tindakan tidak diizinkan',
        message: 'Akses atau peran Anda tidak mengizinkan perubahan ini.',
      };
    }
    if (statusCode === 400 || statusCode === 422) {
      return {
        kind: 'ERROR',
        code: 'ACTION_INVALID',
        title: 'Data belum disimpan',
        message: 'Periksa kembali isian atau aturan keluarga yang berlaku.',
      };
    }
    return {
      kind: 'ERROR',
      code: 'ACTION_FAILED',
      title: 'Perubahan gagal disimpan',
      message: 'Server belum dapat menyelesaikan tindakan. Coba kembali.',
    };
  }

  if (path.includes('/comments')) {
    return {
      kind: 'SUCCESS',
      code: 'COMMENT_SAVED',
      title: 'Komentar terkirim',
      message: 'Komentar berhasil disimpan dalam diskusi usulan.',
    };
  }
  if (
    path.includes('/proposals/approve') ||
    path.includes('/proposals/reject')
  ) {
    return {
      kind: 'SUCCESS',
      code: 'PROPOSAL_REVIEW_SAVED',
      title: 'Keputusan tersimpan',
      message: 'Status dan catatan peninjauan usulan berhasil disimpan.',
    };
  }
  if (path.includes('/relationships') || path.includes('/parent-child')) {
    return {
      kind: 'SUCCESS',
      code: 'RELATIONSHIP_SAVED',
      title: 'Hubungan keluarga tersimpan',
      message: 'Perubahan hubungan berhasil disimpan.',
    };
  }
  if (path.includes('/persons')) {
    return {
      kind: 'SUCCESS',
      code: 'PERSON_SAVED',
      title: 'Data person tersimpan',
      message: 'Perubahan informasi person berhasil disimpan.',
    };
  }
  if (path.includes('/invitations')) {
    return {
      kind: 'SUCCESS',
      code: 'INVITATION_SAVED',
      title: 'Undangan diperbarui',
      message: 'Tindakan undangan berhasil disimpan.',
    };
  }
  if (path.includes('/claims')) {
    return {
      kind: 'SUCCESS',
      code: 'CLAIM_SAVED',
      title: 'Validasi diperbarui',
      message: 'Status validasi identitas berhasil disimpan.',
    };
  }
  return {
    kind: 'SUCCESS',
    code: 'ACTION_SAVED',
    title: 'Perubahan tersimpan',
    message: 'Tindakan berhasil disimpan.',
  };
}

@Injectable()
export class MutationReceiptInterceptor implements NestInterceptor {
  constructor(private readonly notificationsService: NotificationsService) {}

  intercept(context: ExecutionContext, next: CallHandler): Observable<unknown> {
    const request = context.switchToHttp().getRequest<Request>();
    const actor = (request as Request & { user?: AuthUser }).user;
    const method = request.method.toUpperCase();
    const path = sanitizeObservedPath(request);
    const base = actor
      ? {
          userId: actor.userId,
          spaceId: this.spaceId(request),
        }
      : null;

    return next.handle().pipe(
      mergeMap((value: unknown) => {
        const copy = mutationReceiptCopy(method, path, 200);
        if (!base || !copy) return of(value);
        return from(
          this.notificationsService.record({ ...base, ...copy }),
        ).pipe(map(() => value));
      }),
      catchError((error: unknown) => {
        const statusCode =
          typeof error === 'object' &&
          error !== null &&
          'status' in error &&
          typeof error.status === 'number'
            ? error.status
            : 500;
        const copy = mutationReceiptCopy(method, path, statusCode);
        if (!base || !copy) return throwError(() => error);
        return from(
          this.notificationsService.record({ ...base, ...copy }),
        ).pipe(
          catchError(() => of(undefined)),
          mergeMap(() => throwError(() => error)),
        );
      }),
    );
  }

  private spaceId(request: Request) {
    const body = request.body as Record<string, unknown> | undefined;
    const candidates = [
      body?.spaceId,
      request.query.spaceId,
      request.params.spaceId,
    ];
    return (
      candidates.find(
        (candidate): candidate is string =>
          typeof candidate === 'string' && isUUID(candidate),
      ) ?? null
    );
  }
}
