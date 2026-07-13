# Spring Boot OpenAPI Toolserver Starter

Transform any Spring Boot REST API into an AI-ready OpenAPI toolserver by just adding one dependency.

## What is an OpenAPI Toolserver?

An OpenAPI toolserver is an HTTP API that exposes its functionality through a standardized OpenAPI specification, making it discoverable and callable by AI agents and tools like OpenWebUI, LangChain, AutoGPT, and other AI frameworks.

## Features

- **Zero-config setup** - Just add the dependency and you're done
- **`/openapi.json` endpoint** - Standard toolserver endpoint for AI agent discovery
- **AI-friendly descriptions** - Automatically enhances OpenAPI descriptions for better AI understanding
- **CORS enabled by default** - External tools can access your API immediately
- **Startup banner** - Logs the toolserver URL on application startup
- **Fully customizable** - Override defaults via `application.yml` properties

## Quick Start

### 1. Add the Dependency

**Maven:**
```xml
<dependency>
    <groupId>com.toolserver</groupId>
    <artifactId>spring-boot-starter-openapi-toolserver</artifactId>
    <version>1.0.0</version>
</dependency>
```

**Gradle:**
```gradle
implementation 'com.toolserver:spring-boot-starter-openapi-toolserver:1.0.0'
```

### 2. Run Your Application

```bash
mvn spring-boot:run
```

You'll see the banner on startup:

```
================================================================================
🤖 OpenAPI Toolserver available at: http://localhost:8080/openapi.json
================================================================================

Add this URL to OpenWebUI or other AI tools to use your API as a toolserver.
```

### 3. Test the Endpoint

```bash
curl http://localhost:8080/openapi.json
```

### 4. Connect to OpenWebUI

1. Open OpenWebUI settings
2. Navigate to **Tools** section
3. Add tool server: `http://localhost:8080`
4. Your API endpoints are now available as tools in chat!

## How It Works

This starter:

1. **Depends on springdoc-openapi** to generate OpenAPI specs from your `@RestController` endpoints
2. **Exposes `/openapi.json`** endpoint serving the enhanced OpenAPI specification
3. **Enhances descriptions** with AI-friendly context (e.g., "Use this to get user details")
4. **Configures CORS** to allow external AI tools to access the API
5. **Logs startup message** showing the toolserver URL

## Configuration

All configuration is optional. The starter works with zero configuration.

### Available Properties

```yaml
toolserver:
  enabled: true                    # Enable/disable the toolserver
  path: /openapi.json              # Endpoint path for OpenAPI spec
  
  cors:
    enabled: true                  # Enable/disable CORS
    allowed-origins:               # Allowed origins (default: *)
      - "*"
    allowed-methods:               # Allowed HTTP methods
      - GET
      - POST
      - PUT
      - DELETE
      - OPTIONS
  
  enhancement:
    enabled: true                  # Enable AI-friendly enhancements
    context-prefix: "Use this to " # Prefix added to operation summaries
```

### Example Configurations

**Restrict CORS origins:**
```yaml
toolserver:
  cors:
    allowed-origins:
      - http://localhost:3000
      - https://openwebui.example.com
```

**Change endpoint path:**
```yaml
toolserver:
  path: /api/tools.json
```

**Disable enhancements:**
```yaml
toolserver:
  enhancement:
    enabled: false
```

**Completely disable toolserver:**
```yaml
toolserver:
  enabled: false
```

## Example: Before and After

**Your original controller:**
```java
@RestController
@RequestMapping("/api/users")
public class UserController {
    
    @GetMapping("/{id}")
    public User getUser(@PathVariable Long id) {
        return userService.findById(id);
    }
    
    @PostMapping
    public User createUser(@RequestBody User user) {
        return userService.create(user);
    }
}
```

**Without toolserver starter:**
- OpenAPI spec at `/v3/api-docs` (not the standard toolserver path)
- Generic descriptions: "Get user", "Create user"
- No CORS configuration
- Manual setup required for AI tools

**With toolserver starter:**
- OpenAPI spec at `/openapi.json` ✅
- Enhanced descriptions: "Use this to get user by ID", "Use this to create a new user" ✅
- CORS enabled automatically ✅
- Works immediately with OpenWebUI and AI agents ✅
- Startup banner shows the URL ✅

