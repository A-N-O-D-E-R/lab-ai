package bio.anode.ai.auth;

import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

public class ApiKeyServerAuthenticationConverter implements ServerAuthenticationConverter {
    public static final String BEARER = "Bearer ";
    public static final String USER_EMAIL_HEADER = "X-OpenWebUI-User-Email";

    @Override
    @NonNull
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        var request = exchange.getRequest();
        var apiKey = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        var endUserEmail = request.getHeaders().getFirst(USER_EMAIL_HEADER);

        if (apiKey == null || apiKey.isBlank() || !apiKey.startsWith(BEARER))
            return Mono.empty();

        var token = apiKey.substring(BEARER.length());

        var credentials = new ApiKeyCredentials(token, endUserEmail);
        return Mono.just(new UsernamePasswordAuthenticationToken("api-key", credentials));
    }
}
