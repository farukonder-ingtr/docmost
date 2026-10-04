import { Global, Module } from '@nestjs/common';
import { PageAccessService } from './page-access.service';
import { LdapAuthzModule } from '../../../integrations/ldap-authz/ldap-authz.module';

@Global()
@Module({
  imports: [LdapAuthzModule],
  providers: [PageAccessService],
  exports: [PageAccessService],
})
export class PageAccessModule {}
