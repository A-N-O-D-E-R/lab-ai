package bio.anode.ai.chat.memory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class CaffeineChatMemoryRepository implements ChatMemoryRepository {

    private final Cache<String, List<Message>> chatMemoryStore;

    public CaffeineChatMemoryRepository(int maxConversations, Duration expireAfter) {
        this.chatMemoryStore = Caffeine.newBuilder()
                .maximumSize(maxConversations)
                .expireAfterWrite(expireAfter)
                .build();
    }

    @Override
    public List<String> findConversationIds() {
        return new ArrayList<>(chatMemoryStore.asMap().keySet());
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        List<Message> messages = this.chatMemoryStore.getIfPresent(conversationId);
        return messages != null ? new ArrayList<>(messages) : List.of();
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        Assert.notNull(messages, "messages cannot be null");
        Assert.noNullElements(messages, "messages cannot contain null elements");
        this.chatMemoryStore.put(conversationId, messages);
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        this.chatMemoryStore.invalidate(conversationId);
    }
}
