import { ForbiddenException, Injectable } from '@nestjs/common';
import { Page, User } from '@docmost/db/types/entity.types';
import { PagePermissionRepo } from '@docmost/db/repos/page/page-permission.repo';
import SpaceAbilityFactory from '../../casl/abilities/space-ability.factory';
import {
  SpaceCaslAction,
  SpaceCaslSubject,
} from '../../casl/interfaces/space-ability.type';
import { SpaceRepo } from '@docmost/db/repos/space/space.repo';
import { LdapAuthzService } from '../../../integrations/ldap-authz/ldap-authz.service';
import {
  LdapPermission,
  LdapResourceType,
} from '../../../integrations/ldap-authz/ldap-authz.types';

@Injectable()
export class PageAccessService {
  constructor(
    private readonly pagePermissionRepo: PagePermissionRepo,
    private readonly spaceAbility: SpaceAbilityFactory,
    private readonly spaceRepo: SpaceRepo,
    private readonly ldapAuthzService: LdapAuthzService,
  ) {}

  /**
   * Secondary authorization layer: LDAP group policies (see
   * docmost-ldap-page-authorization.md). Runs in addition to, never instead
   * of, the space/page permission checks above — both must allow access.
   */
  private async assertLdapAllowed(
    page: Page,
    user: User,
    permission: LdapPermission,
  ): Promise<void> {
    if (!this.ldapAuthzService.isEnabled()) {
      return;
    }

    const allowed = await this.ldapAuthzService.isAllowed(
      user.email,
      LdapResourceType.PAGE,
      page.id,
      permission,
    );

    if (!allowed) {
      throw new ForbiddenException();
    }
  }

  /**
   * Validate user can view page, throws ForbiddenException if not.
   * If page has restrictions: page-level permission determines access.
   * If no restrictions: space-level permission determines access.
   */
  async validateCanView(page: Page, user: User): Promise<void> {
    // TODO: cache by pageId and userId.
    const ability = await this.spaceAbility.createForUser(user, page.spaceId);

    // User must be at least a space member
    if (ability.cannot(SpaceCaslAction.Read, SpaceCaslSubject.Page)) {
      throw new ForbiddenException();
    }

    const canAccess = await this.pagePermissionRepo.canUserAccessPage(
      user.id,
      page.id,
    );
    if (!canAccess) {
      throw new ForbiddenException();
    }

    await this.assertLdapAllowed(page, user, LdapPermission.VIEW);
  }

  /**
   * Validate user can view page AND return effective canEdit permission.
   * Combines access check + edit permission in a single query pass.
   */
  async validateCanViewWithPermissions(
    page: Page,
    user: User,
  ): Promise<{ canEdit: boolean; hasRestriction: boolean }> {
    const ability = await this.spaceAbility.createForUser(user, page.spaceId);

    if (ability.cannot(SpaceCaslAction.Read, SpaceCaslSubject.Page)) {
      throw new ForbiddenException();
    }

    const { hasAnyRestriction, canAccess, canEdit } =
      await this.pagePermissionRepo.canUserEditPage(user.id, page.id);

    if (hasAnyRestriction && !canAccess) {
      throw new ForbiddenException();
    }

    await this.assertLdapAllowed(page, user, LdapPermission.VIEW);

    const localCanEdit = hasAnyRestriction
      ? canEdit
      : ability.can(SpaceCaslAction.Edit, SpaceCaslSubject.Page);

    const ldapCanEdit =
      localCanEdit && this.ldapAuthzService.isEnabled()
        ? await this.ldapAuthzService.isAllowed(
            user.email,
            LdapResourceType.PAGE,
            page.id,
            LdapPermission.EDIT,
          )
        : localCanEdit;

    return {
      canEdit: ldapCanEdit,
      hasRestriction: hasAnyRestriction,
    };
  }

  /**
   * Validate user can edit page, throws ForbiddenException if not.
   * If page has restrictions: page-level writer permission determines access.
   * If no restrictions: space-level edit permission determines access.
   */
  async validateCanEdit(
    page: Page,
    user: User,
  ): Promise<{ hasRestriction: boolean }> {
    const ability = await this.spaceAbility.createForUser(user, page.spaceId);

    // User must be at least a space member
    if (ability.cannot(SpaceCaslAction.Read, SpaceCaslSubject.Page)) {
      throw new ForbiddenException();
    }

    const { hasAnyRestriction, canEdit } =
      await this.pagePermissionRepo.canUserEditPage(user.id, page.id);

    if (hasAnyRestriction) {
      // Page has restrictions - use page-level permission
      if (!canEdit) {
        throw new ForbiddenException();
      }
    } else {
      // No restrictions - use space-level permission
      if (ability.cannot(SpaceCaslAction.Edit, SpaceCaslSubject.Page)) {
        throw new ForbiddenException();
      }
    }

    await this.assertLdapAllowed(page, user, LdapPermission.EDIT);

    return { hasRestriction: hasAnyRestriction };
  }

  async validateCanComment(
    page: Page,
    user: User,
    workspaceId: string,
  ): Promise<void> {
    try {
      await this.validateCanEdit(page, user);
      return;
    } catch {
      // User cannot edit — check if reader commenting is enabled
    }

    await this.validateCanView(page, user);

    const space = await this.spaceRepo.findById(page.spaceId, workspaceId);
    const settings = space?.settings as Record<string, any> | null;
    if (!settings?.comments?.allowViewerComments) {
      throw new ForbiddenException();
    }
  }
}
