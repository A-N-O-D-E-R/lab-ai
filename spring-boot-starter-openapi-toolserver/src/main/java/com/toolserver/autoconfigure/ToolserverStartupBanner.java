package com.toolserver.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

public class ToolserverStartupBanner implements ApplicationListener<ApplicationStartedEvent> {

    private static final Logger logger = LoggerFactory.getLogger(ToolserverStartupBanner.class);

    private final ToolserverProperties properties;
    private final Environment environment;

    public ToolserverStartupBanner(ToolserverProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        if (!properties.isEnabled()) {
            return;
        }

        String port = environment.getProperty("server.port", "8080");
        String contextPath = environment.getProperty("server.servlet.context-path", "");
        String path = properties.getPath();
        String url = "http://localhost:" + port + contextPath + path;

        logger.info("");
        logger.info("=".repeat(80));
        logger.info("🤖 OpenAPI Toolserver available at: {}", url);
        logger.info("=".repeat(80));
        logger.info("");
        logger.info("Add this URL to OpenWebUI or other AI tools to use your API as a toolserver.");
        logger.info("");
    }
}
