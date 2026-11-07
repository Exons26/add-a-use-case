package team.projectpulse.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.jwt.Jwt;
import team.projectpulse.user.UserRepository;

/**
 * Turns a verified JWT into an authentication only while the account it names is still enabled
 * (FR-SEC-active-account).
 *
 * <p><strong>Why every request, not only sign-in.</strong> A JWT is self-contained and lives for two hours. Without
 * this check a student or instructor who is deactivated keeps working until her token expires, because nothing
 * after sign-in ever looks at the account again. Here the account is re-read on each request, so deactivation takes
 * effect on her very next call.
 *
 * <p><strong>Why it answers the same as sign-in.</strong> A disabled or deleted account throws
 * {@link DisabledException}, the exception {@code DaoAuthenticationProvider} throws for a disabled account at login.
 * The bearer-token failure path hands it to {@code CustomBearerTokenAuthenticationEntryPoint}, and
 * {@code ExceptionHandlerAdvice} maps it to 401 "User account is abnormal.", which the SPA treats as a logout.
 *
 * <p>The check is an existence query on the primary key plus the flag, not an entity load: loading a
 * {@code Student} would eagerly pull her course section and team on every API call.
 */
public class ActiveAccountJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final Converter<Jwt, AbstractAuthenticationToken> delegate;
    private final UserRepository userRepository;


    public ActiveAccountJwtAuthenticationConverter(Converter<Jwt, AbstractAuthenticationToken> delegate, UserRepository userRepository) {
        this.delegate = delegate;
        this.userRepository = userRepository;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Integer userId = readUserId(jwt);
        if (userId == null || !this.userRepository.existsByIdAndEnabledTrue(userId)) {
            throw new DisabledException("The account is deactivated.");
        }
        return this.delegate.convert(jwt);
    }

    private static Integer readUserId(Jwt jwt) {
        Object claim = jwt.getClaim("userId");
        if (claim instanceof Number number) {
            return number.intValue();
        }
        if (claim instanceof String string) {
            try {
                return Integer.valueOf(string);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

}
