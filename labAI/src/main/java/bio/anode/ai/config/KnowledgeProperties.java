package bio.anode.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Knowledge-base (RAG) configuration, bound under {@code labai.knowledge.*}.
 *
 * @param embeddingModel   registry model id (from {@code labai.models}) used to embed knowledge;
 *                         its provider selects the concrete {@link org.springframework.ai.embedding.EmbeddingModel}.
 * @param dimensions       embedding dimension — MUST match {@code embeddingModel} and is fixed once the
 *                         pgvector table is created (openai text-embedding-3-small = 1536, titan v2 = 1024).
 * @param vectorTableName  pgvector table name (kept distinct from the OpenAI {@code vector_store} JPA table).
 * @param defaultTopK      default number of chunks retrieved per query.
 * @param defaultThreshold default similarity threshold.
 * @param chunkSize        TokenTextSplitter target chunk size (tokens).
 * @param minChunkSizeChars TokenTextSplitter minimum chunk size (chars).
 * @param maxUploadEntries safety cap on markdown entries processed per uploaded bundle.
 */
@ConfigurationProperties(prefix = "labai.knowledge")
public record KnowledgeProperties(
        String embeddingModel,
        Integer dimensions,
        String vectorTableName,
        Integer defaultTopK,
        Double defaultThreshold,
        Integer chunkSize,
        Integer minChunkSizeChars,
        Integer maxUploadEntries) {

    public KnowledgeProperties {
        dimensions = dimensions == null ? 1536 : dimensions;
        vectorTableName = vectorTableName == null ? "okf_vectors" : vectorTableName;
        defaultTopK = defaultTopK == null ? 4 : defaultTopK;
        defaultThreshold = defaultThreshold == null ? 0.5 : defaultThreshold;
        chunkSize = chunkSize == null ? 800 : chunkSize;
        minChunkSizeChars = minChunkSizeChars == null ? 350 : minChunkSizeChars;
        maxUploadEntries = maxUploadEntries == null ? 5000 : maxUploadEntries;
    }
}
