package bio.anode.ai.support;

import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

import java.io.IOException;

/** Buffers a reactive multipart {@link FilePart} into an in-memory byte array. */
public final class MultipartSupport {

    private MultipartSupport() {
    }

    public static Mono<byte[]> toBytes(FilePart file) {
        return DataBufferUtils.join(file.content())
                .map(dataBuffer -> {
                    try (var in = dataBuffer.asInputStream(true)) {
                        return in.readAllBytes();
                    } catch (IOException e) {
                        throw new IllegalArgumentException("Could not read uploaded file", e);
                    }
                });
    }
}
