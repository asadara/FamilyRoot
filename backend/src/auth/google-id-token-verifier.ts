import {
  Injectable,
  ServiceUnavailableException,
  UnauthorizedException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { OAuth2Client } from 'google-auth-library';

export const GOOGLE_ID_TOKEN_VERIFIER = Symbol('GOOGLE_ID_TOKEN_VERIFIER');

export type GoogleIdentity = {
  subject: string;
  email: string;
  displayName: string;
  hostedDomain: string | null;
};

export interface GoogleIdTokenVerifierContract {
  verify(idToken: string): Promise<GoogleIdentity>;
}

@Injectable()
export class GoogleIdTokenVerifier implements GoogleIdTokenVerifierContract {
  private readonly client = new OAuth2Client();

  constructor(private readonly config: ConfigService) {}

  async verify(idToken: string): Promise<GoogleIdentity> {
    const audience = this.config.get<string>('GOOGLE_OAUTH_CLIENT_ID')?.trim();
    if (!audience) {
      throw new ServiceUnavailableException(
        'Google Sign-In is not configured on this server',
      );
    }

    try {
      const ticket = await this.client.verifyIdToken({ idToken, audience });
      const payload = ticket.getPayload();
      if (!payload?.sub || !payload.email || payload.email_verified !== true) {
        throw new UnauthorizedException(
          'Google ID token does not contain a verified identity',
        );
      }

      const email = payload.email.trim().toLowerCase();
      return {
        subject: payload.sub,
        email,
        displayName: payload.name?.trim() || email.split('@')[0],
        hostedDomain: payload.hd?.trim().toLowerCase() || null,
      };
    } catch (error) {
      if (error instanceof UnauthorizedException) throw error;
      throw new UnauthorizedException('Google ID token is invalid or expired');
    }
  }
}
