#!/bin/bash
# Seeds the test OpenLDAP container with OUs/users/groups for local/dev testing.
# groupOfNames requires at least one "member" attribute, but our OU entries
# must exist first, so we add OUs then entries referencing them, in file order.
set -euo pipefail

LDAP_HOST="${LDAP_HOST:-ldap://ldap:389}"
BIND_DN="${BIND_DN:-cn=admin,dc=placeholder,dc=test}"
BIND_PW="${BIND_PW:-admin}"

echo "Seeding test LDAP at ${LDAP_HOST} ..."

ldapadd -x -H "${LDAP_HOST}" -D "${BIND_DN}" -w "${BIND_PW}" -f /seed/raw.ldif -c || true

echo "LDAP seed complete."
