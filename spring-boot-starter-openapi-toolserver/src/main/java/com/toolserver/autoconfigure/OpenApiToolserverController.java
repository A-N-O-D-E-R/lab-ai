package com.toolserver.autoconfigure;

import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
public class OpenApiToolserverController {

    private final RestTemplate restTemplate;
    private final Environment environment;

    public OpenApiToolserverController(Environment environment) {
        this.restTemplate = new RestTemplate();
        this.environment = environment;
    }

    @GetMapping(value = "${toolserver.path:/openapi.json}", produces = MediaType.APPLICATION_JSON_VALUE)
    public String getOpenApiSpec() {
        // Get port and context path
        String port = environment.getProperty("server.port", "8080");
        String contextPath = environment.getProperty("server.servlet.context-path", "");

        // Fetch from springdoc's endpoint
        String url = "http://localhost:" + port + contextPath + "/v3/api-docs";

        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        return response.getBody();
    }
}
