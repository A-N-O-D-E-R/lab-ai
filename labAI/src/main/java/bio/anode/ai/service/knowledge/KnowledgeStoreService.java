package bio.anode.ai.service.knowledge;

import bio.anode.ai.config.KnowledgeProperties;
import bio.anode.ai.dao.VectorStoreFileRepository;
import bio.anode.ai.dao.VectorStoreRepository;
import bio.anode.ai.dao.jpa.VectorStoreEntity;
import bio.anode.ai.dao.jpa.VectorStoreFileEntity;
import bio.anode.ai.support.Ids;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Business logic behind the OpenAI Vector Stores API. Chunk vectors are stored in the pgvector
 * {@code okf_vectors} table (scoped by the {@code vector_store_id} metadata); this service manages the
 * OpenAI-facing store/file records and orchestrates OKF ingestion into the {@link VectorStore}.
 *
 * <p>All methods are blocking (JPA + JDBC + embedding calls); controllers offload to a
 * bounded-elastic scheduler.
 */
@Service
@Slf4j
public class KnowledgeStoreService {

    private final VectorStoreRepository storeRepo;
    private final VectorStoreFileRepository fileLinkRepo;
    private final FileService fileService;
    private final OkfParserService okfParser;
    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final KnowledgeProperties properties;

    public KnowledgeStoreService(VectorStoreRepository storeRepo,
                                 VectorStoreFileRepository fileLinkRepo,
                                 FileService fileService,
                                 OkfParserService okfParser,
                                 VectorStore vectorStore,
                                 JdbcTemplate jdbcTemplate,
                                 KnowledgeProperties properties) {
        this.storeRepo = storeRepo;
        this.fileLinkRepo = fileLinkRepo;
        this.fileService = fileService;
        this.okfParser = okfParser;
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    // --- store CRUD ------------------------------------------------------------------------------

    public VectorStoreEntity create(String name, List<String> fileIds) {
        var now = System.currentTimeMillis() / 1_000L;
        var store = storeRepo.save(VectorStoreEntity.builder()
                .id(Ids.generate("vs"))
                .name(name)
                .status("completed")
                .createdAt(now)
                .build());
        if (fileIds != null) {
            fileIds.forEach(fileId -> attachFile(store.getId(), fileId));
        }
        return storeRepo.findById(store.getId()).orElse(store);
    }

    public List<VectorStoreEntity> list() {
        return storeRepo.findAll();
    }

    public VectorStoreEntity get(String vectorStoreId) {
        return storeRepo.findById(vectorStoreId)
                .orElseThrow(() -> notFound(vectorStoreId));
    }

    public VectorStoreEntity modify(String vectorStoreId, String name) {
        var store = get(vectorStoreId);
        if (name != null) {
            store.setName(name);
        }
        return storeRepo.save(store);
    }

    @Transactional
    public void delete(String vectorStoreId) {
        var store = get(vectorStoreId);
        // Remove all chunk vectors for this store, then the association + store records.
        deleteChunks("vector_store_id", vectorStoreId, null);
        fileLinkRepo.deleteByVectorStoreId(vectorStoreId);
        storeRepo.delete(store);
    }

    // --- file attach / list / delete -------------------------------------------------------------

    public VectorStoreFileEntity attachFile(String vectorStoreId, String fileId) {
        var store = get(vectorStoreId);
        var file = fileService.get(fileId); // 404 if missing
        var now = System.currentTimeMillis() / 1_000L;

        var link = fileLinkRepo.findByVectorStoreIdAndFileId(vectorStoreId, fileId)
                .orElseGet(() -> VectorStoreFileEntity.builder()
                        .id(Ids.generate("vsf"))
                        .vectorStoreId(vectorStoreId)
                        .fileId(fileId)
                        .createdAt(now)
                        .build());
        link.setStatus("in_progress");
        fileLinkRepo.save(link);

        try {
            List<Document> chunks = okfParser.parse(file.getBytes(), file.getFilename(), vectorStoreId, fileId);
            if (chunks.isEmpty()) {
                throw new IllegalArgumentException(
                        "No OKF concept documents found in " + file.getFilename()
                        + " (need Markdown with 'type' frontmatter)");
            }
            vectorStore.add(chunks);
            link.setStatus("completed");
            link.setChunkCount(chunks.size());
            link.setLastError(null);
        } catch (RuntimeException ex) {
            link.setStatus("failed");
            link.setLastError(truncate(ex.getMessage()));
            fileLinkRepo.save(link);
            recomputeCounts(store);
            throw ex;
        }
        fileLinkRepo.save(link);
        recomputeCounts(store);
        return link;
    }

    public List<VectorStoreFileEntity> listFiles(String vectorStoreId) {
        get(vectorStoreId);
        return fileLinkRepo.findByVectorStoreId(vectorStoreId);
    }

    public VectorStoreFileEntity getFile(String vectorStoreId, String fileId) {
        return fileLinkRepo.findByVectorStoreIdAndFileId(vectorStoreId, fileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No such file " + fileId + " in vector store " + vectorStoreId));
    }

    @Transactional
    public void deleteFile(String vectorStoreId, String fileId) {
        var link = getFile(vectorStoreId, fileId);
        deleteChunks("vector_store_id", vectorStoreId, fileId);
        fileLinkRepo.delete(link);
        storeRepo.findById(vectorStoreId).ifPresent(this::recomputeCounts);
    }

    // --- search ----------------------------------------------------------------------------------

    public List<Document> search(String vectorStoreId, String query, Integer maxNumResults) {
        get(vectorStoreId);
        var topK = maxNumResults != null && maxNumResults > 0 ? maxNumResults : properties.defaultTopK();
        var sr = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(properties.defaultThreshold())
                .filterExpression("vector_store_id == '" + vectorStoreId + "'")
                .build();
        return vectorStore.similaritySearch(sr);
    }

    // --- helpers ---------------------------------------------------------------------------------

    /** Delete chunk rows from the pgvector table by JSON metadata (Spring AI delete is id-based only). */
    private void deleteChunks(String vsKey, String vectorStoreId, String fileId) {
        var table = properties.vectorTableName();
        if (fileId == null) {
            jdbcTemplate.update(
                    "DELETE FROM " + table + " WHERE metadata->>'" + vsKey + "' = ?",
                    vectorStoreId);
        } else {
            jdbcTemplate.update(
                    "DELETE FROM " + table + " WHERE metadata->>'" + vsKey + "' = ? AND metadata->>'file_id' = ?",
                    vectorStoreId, fileId);
        }
    }

    private void recomputeCounts(VectorStoreEntity store) {
        var links = fileLinkRepo.findByVectorStoreId(store.getId());
        int completed = (int) links.stream().filter(l -> "completed".equals(l.getStatus())).count();
        int inProgress = (int) links.stream().filter(l -> "in_progress".equals(l.getStatus())).count();
        int failed = (int) links.stream().filter(l -> "failed".equals(l.getStatus())).count();
        store.setFileCountCompleted(completed);
        store.setFileCountInProgress(inProgress);
        store.setFileCountFailed(failed);
        store.setFileCountTotal(links.size());
        storeRepo.save(store);
    }

    private ResponseStatusException notFound(String vectorStoreId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "No such vector store: " + vectorStoreId);
    }

    private String truncate(String s) {
        if (s == null) return null;
        return s.length() > 1900 ? s.substring(0, 1900) : s;
    }
}
