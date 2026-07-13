package bio.anode.ai.service.knowledge;

import bio.anode.ai.dao.FileObjectRepository;
import bio.anode.ai.dao.jpa.FileObjectEntity;
import bio.anode.ai.support.Ids;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Backs the OpenAI Files API. Stores uploaded file bytes + metadata in Postgres.
 * All methods are blocking (JPA); callers offload to a bounded-elastic scheduler.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileService {

    private final FileObjectRepository repository;

    public FileObjectEntity upload(byte[] bytes, String filename, String purpose) {
        var now = System.currentTimeMillis() / 1_000L;
        var entity = FileObjectEntity.builder()
                .id(Ids.generate("file"))
                .filename(filename)
                .bytes(bytes)
                .bytesSize(bytes.length)
                .purpose(purpose == null ? "assistants" : purpose)
                .status("processed")
                .createdAt(now)
                .build();
        var saved = repository.save(entity);
        log.info("Stored file {} ({} bytes, purpose={})", saved.getId(), saved.getBytesSize(), saved.getPurpose());
        return saved;
    }

    public List<FileObjectEntity> list() {
        return repository.findAll();
    }

    public FileObjectEntity get(String fileId) {
        return repository.findById(fileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such file: " + fileId));
    }

    public byte[] content(String fileId) {
        return get(fileId).getBytes();
    }

    public void delete(String fileId) {
        if (!repository.existsById(fileId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such file: " + fileId);
        }
        repository.deleteById(fileId);
    }
}
