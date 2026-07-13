package bio.anode.ai.controller;

import bio.anode.ai.controller.model.common.DeletedObject;
import bio.anode.ai.controller.model.common.ListResponse;
import bio.anode.ai.controller.model.vectorstores.*;
import bio.anode.ai.service.knowledge.KnowledgeStoreService;
import bio.anode.ai.support.NotImplementedException;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * OpenAI Vector Stores API. Backed by a pgvector store + OKF ingestion (see
 * {@link KnowledgeStoreService}); distinct from the Spring AI {@code VectorStore} bean it uses
 * internally. File-batch endpoints remain 501. Blocking work is offloaded to bounded-elastic.
 */
@RestController
@RequestMapping("/api/v1/vector_stores")
@RequiredArgsConstructor
public class VectorStoresController {

    private final KnowledgeStoreService service;

    // --- store CRUD ------------------------------------------------------------------------------

    @PostMapping
    public Mono<VectorStoreResponse> create(@RequestBody(required = false) CreateVectorStoreRequest request) {
        var name = request == null ? null : request.name();
        var fileIds = request == null ? null : request.fileIds();
        return blocking(() -> VectorStoreResponse.from(service.create(name, fileIds)));
    }

    @GetMapping
    public Mono<ListResponse<VectorStoreResponse>> list() {
        return blocking(() -> new ListResponse<>(
                service.list().stream().map(VectorStoreResponse::from).toList()));
    }

    @GetMapping("/{vectorStoreId}")
    public Mono<VectorStoreResponse> retrieve(@PathVariable String vectorStoreId) {
        return blocking(() -> VectorStoreResponse.from(service.get(vectorStoreId)));
    }

    @PostMapping("/{vectorStoreId}")
    public Mono<VectorStoreResponse> modify(@PathVariable String vectorStoreId,
                                            @RequestBody(required = false) ModifyVectorStoreRequest request) {
        var name = request == null ? null : request.name();
        return blocking(() -> VectorStoreResponse.from(service.modify(vectorStoreId, name)));
    }

    @DeleteMapping("/{vectorStoreId}")
    public Mono<DeletedObject> delete(@PathVariable String vectorStoreId) {
        return blocking(() -> {
            service.delete(vectorStoreId);
            return new DeletedObject(vectorStoreId, "vector_store.deleted", true);
        });
    }

    @PostMapping("/{vectorStoreId}/search")
    public Mono<VectorStoreSearchResponse> search(@PathVariable String vectorStoreId,
                                                  @RequestBody VectorStoreSearchRequest request) {
        return blocking(() -> {
            List<Document> results = service.search(vectorStoreId, request.query(), request.maxNumResults());
            var data = results.stream().map(this::toResult).toList();
            return new VectorStoreSearchResponse(request.query(), data);
        });
    }

    // --- files subresource -----------------------------------------------------------------------

    @PostMapping("/{vectorStoreId}/files")
    public Mono<VectorStoreFileResponse> attachFile(@PathVariable String vectorStoreId,
                                                    @RequestBody AttachFileRequest request) {
        return blocking(() -> VectorStoreFileResponse.from(service.attachFile(vectorStoreId, request.fileId())));
    }

    @GetMapping("/{vectorStoreId}/files")
    public Mono<ListResponse<VectorStoreFileResponse>> listFiles(@PathVariable String vectorStoreId) {
        return blocking(() -> new ListResponse<>(
                service.listFiles(vectorStoreId).stream().map(VectorStoreFileResponse::from).toList()));
    }

    @GetMapping("/{vectorStoreId}/files/{fileId}")
    public Mono<VectorStoreFileResponse> getFile(@PathVariable String vectorStoreId, @PathVariable String fileId) {
        return blocking(() -> VectorStoreFileResponse.from(service.getFile(vectorStoreId, fileId)));
    }

    @DeleteMapping("/{vectorStoreId}/files/{fileId}")
    public Mono<DeletedObject> deleteFile(@PathVariable String vectorStoreId, @PathVariable String fileId) {
        return blocking(() -> {
            service.deleteFile(vectorStoreId, fileId);
            return new DeletedObject(fileId, "vector_store.file.deleted", true);
        });
    }

    @GetMapping("/{vectorStoreId}/files/{fileId}/content")
    public Object fileContent(@PathVariable String vectorStoreId, @PathVariable String fileId) {
        throw new NotImplementedException("Vector store file content retrieval is not implemented");
    }

    // --- file batches (not implemented) ----------------------------------------------------------

    @PostMapping("/{vectorStoreId}/file_batches")
    public Object createFileBatch(@PathVariable String vectorStoreId, @RequestBody(required = false) Object request) {
        throw new NotImplementedException("The Vector Store file batches API is not implemented");
    }

    @GetMapping("/{vectorStoreId}/file_batches/{batchId}")
    public Object retrieveFileBatch(@PathVariable String vectorStoreId, @PathVariable String batchId) {
        throw new NotImplementedException("The Vector Store file batches API is not implemented");
    }

    @PostMapping("/{vectorStoreId}/file_batches/{batchId}/cancel")
    public Object cancelFileBatch(@PathVariable String vectorStoreId, @PathVariable String batchId) {
        throw new NotImplementedException("The Vector Store file batches API is not implemented");
    }

    @GetMapping("/{vectorStoreId}/file_batches/{batchId}/files")
    public Object listFileBatchFiles(@PathVariable String vectorStoreId, @PathVariable String batchId) {
        throw new NotImplementedException("The Vector Store file batches API is not implemented");
    }

    // --- helpers ---------------------------------------------------------------------------------

    private VectorStoreSearchResponse.Result toResult(Document d) {
        var meta = d.getMetadata();
        return new VectorStoreSearchResponse.Result(
                str(meta.get("file_id")),
                str(meta.get("okf_title")),
                d.getScore(),
                List.of(new VectorStoreSearchResponse.Content(d.getText())));
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private <T> Mono<T> blocking(java.util.concurrent.Callable<T> work) {
        return Mono.fromCallable(work).subscribeOn(Schedulers.boundedElastic());
    }
}
