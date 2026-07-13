package bio.anode.ai.controller.model.common;

import java.util.List;

/** Generic OpenAI list envelope: {@code {"object":"list","data":[...]}}. */
public record ListResponse<T>(String object, List<T> data) {
    public ListResponse(List<T> data) {
        this("list", data);
    }
}
