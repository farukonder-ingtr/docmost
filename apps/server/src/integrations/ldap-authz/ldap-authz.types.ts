export enum LdapPermission {
  VIEW = 'VIEW',
  EDIT = 'EDIT',
  ADMIN = 'ADMIN',
}

export enum LdapResourceType {
  PAGE = 'PAGE',
  SPACE = 'SPACE',
}

export interface LdapAuthenticatedUser {
  username: string;
  email: string;
  displayName: string;
  groups: string[];
}
