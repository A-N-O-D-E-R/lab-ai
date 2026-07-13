package bio.anode.ai.auth;

import bio.anode.ai.config.LabaiProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity.CsrfSpec;
import org.springframework.security.config.web.server.ServerHttpSecurity.FormLoginSpec;
import org.springframework.security.config.web.server.ServerHttpSecurity.HttpBasicSpec;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http, LabaiProperties properties) {
        var authManager = new ApiKeyReactiveAuthenticationManager(properties);
        var authenticationWebFilter = new AuthenticationWebFilter(authManager);

        authenticationWebFilter.setServerAuthenticationConverter(new ApiKeyServerAuthenticationConverter());
        authenticationWebFilter.setSecurityContextRepository(NoOpServerSecurityContextRepository.getInstance());

        http
                .csrf(CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/api/**").authenticated()
                        .anyExchange().permitAll())
                // Place the filter at the AUTHENTICATION phase
                .addFilterAt(authenticationWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                // disable default forms and basic auth
                .httpBasic(HttpBasicSpec::disable)
                .formLogin(FormLoginSpec::disable);

        return http.build();
    }
}
