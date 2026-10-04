package com.company.docmostauthz.ldap;

import com.company.docmostauthz.config.LdapProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.ContextMapper;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.BaseLdapPathContextSource;
import org.springframework.security.authentication.BadCredentialsException;

import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.ldap.LdapName;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LdapServiceTest {

    @Mock
    private LdapTemplate ldapTemplate;

    @Mock
    private BaseLdapPathContextSource contextSource;

    private LdapProperties ldapProperties;
    private LdapService ldapService;

    private static final String USER_DN = "uid=faruk,ou=Users,dc=placeholder,dc=test";

    @BeforeEach
    void setUp() {
        ldapProperties = new LdapProperties();
        ldapProperties.setUserSearchBase("ou=Users,dc=placeholder,dc=test");
        ldapProperties.setUserSearchFilter("(uid={0})");
        ldapProperties.setGroupSearchBase("ou=Groups,dc=placeholder,dc=test");

        ldapService = new LdapService(ldapTemplate, contextSource, ldapProperties);
    }

    /** Builds a fake directory entry and wires the mocked LdapTemplate to hand it to whichever ContextMapper is passed in. */
    private void stubUserSearch(String filter, String uid, String mail, String displayName) throws Exception {
        BasicAttributes attrs = new BasicAttributes();
        attrs.put(new BasicAttribute("uid", uid));
        attrs.put(new BasicAttribute("mail", mail));
        attrs.put(new BasicAttribute("displayName", displayName));
        DirContextAdapter adapter = new DirContextAdapter(attrs, new LdapName(USER_DN));

        when(ldapTemplate.search(eq(ldapProperties.getUserSearchBase()), eq(filter), any(ContextMapper.class)))
                .thenAnswer(invocation -> {
                    ContextMapper<?> mapper = invocation.getArgument(2);
                    return List.of(mapper.mapFromContext(adapter));
                });
    }

    private void stubGroupSearch(String... groupNames) {
        when(ldapTemplate.search(eq(ldapProperties.getGroupSearchBase()), anyString(), any(AttributesMapper.class)))
                .thenAnswer(invocation -> {
                    AttributesMapper<?> mapper = invocation.getArgument(2);
                    return List.of(groupNames).stream()
                            .map(name -> {
                                BasicAttributes groupAttrs = new BasicAttributes();
                                groupAttrs.put(new BasicAttribute("cn", name));
                                try {
                                    return mapper.mapFromAttributes(groupAttrs);
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            })
                            .toList();
                });
    }

    @Test
    void findUserResolvesProfileAndGroups() throws Exception {
        stubUserSearch("(uid=faruk)", "faruk", "faruk@placeholder.test", "Faruk Yilmaz");
        stubGroupSearch("DOCMOST-ADMIN", "DOCMOST-IT");

        LdapUser user = ldapService.findUser("faruk");

        assertThat(user.username()).isEqualTo("faruk");
        assertThat(user.email()).isEqualTo("faruk@placeholder.test");
        assertThat(user.displayName()).isEqualTo("Faruk Yilmaz");
        assertThat(user.dn()).isEqualTo(USER_DN);
        assertThat(user.groups()).containsExactlyInAnyOrder("DOCMOST-ADMIN", "DOCMOST-IT");
    }

    @Test
    void findUserThrowsWhenNoEntryMatches() {
        when(ldapTemplate.search(eq(ldapProperties.getUserSearchBase()), eq("(uid=ghost)"), any(ContextMapper.class)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> ldapService.findUser("ghost"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void groupSearchFilterEscapesSpecialCharactersInUserDn() throws Exception {
        String dnWithParens = "cn=weird (name),ou=Users,dc=placeholder,dc=test";

        BasicAttributes attrs = new BasicAttributes();
        attrs.put(new BasicAttribute("uid", "weird"));
        attrs.put(new BasicAttribute("mail", "weird@placeholder.test"));
        DirContextAdapter adapter = new DirContextAdapter(attrs, new LdapName(dnWithParens));

        when(ldapTemplate.search(eq(ldapProperties.getUserSearchBase()), anyString(), any(ContextMapper.class)))
                .thenAnswer(invocation -> {
                    ContextMapper<?> mapper = invocation.getArgument(2);
                    return List.of(mapper.mapFromContext(adapter));
                });

        ArgumentCaptor<String> filterCaptor = ArgumentCaptor.forClass(String.class);
        when(ldapTemplate.search(eq(ldapProperties.getGroupSearchBase()), filterCaptor.capture(), any(AttributesMapper.class)))
                .thenReturn(List.of());

        ldapService.findUser("weird");

        assertThat(filterCaptor.getValue())
                .contains("\\28")
                .contains("\\29")
                .doesNotContain("(name)");
    }

    @Test
    void authenticateSucceedsWhenBindSucceeds() throws Exception {
        stubUserSearch("(uid=faruk)", "faruk", "faruk@placeholder.test", "Faruk Yilmaz");
        stubGroupSearch("DOCMOST-ADMIN");
        when(ldapTemplate.authenticate(eq(USER_DN), eq("(objectClass=*)"), eq("correct-password"))).thenReturn(true);

        LdapUser user = ldapService.authenticate("faruk", "correct-password");

        assertThat(user.username()).isEqualTo("faruk");
    }

    @Test
    void authenticateThrowsBadCredentialsWhenBindFails() throws Exception {
        stubUserSearch("(uid=faruk)", "faruk", "faruk@placeholder.test", "Faruk Yilmaz");
        stubGroupSearch("DOCMOST-ADMIN");
        when(ldapTemplate.authenticate(eq(USER_DN), eq("(objectClass=*)"), eq("wrong-password"))).thenReturn(false);

        assertThatThrownBy(() -> ldapService.authenticate("faruk", "wrong-password"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void authenticateThrowsBadCredentialsWhenUserUnknown() {
        when(ldapTemplate.search(eq(ldapProperties.getUserSearchBase()), eq("(uid=ghost)"), any(ContextMapper.class)))
                .thenReturn(List.of());

        // Unknown username surfaces as IllegalArgumentException from findUser();
        // the authenticate() caller in AuthorizationService/controllers maps both
        // to the same 401 so unknown-vs-wrong-password is never distinguishable externally.
        assertThatThrownBy(() -> ldapService.authenticate("ghost", "whatever"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
