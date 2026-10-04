export interface LdapAuthenticatedUser {
  username: string;
  email: string;
  displayName: string;
  groups: string[];
}
