package team.projectpulse.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import team.projectpulse.user.UserRepository;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ActiveAccountJwtAuthenticationConverterTest {

    @Mock
    Converter<Jwt, AbstractAuthenticationToken> delegate;

    @Mock
    UserRepository userRepository;


    private static Jwt jwtWithUserId(Object userId) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("j.smith@abc.edu")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        if (userId != null) {
            builder.claim("userId", userId);
        }
        return builder.build();
    }

    @Test
    void testEnabledAccountIsAuthenticatedByTheDelegate() {
        Jwt jwt = jwtWithUserId(4L);
        JwtAuthenticationToken token = new JwtAuthenticationToken(jwt);
        given(this.userRepository.existsByIdAndEnabledTrue(4)).willReturn(true);
        given(this.delegate.convert(jwt)).willReturn(token);

        ActiveAccountJwtAuthenticationConverter converter = new ActiveAccountJwtAuthenticationConverter(this.delegate, this.userRepository);

        assertThat(converter.convert(jwt)).isSameAs(token);
    }

    @Test
    void testDeactivatedAccountIsRefusedEvenWithAValidToken() {
        Jwt jwt = jwtWithUserId(4L);
        given(this.userRepository.existsByIdAndEnabledTrue(4)).willReturn(false);

        ActiveAccountJwtAuthenticationConverter converter = new ActiveAccountJwtAuthenticationConverter(this.delegate, this.userRepository);

        assertThatThrownBy(() -> converter.convert(jwt)).isInstanceOf(DisabledException.class);
        verify(this.delegate, never()).convert(any());
    }

    @Test
    void testTokenWithoutAUserIdIsRefused() {
        Jwt jwt = jwtWithUserId(null);

        ActiveAccountJwtAuthenticationConverter converter = new ActiveAccountJwtAuthenticationConverter(this.delegate, this.userRepository);

        assertThatThrownBy(() -> converter.convert(jwt)).isInstanceOf(DisabledException.class);
        verifyNoInteractions(this.userRepository, this.delegate);
    }

}
