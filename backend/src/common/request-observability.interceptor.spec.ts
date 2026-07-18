import { Request } from 'express';
import { sanitizeObservedPath } from './request-observability.interceptor';

describe('privacy-safe request observability', () => {
  it('uses the route template instead of a person identifier', () => {
    const request = {
      baseUrl: '',
      route: { path: '/persons/:personId/sources' },
      path: '/persons/3d594650-1b18-485f-bc33-9b1b72832c02/sources',
    } as Request;

    expect(sanitizeObservedPath(request)).toBe('/persons/:personId/sources');
  });

  it('redacts long invitation tokens when route metadata is unavailable', () => {
    const request = {
      baseUrl: '',
      path: '/spaces/invitations/this-is-a-long-secret-invitation-token',
    } as Request;

    expect(sanitizeObservedPath(request)).toBe(
      '/spaces/invitations/:identifier',
    );
  });
});
