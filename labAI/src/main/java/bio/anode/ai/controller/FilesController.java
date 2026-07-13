package bio.anode.ai.controller;

import bio.anode.ai.controller.model.common.DeletedObject;
import bio.anode.ai.controller.model.common.ListResponse;
import bio.anode.ai.controller.model.files.FileObjectResponse;
import bio.anode.ai.service.knowledge.FileService;
import bio.anode.ai.support.MultipartSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** OpenAI Files API — stores uploaded files (used as knowledge sources for vector stores). */
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FilesController {

    private final FileService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<FileObjectResponse> upload(
            @RequestPart("file") FilePart file,
            @RequestPart(value = "purpose", required = false) String purpose) {
        return MultipartSupport.toBytes(file)
                .publishOn(Schedulers.boundedElastic())
                .map(bytes -> FileObjectResponse.from(service.upload(bytes, file.filename(), purpose)));
    }

    @GetMapping
    public Mono<ListResponse<FileObjectResponse>> list() {
        return Mono.fromCallable(() -> new ListResponse<>(service.list().stream()
                        .map(FileObjectResponse::from)
                        .toList()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{fileId}")
    public Mono<FileObjectResponse> retrieve(@PathVariable String fileId) {
        return Mono.fromCallable(() -> FileObjectResponse.from(service.get(fileId)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @DeleteMapping("/{fileId}")
    public Mono<DeletedObject> delete(@PathVariable String fileId) {
        return Mono.fromCallable(() -> {
                    service.delete(fileId);
                    return new DeletedObject(fileId, "file", true);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{fileId}/content")
    public Mono<ResponseEntity<byte[]>> content(@PathVariable String fileId) {
        return Mono.fromCallable(() -> {
                    var entity = service.get(fileId);
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION,
                                    "attachment; filename=\"" + entity.getFilename() + "\"")
                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                            .body(entity.getBytes());
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
}
