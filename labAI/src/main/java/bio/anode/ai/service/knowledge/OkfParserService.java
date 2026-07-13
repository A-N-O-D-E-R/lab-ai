package bio.anode.ai.service.knowledge;

import bio.anode.ai.config.KnowledgeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipInputStream;

/**
 * Parses OKF (Open Knowledge Format) content into embeddable {@link Document} chunks.
 *
 * <p>OKF is a bundle of Markdown files with YAML frontmatter. Each concept document has a
 * {@code ---}-delimited YAML frontmatter block ({@code type} required; optional {@code title},
 * {@code description}, {@code resource}, {@code tags}, {@code timestamp} + arbitrary keys) followed by
 * a Markdown body. Reserved files {@code index.md}/{@code log.md} carry no frontmatter and are skipped.
 *
 * <p>Supported inputs (v1): a single {@code .md}/{@code .markdown} file, or a {@code .zip} bundle.
 * ({@code .tar}/{@code .tar.gz} would need commons-compress — not a dependency.)
 */
@Service
@Slf4j
public class OkfParserService {

    private final KnowledgeProperties properties;

    public OkfParserService(KnowledgeProperties properties) {
        this.properties = properties;
    }

    /**
     * Parse uploaded content into chunk Documents, each stamped with the scoping metadata
     * {@code vector_store_id} and {@code file_id}.
     */
    public List<Document> parse(byte[] content, String filename, String vectorStoreId, String fileId) {
        var entries = readEntries(content, filename);
        var splitter = TokenTextSplitter.builder()
                .withChunkSize(properties.chunkSize())
                .withMinChunkSizeChars(properties.minChunkSizeChars())
                .build();

        var chunks = new ArrayList<Document>();
        for (var entry : entries) {
            var parsed = parseConcept(entry.path(), entry.text());
            if (parsed == null) {
                continue; // reserved file or missing/invalid frontmatter
            }
            var baseMetadata = baseMetadata(parsed, vectorStoreId, fileId, entry.path());
            var bodyDoc = new Document(parsed.body(), baseMetadata);
            for (var chunk : splitter.split(bodyDoc)) {
                // TokenTextSplitter carries over metadata; ensure scoping keys are present.
                chunk.getMetadata().putAll(baseMetadata);
                chunks.add(chunk);
            }
        }
        log.info("OKF parse: file={} entries={} -> {} chunks (vs={})",
                filename, entries.size(), chunks.size(), vectorStoreId);
        return chunks;
    }

    // --- bundle handling -------------------------------------------------------------------------

    private record Entry(String path, String text) {
    }

    private List<Entry> readEntries(byte[] content, String filename) {
        var name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        var entries = new ArrayList<Entry>();
        if (name.endsWith(".zip")) {
            try (var zis = new ZipInputStream(new ByteArrayInputStream(content))) {
                var e = zis.getNextEntry();
                while (e != null) {
                    var entryName = e.getName();
                    if (!e.isDirectory() && isMarkdown(entryName)) {
                        entries.add(new Entry(entryName, new String(zis.readAllBytes(), StandardCharsets.UTF_8)));
                        if (entries.size() >= properties.maxUploadEntries()) {
                            log.warn("OKF bundle {} truncated at {} entries (labai.knowledge.max-upload-entries)",
                                    filename, properties.maxUploadEntries());
                            break;
                        }
                    }
                    zis.closeEntry();
                    e = zis.getNextEntry();
                }
            } catch (Exception ex) {
                throw new IllegalArgumentException("Could not read OKF .zip bundle: " + ex.getMessage(), ex);
            }
        } else {
            // Treat as a single markdown document.
            entries.add(new Entry(filename, new String(content, StandardCharsets.UTF_8)));
        }
        return entries;
    }

    private boolean isMarkdown(String path) {
        var p = path.toLowerCase(Locale.ROOT);
        return p.endsWith(".md") || p.endsWith(".markdown");
    }