## Requirements

- **Java**: 17+
- **Spring Boot**: 3.x (tested with 3.3.1)
- **Spring Web**: Your application must have `spring-boot-starter-web`

## How AI Agents Use Your API

Once you add your toolserver URL to an AI tool like OpenWebUI:

1. **Discovery**: The AI reads `/openapi.json` to discover available endpoints
2. **Understanding**: Enhanced descriptions help the AI understand when to use each endpoint
3. **Invocation**: When a user asks a question, the AI calls your API endpoints automatically
4. **Response**: The AI formats the API response into a natural language answer

**Example conversation:**

```
User: "What are the details for user 123?"
↓
AI reads OpenAPI spec and finds: GET /api/users/{id}
↓
AI calls: http://localhost:8080/api/users/123
↓
AI responds: "User 123 is Alice Smith, email: alice@example.com, registered on..."
```

## Architecture

```
┌─────────────────────┐
│   Your Spring Boot  │
│   REST Controllers  │
└──────────┬──────────┘
           │
           ├─────────────────────────────────┐
           │                                 │
           ▼                                 ▼
┌──────────────────────┐      ┌──────────────────────────┐
│   springdoc-openapi  │      │  Toolserver Starter      │
│                      │      │                          │
│ • Scans controllers  │      │ • Enhances descriptions  │
│ • Generates OpenAPI  │◄─────┤ • Adds /openapi.json     │
│ • Creates /v3/api... │      │ • Configures CORS        │
└──────────────────────┘      │ • Logs startup banner    │
                              └──────────────────────────┘
                                         │
                                         ▼
                              ┌──────────────────────┐
                              │  /openapi.json       │
                              │  (AI-ready spec)     │
                              └──────────────────────┘
                                         │
                                         ▼
                              ┌──────────────────────┐
                              │  OpenWebUI / AI Tool │
                              └──────────────────────┘
```

## Advanced Usage

### Custom OpenAPI Customization

You can add your own OpenAPI customizations alongside the toolserver enhancements:

```java
@Bean
public OpenAPI customOpenAPI() {
    return new OpenAPI()
        .info(new Info()
            .title("My API")
            .version("1.0.0")
            .description("My awesome API as a toolserver"));
}
```

### Programmatic Configuration

```java
@Configuration
public class MyToolserverConfig {
    
    @Bean
    public ToolserverProperties customToolserverProperties() {
        ToolserverProperties props = new ToolserverProperties();
        props.setPath("/tools.json");
        props.getEnhancement().setContextPrefix("API call to ");
        return props;
    }
}
```

## Troubleshooting

### Toolserver endpoint not available

**Check that:**
- Spring Boot version is 3.x
- `spring-boot-starter-web` is in your dependencies
- At least one `@RestController` exists in your application
- `toolserver.enabled=true` (or not set, as it defaults to true)

### CORS errors in browser

**Solution:**
```yaml
toolserver:
  cors:
    allowed-origins:
      - http://localhost:3000  # Add your frontend origin
```

### OpenAPI spec is empty

**Ensure:**
- You have `@RestController` classes with mapping annotations
- Controllers are in a package scanned by Spring Boot
- springdoc-openapi is working (check `/v3/api-docs`)

## Comparison with Manual Setup

| Feature | Manual Setup | With Toolserver Starter |
|---------|-------------|------------------------|
| Add dependencies | ✅ Manual | ✅ Automatic |
| Configure OpenAPI endpoint | ⚙️ Manual | ✅ Automatic |
| Set up CORS | ⚙️ Manual | ✅ Automatic |
| Enhance descriptions for AI | ❌ Manual work | ✅ Automatic |
| Startup logging | ❌ None | ✅ Shows URL |
| Configuration | ⚙️ Complex | ✅ Simple properties |
| Works immediately | ❌ No | ✅ Yes |

## Examples

See the `examples/` directory for complete working examples:

- `simple-api/` - Basic REST API transformed into a toolserver
- `complex-api/` - Multi-controller API with custom configuration

## License

MIT License

## Contributing

Contributions welcome! Please open an issue or PR.

## Support

For issues and questions, please use the GitHub issue tracker.

---

**Transform any Spring Boot API into an AI-ready toolserver - just add one dependency! 🚀**
