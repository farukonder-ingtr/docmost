import { Injectable, Logger } from '@nestjs/common';
import { InjectKysely } from 'nestjs-kysely';
import { sql } from 'kysely';
import { KyselyDB } from '@docmost/db/types/kysely.types';
import { SpaceMemberRepo } from '@docmost/db/repos/space/space-member.repo';
import { SpaceMemberService } from '../../core/space/services/space-member.service';
import { SpaceRole } from '../../common/helpers/types/permission';

// LDAP group "INGWIKI_<NAME>" grants access to the Docmost space "INGWIKI-<NAME>"
const SPACE_GROUP_PATTERN = /^INGWIKI_(.+)$/i;

@Injectable()
export class LdapSpaceProvisionService {
  private readonly logger = new Logger(LdapSpaceProvisionService.name);

  constructor(
    @InjectKysely() private readonly db: KyselyDB,
    private readonly spaceMemberRepo: SpaceMemberRepo,
    private readonly spaceMemberService: SpaceMemberService,
  ) {}

  /**
   * Grants space membership (default role: writer) for every LDAP group named
   * INGWIKI_<NAME> that has a matching INGWIKI-<NAME> space in this workspace.
   * Idempotent and additive only — never revokes membership if the LDAP group
   * is later removed. From this point on, normal Docmost space-level RBAC
   * (SpaceRole) governs the user's access, same as any native member.
   */
  async syncSpaceMemberships(
    userId: string,
    workspaceId: string,
    ldapGroups: string[],
  ): Promise<void> {
    for (const group of ldapGroups) {
      const match = group.match(SPACE_GROUP_PATTERN);
      if (!match) {
        continue;
      }

      const expectedSpaceName = `INGWIKI-${match[1]}`;

      const space = await this.db
        .selectFrom('spaces')
        .select(['id'])
        .where('workspaceId', '=', workspaceId)
        .where(sql`LOWER(name)`, '=', sql`LOWER(${expectedSpaceName})`)
        .executeTakeFirst();

      if (!space) {
        continue;
      }

      const existingRoles = await this.spaceMemberRepo.getUserSpaceRoles(
        userId,
        space.id,
      );
      if (existingRoles?.length > 0) {
        continue;
      }

      try {
        await this.spaceMemberService.addUserToSpace(
          userId,
          space.id,
          SpaceRole.WRITER,
          workspaceId,
        );
        this.logger.log(
          `Granted space access via LDAP group "${group}": userId=${userId} space="${expectedSpaceName}"`,
        );
      } catch (err) {
        this.logger.error(
          `Failed to grant space access via LDAP group "${group}" for userId=${userId}`,
          err,
        );
      }
    }
  }
}
