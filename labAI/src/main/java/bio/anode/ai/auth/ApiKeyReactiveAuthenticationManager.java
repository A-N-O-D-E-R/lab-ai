package bio.anode.ai.auth;

import bio.anode.ai.config.LabaiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import reactor.core.publisher.Mono;

import java.util.List;

@RequiredArgsConstructor
@Slf4j
public class ApiKeyReactiveAuthenticationManager implements ReactiveAuthenticationManager {

    private final LabaiProperties labaiProperties;

    @Override
    @NonNull
    public Mono<Authentication> authenticate(@NonNull Authentication authentication) {
        if (authentication.getCredentials() == null) {
            return Mono.empty();
        }

        if (authentication.getCredentials() instanceof ApiKeyCredentials providedKey) {
            var username = labaiProperties.apikeys().get(providedKey.apiKey());
            if (username == null)
                return Mono.error(new AccessDeniedException("Invalid API key"));

            var authorities = List.of(new SimpleGrantedAuthority("ROLE_API"));

            // Authenticate with the end-user email if the frontend forwarded one, otherwise fall
            // back to the username bound to the API key in configuration.
            var auth = new UsernamePasswordAuthenticationToken(
                    StringUtils.defaultIfBlank(providedKey.endUserEmail(), username),
                    providedKey,
                    authorities);

            return Mono.just(auth);
        }

        return Mono.error(new AccessDeniedException("Invalid credentials"));
    }
}
