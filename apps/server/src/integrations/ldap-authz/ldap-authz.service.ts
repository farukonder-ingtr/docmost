import {
  Injectable,
  InternalServerErrorException,
  Logger,
  UnauthorizedException,
} from '@nestjs/common';
import { EnvironmentService } from '../environment/environment.service';
import {
  LdapAuthenticatedUser,
  LdapPermission,
  LdapResourceType,
} from './ldap-authz.types';

/**
 * Client for the Spring Boot LDAP authorization service (see
 * docmost-ldap-page-authorization.md). Fails closed: any network/response
 * error is treated as "not allowed", never as "allowed".
 */
@Injectable()
export class LdapAuthzService {
  private readonly logger = new Logger(LdapAuthzService.name);

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

  /**
   * Returns true only if the authz service explicitly allowed the request.
   * Any failure (timeout, 5xx, bad secret, etc.) returns false.
   */
  async isAllowed(
    username: string,
    resourceType: LdapResourceType,
    resourceId: string,
    permission: LdapPermission,
  ): Promise<boolean> {
    try {
      const response = await this.request(
        '/internal/authorize',
        { resourceType, resourceId, permission },
        { 'X-Docmost-User': username },
      );

      if (!response.ok) {
        this.logger.warn(
          `authz service returned ${response.status} for ${resourceType}:${resourceId}`,
        );
        return false;
      }

      const body = await response.json();
      return body?.allowed === true;
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      this.logger.error(`authz service call failed, denying access: ${message}`);
      return false;
    }
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
