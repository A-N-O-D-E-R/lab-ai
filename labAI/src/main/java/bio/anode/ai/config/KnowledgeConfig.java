package bio.anode.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Wires the knowledge-base vector store.
 *
 * <p>The knowledge {@link EmbeddingModel} is <em>pinned</em> via {@code labai.knowledge.embedding-model}
 * so ingestion and retrieval are deterministic even if more than one provider contributes an
 * {@code EmbeddingModel} bean. The {@link PgVectorStore} is defined explicitly (rather than relying on
 * the pgvector auto-configuration) so it binds to the pinned model, uses a dedicated table
 * ({@code okf_vectors}, distinct from the OpenAI {@code vector_store} JPA table), and so the
 * auto-configured store (which is {@code @ConditionalOnMissingBean}) backs off.
 */
@Configuration(proxyBeanMethods = false)
@Slf4j
public class KnowledgeConfig {

    public static final String KNOWLEDGE_EMBEDDING = "knowledgeEmbeddingModel";

    /**
     * Selects the {@link EmbeddingModel} used for knowledge embedding. Today only the OpenAI model is on
     * the classpath, so {@code getIfUnique} returns it; if multiple providers contribute embedding beans
     * in future this fails fast with a clear message so the provider can be pinned by qualifier.
     *
     * <p>{@code defaultCandidate = false} keeps this wrapper out of generic {@code EmbeddingModel}
     * injection points (e.g. the {@code /embeddings} service, the pgvector auto-config) so it does not
     * create its own ambiguity.
     */
    @Bean(name = KNOWLEDGE_EMBEDDING, defaultCandidate = false)
    @Qualifier(KNOWLEDGE_EMBEDDING)
    EmbeddingModel knowledgeEmbeddingModel(ObjectProvider<EmbeddingModel> embeddingModels,
                                           KnowledgeProperties properties) {
        var model = embeddingModels.getIfUnique();
        if (model == null) {
            var all = embeddingModels.stream().map(m -> m.getClass().getSimpleName()).toList();
            if (all.isEmpty()) {
                throw new IllegalStateException(
                        "labai.knowledge requires an EmbeddingModel provider on the classpath "
                        + "(e.g. configure spring.ai.openai.api-key). None found.");
            }
            throw new IllegalStateException(
                    "Multiple EmbeddingModel beans found " + all + "; pin one for "
                    + "labai.knowledge.embedding-model=" + properties.embeddingModel()
                    + " by qualifying the provider bean.");
        }
        log.info("Knowledge embedding model: {} (pinned id={}, dimensions={})",
                model.getClass().getSimpleName(), properties.embeddingModel(), properties.dimensions());
        return model;
    }

    @Bean
    VectorStore knowledgeVectorStore(JdbcTemplate jdbcTemplate,
                                     @Qualifier(KNOWLEDGE_EMBEDDING) EmbeddingModel knowledgeEmbeddingModel,
                                     KnowledgeProperties properties) {
        return PgVectorStore.builder(jdbcTemplate, knowledgeEmbeddingModel)
                .vectorTableName(properties.vectorTableName())
                .dimensions(properties.dimensions())
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(true)
                .build();
    }
}