    // --- concept parsing -------------------------------------------------------------------------

    private record Concept(Map<String, Object> frontmatter, String body) {
    }

    private Concept parseConcept(String path, String text) {
        var base = baseName(path);
        if ("index.md".equals(base) || "log.md".equals(base)) {
            return null; // reserved OKF files (no frontmatter)
        }
        var fm = splitFrontmatter(text);
        if (fm == null) {
            log.warn("Skipping OKF entry without YAML frontmatter: {}", path);
            return null;
        }
        Map<String, Object> frontmatter;
        try {
            Object loaded = new Yaml().load(fm.yaml());
            frontmatter = loaded instanceof Map<?, ?> m ? castMap(m) : new LinkedHashMap<>();
        } catch (Exception ex) {
            log.warn("Skipping OKF entry with unparseable frontmatter {}: {}", path, ex.getMessage());
            return null;
        }
        if (frontmatter.get("type") == null || String.valueOf(frontmatter.get("type")).isBlank()) {
            log.warn("Skipping OKF entry missing required 'type' frontmatter: {}", path);
            return null;
        }
        return new Concept(frontmatter, fm.body());
    }

    private record Frontmatter(String yaml, String body) {
    }

    /** Split a {@code ---}-delimited YAML frontmatter block from the markdown body. */
    private Frontmatter splitFrontmatter(String text) {
        var normalized = text.stripLeading().replace("\r\n", "\n");
        if (!normalized.startsWith("---\n") && !normalized.equals("---")) {
            return null;
        }
        // Content after the opening delimiter line.
        int afterOpen = normalized.indexOf('\n') + 1;
        int close = normalized.indexOf("\n---", afterOpen);
        if (close < 0) {
            return null;
        }
        var yaml = normalized.substring(afterOpen, close);
        // Body starts after the closing '---' line.
        int bodyStart = normalized.indexOf('\n', close + 1);
        var body = bodyStart < 0 ? "" : normalized.substring(bodyStart + 1).strip();
        return new Frontmatter(yaml, body);
    }

    private Map<String, Object> baseMetadata(Concept c, String vectorStoreId, String fileId, String path) {
        var meta = new LinkedHashMap<String, Object>();
        // Scoping keys (REQUIRED — sole isolation between knowledge bases in the shared table).
        meta.put("vector_store_id", vectorStoreId);
        meta.put("file_id", fileId);
        meta.put("source_path", path == null ? "" : path);
        // Well-known OKF frontmatter, namespaced to avoid clashes.
        putIfPresent(meta, "okf_type", c.frontmatter().get("type"));
        putIfPresent(meta, "okf_title", c.frontmatter().get("title"));
        putIfPresent(meta, "okf_description", c.frontmatter().get("description"));
        putIfPresent(meta, "okf_resource", c.frontmatter().get("resource"));
        putIfPresent(meta, "okf_tags", stringifyTags(c.frontmatter().get("tags")));
        putIfPresent(meta, "okf_timestamp", c.frontmatter().get("timestamp"));
        return meta;
    }

    private void putIfPresent(Map<String, Object> meta, String key, Object value) {
        if (value != null && !String.valueOf(value).isBlank()) {
            meta.put(key, String.valueOf(value));
        }
    }

    private String stringifyTags(Object tags) {
        if (tags instanceof List<?> list) {
            return String.join(",", list.stream().map(String::valueOf).toList());
        }
        return tags == null ? null : String.valueOf(tags);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Map<?, ?> m) {
        var out = new LinkedHashMap<String, Object>();
        m.forEach((k, v) -> out.put(String.valueOf(k), v));
        return out;
    }

    private String baseName(String path) {
        if (path == null) return "";
        var norm = path.replace('\\', '/');
        int slash = norm.lastIndexOf('/');
        return (slash < 0 ? norm : norm.substring(slash + 1)).toLowerCase(Locale.ROOT);
    }
}
