package bio.anode.ai.service.knowledge;

import bio.anode.ai.config.KnowledgeProperties;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class OkfParserServiceTest {

    private final OkfParserService parser = new OkfParserService(
            new KnowledgeProperties("labai-openai-embed", 1536, "okf_vectors", 4, 0.5, 800, 350, 5000));

    private static final String CONCEPT_MD = """
            ---
            type: BigQuery Table
            title: Customer Orders
            description: One row per completed customer order.
            tags: [sales, orders, revenue]
            timestamp: 2026-05-28T14:30:00Z
            ---
            # Schema
            The orders table has columns id, customer_id, total.

            # Examples
            SELECT * FROM orders WHERE total > 100;
            """;

    @Test
    void parsesSingleMarkdownWithFrontmatter() {
        var docs = parser.parse(CONCEPT_MD.getBytes(StandardCharsets.UTF_8), "orders.md", "vs-123", "file-abc");

        assertFalse(docs.isEmpty(), "should produce at least one chunk");
        var meta = docs.get(0).getMetadata();
        assertEquals("vs-123", meta.get("vector_store_id"));
        assertEquals("file-abc", meta.get("file_id"));
        assertEquals("BigQuery Table", meta.get("okf_type"));
        assertEquals("Customer Orders", meta.get("okf_title"));
        assertEquals("sales,orders,revenue", meta.get("okf_tags"));
        assertEquals("orders.md", meta.get("source_path"));
        assertTrue(docs.stream().anyMatch(d -> d.getText().contains("orders table")),
                "body content should be chunked and retained");
    }

    @Test
    void skipsEntryMissingRequiredType() {
        var noType = """
                ---
                title: No type here
                ---
                Body text.
                """;
        var docs = parser.parse(noType.getBytes(StandardCharsets.UTF_8), "bad.md", "vs-1", "file-1");
        assertTrue(docs.isEmpty(), "entry without 'type' frontmatter must be skipped");
    }

    @Test
    void skipsContentWithoutFrontmatter() {
        var docs = parser.parse("Just plain text, no frontmatter.".getBytes(StandardCharsets.UTF_8),
                "plain.md", "vs-1", "file-1");
        assertTrue(docs.isEmpty());
    }

    @Test
    void skipsReservedIndexAndLogFiles() throws Exception {
        var zipBytes = zip(
                entry("index.md", "# Directory\n* [x](x.md)"),
                entry("log.md", "# Log\n## 2026-05-22\n* created"),
                entry("concept.md", CONCEPT_MD));
        var docs = parser.parse(zipBytes, "bundle.zip", "vs-9", "file-9");

        assertFalse(docs.isEmpty());
        assertTrue(docs.stream().allMatch(d -> "concept.md".equals(d.getMetadata().get("source_path"))),
                "only the concept doc should be ingested; index.md/log.md skipped");
    }

    // --- helpers ---

    private record NamedEntry(String name, String content) {
    }

    private NamedEntry entry(String name, String content) {
        return new NamedEntry(name, content);
    }

    private byte[] zip(NamedEntry... entries) throws Exception {
        var bos = new ByteArrayOutputStream();
        try (var zos = new ZipOutputStream(bos)) {
            for (var e : entries) {
                zos.putNextEntry(new ZipEntry(e.name()));
                zos.write(e.content().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return bos.toByteArray();
    }
}
