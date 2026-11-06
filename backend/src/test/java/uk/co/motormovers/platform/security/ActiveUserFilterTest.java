package uk.co.motormovers.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import uk.co.motormovers.platform.security.service.ActiveUserFilter;
import uk.co.motormovers.platform.user.repository.AppUserRepository;

/** This filter runs before the API's error handling, so a failure in it must still answer as a problem detail. */
class ActiveUserFilterTest {

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aDatabaseFailureIsAProblemDetail() throws Exception {
        AppUserRepository users = mock(AppUserRepository.class);
        when(users.findById(any())).thenThrow(new DataAccessResourceFailureException("connection to db:5432 refused"));
        Jwt jwt = new Jwt("token", Instant.EPOCH, Instant.MAX, Map.of("alg", "none"), Map.of("uid", 7));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new ActiveUserFilter(users, Clock.systemUTC()).doFilter(new MockHttpServletRequest(), response, chain);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        assertThat(response.getContentAsString()).contains("\"detail\":\"Something went wrong. Please try again.\"")
                .doesNotContain("db:5432");
        assertThat(chain.getRequest()).isNull();
    }
}
