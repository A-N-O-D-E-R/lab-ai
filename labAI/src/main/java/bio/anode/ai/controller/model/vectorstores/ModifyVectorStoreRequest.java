package bio.anode.ai.controller.model.vectorstores;

/** OpenAI modify-vector-store request (only {@code name} is honored). */
public record ModifyVectorStoreRequest(String name) {
}
