package com.company.docmostauthz.authorization;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationControllerTest {

    @Mock
    private AuthorizationService service;

    @Test
    void delegatesToServiceWithHeaderAsUsername() {
        AuthorizationController controller = new AuthorizationController(service);
        AuthorizationRequest request = new AuthorizationRequest("PAGE", "page-1", Permission.VIEW);
        AuthorizationDecision expected = AuthorizationDecision.allow("ok");

        when(service.authorize("faruk@placeholder.test", "PAGE", "page-1", Permission.VIEW))
                .thenReturn(expected);

        AuthorizationDecision actual = controller.authorize("faruk@placeholder.test", request);

        assertThat(actual).isEqualTo(expected);
        verify(service).authorize("faruk@placeholder.test", "PAGE", "page-1", Permission.VIEW);
    }
}
