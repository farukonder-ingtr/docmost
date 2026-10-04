import {
  Injectable,
  InternalServerErrorException,
  UnauthorizedException,
} from '@nestjs/common';
import { EnvironmentService } from '../environment/environment.service';
import { LdapAuthenticatedUser } from './ldap-authz.types';

/**
 * Client for the Spring Boot LDAP authentication service (see
 * docmost-ldap-page-authorization.md).
 */
@Injectable()
export class LdapAuthzService {
  constructor(private readonly environmentService: EnvironmentService) {}

  isEnabled(): boolean {
    return this.environmentService.isLdapAuthEnabled();
  }

  async authenticate(
    username: string,
    password: string,
  ): Promise<LdapAuthenticatedUser> {
    const response = await this.request('/internal/authenticate', {
      username,
      password,
    });

    if (response.status === 401) {
      throw new UnauthorizedException('Invalid LDAP credentials');
    }

    if (!response.ok) {
      throw new InternalServerErrorException(
        'LDAP authentication service unavailable',
      );
    }

    return response.json() as Promise<LdapAuthenticatedUser>;
  }

  private async request(
    path: string,
    body: unknown,
    extraHeaders: Record<string, string> = {},
  ): Promise<Response> {
    const baseUrl = this.environmentService.getAuthzUrl();
    const secret = this.environmentService.getDocmostInternalSecret();

    return fetch(`${baseUrl}${path}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Docmost-Internal-Secret': secret,
        ...extraHeaders,
      },
      body: JSON.stringify(body),
    });
  }
}
