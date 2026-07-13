package com.toolserver.autoconfigure;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.core.env.Environment;

import java.util.Map;

public class OpenApiEnhancer implements OpenApiCustomizer {

    private final ToolserverProperties properties;
    private final Environment environment;

    public OpenApiEnhancer(ToolserverProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void customise(OpenAPI openApi) {
        if (!properties.getEnhancement().isEnabled()) {
            return;
        }

        // Add dynamic server URL
        addServerUrl(openApi);

        // Enhance operation descriptions
        if (openApi.getPaths() != null) {
            openApi.getPaths().forEach((path, pathItem) -> {
                enhancePathItem(pathItem);
            });
        }
    }

    private void addServerUrl(OpenAPI openApi) {
        String port = environment.getProperty("server.port", "8080");
        String contextPath = environment.getProperty("server.servlet.context-path", "");
        String serverUrl = "http://localhost:" + port + contextPath;

        Server server = new Server();
        server.setUrl(serverUrl);
        server.setDescription("Local server");

        if (openApi.getServers() == null || openApi.getServers().isEmpty()) {
            openApi.addServersItem(server);
        }
    }

    private void enhancePathItem(PathItem pathItem) {
        enhanceOperation(pathItem.getGet());
        enhanceOperation(pathItem.getPost());
        enhanceOperation(pathItem.getPut());
        enhanceOperation(pathItem.getDelete());
        enhanceOperation(pathItem.getPatch());
    }

    private void enhanceOperation(Operation operation) {
        if (operation == null) {
            return;
        }

        // Enhance summary with AI-friendly context
        if (operation.getSummary() != null && !operation.getSummary().isEmpty()) {
            String summary = operation.getSummary();
            String prefix = properties.getEnhancement().getContextPrefix();

            // Only add prefix if not already present
            if (!summary.toLowerCase().startsWith(prefix.toLowerCase().trim())) {
                operation.setSummary(prefix + summary.substring(0, 1).toLowerCase() + summary.substring(1));
            }
        }

        // Enhance parameter descriptions
        if (operation.getParameters() != null) {
            for (Parameter param : operation.getParameters()) {
                enhanceParameter(param);
            }
        }

        // Ensure description is present
        if (operation.getDescription() == null || operation.getDescription().isEmpty()) {
            if (operation.getSummary() != null) {
                operation.setDescription(operation.getSummary());
            }
        }
    }

    private void enhanceParameter(Parameter param) {
        if (param.getDescription() == null || param.getDescription().isEmpty()) {
            // Add basic description based on parameter name and type
            String type = param.getSchema() != null ? param.getSchema().getType() : "parameter";
            String description = param.getName() + " (" + type + ")";

            if (Boolean.TRUE.equals(param.getRequired())) {
                description += " - required";
            }

            param.setDescription(description);
        }
    }
}
