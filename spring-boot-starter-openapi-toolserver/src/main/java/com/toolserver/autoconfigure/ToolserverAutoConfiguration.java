package com.toolserver.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.RestController;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({OpenAPI.class, RestController.class})
@AutoConfigureAfter(SpringDocConfiguration.class)
@EnableConfigurationProperties(ToolserverProperties.class)
@ConditionalOnProperty(prefix = "toolserver", name = "enabled", matchIfMissing = true)
public class ToolserverAutoConfiguration {

    @Bean
    public OpenApiEnhancer openApiEnhancer(ToolserverProperties properties, Environment environment) {
        return new OpenApiEnhancer(properties, environment);
    }

    @Bean
    public OpenApiToolserverController openApiToolserverController(Environment environment) {
        return new OpenApiToolserverController(environment);
    }

    @Bean
    @ConditionalOnProperty(prefix = "toolserver.cors", name = "enabled", matchIfMissing = true)
    public ToolserverCorsConfiguration toolserverCorsConfiguration(ToolserverProperties properties) {
        return new ToolserverCorsConfiguration(properties);
    }

    @Bean
    public ToolserverStartupBanner toolserverStartupBanner(ToolserverProperties properties, Environment environment) {
        return new ToolserverStartupBanner(properties, environment);
    }
}
