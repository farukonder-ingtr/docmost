package com.company.docmostauthz.auth;

import com.company.docmostauthz.ldap.LdapService;
import com.company.docmostauthz.ldap.LdapUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Set;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LdapAuthenticationControllerTest {

    @Mock
    private LdapService ldapService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LdapAuthenticationController controller = new LdapAuthenticationController(ldapService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void returnsProfileAndGroupsOnSuccessfulAuthentication() throws Exception {
        when(ldapService.authenticate(eq("faruk"), eq("faruk123")))
                .thenReturn(new LdapUser("faruk", "faruk@placeholder.test", "Faruk Yilmaz", "uid=faruk,ou=Users,dc=placeholder,dc=test", Set.of("DOCMOST-ADMIN")));

        mockMvc.perform(post("/internal/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"faruk\",\"password\":\"faruk123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("faruk@placeholder.test"))
                .andExpect(jsonPath("$.groups[0]").value("DOCMOST-ADMIN"));
    }

    @Test
    void returns401WithoutBodyOnBadCredentials() throws Exception {
        when(ldapService.authenticate(eq("faruk"), eq("wrong")))
                .thenThrow(new BadCredentialsException("Invalid LDAP credentials"));

        mockMvc.perform(post("/internal/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"faruk\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returns401WithoutLeakingUnknownUsername() throws Exception {
        when(ldapService.authenticate(eq("ghost"), eq("whatever")))
                .thenThrow(new IllegalArgumentException("LDAP user not found: ghost"));

        mockMvc.perform(post("/internal/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ghost\",\"password\":\"whatever\"}"))
                .andExpect(status().isUnauthorized());
    }
}
