import { Module } from '@nestjs/common';
import { LdapAuthzService } from './ldap-authz.service';
import { LdapSpaceProvisionService } from './ldap-space-provision.service';
import { SpaceModule } from '../../core/space/space.module';

@Module({
  imports: [SpaceModule],
  providers: [LdapAuthzService, LdapSpaceProvisionService],
  exports: [LdapAuthzService, LdapSpaceProvisionService],
})
export class LdapAuthzModule {}
