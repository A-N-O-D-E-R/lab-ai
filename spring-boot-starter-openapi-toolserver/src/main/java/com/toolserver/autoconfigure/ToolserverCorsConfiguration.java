package com.toolserver.autoconfigure;

import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

public class ToolserverCorsConfiguration implements WebMvcConfigurer {

    private final ToolserverProperties properties;

    public ToolserverCorsConfiguration(ToolserverProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (!properties.getCors().isEnabled()) {
            return;
        }

        ToolserverProperties.Cors corsConfig = properties.getCors();

        registry.addMapping(properties.getPath())
                .allowedOrigins(corsConfig.getAllowedOrigins().toArray(new String[0]))
                .allowedMethods(corsConfig.getAllowedMethods().toArray(new String[0]))
                .allowedHeaders("*")
                .exposedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
