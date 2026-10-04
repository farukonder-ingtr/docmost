import { Module } from '@nestjs/common';
import { LdapAuthzService } from './ldap-authz.service';

@Module({
  providers: [LdapAuthzService],
  exports: [LdapAuthzService],
})
export class LdapAuthzModule {}
