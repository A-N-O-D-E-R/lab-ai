package bio.anode.ai.config;

import bio.anode.ai.chat.memory.CaffeineChatMemoryRepository;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Chat memory wiring. The per-provider {@code ChatClient}s are built lazily in
 * {@code ChatClientRegistry} from whichever provider {@code ChatModel} beans the Spring AI
 * auto-configurations contribute (all provider chat auto-configs use {@code spring.ai.model.chat}
 * with {@code matchIfMissing=true}, so every provider on the classpath registers its model).
 */
@Configuration(proxyBeanMethods = false)
public class ChatConfig {

    @Bean
    ChatMemoryRepository chatMemoryRepository() {
        return new CaffeineChatMemoryRepository(1000, Duration.ofMinutes(5));
    }
}
