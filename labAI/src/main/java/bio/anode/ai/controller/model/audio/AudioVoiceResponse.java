package bio.anode.ai.controller.model.audio;

import java.util.Collections;
import java.util.List;

public record AudioVoiceResponse(List<Voice> voices) {

  public AudioVoiceResponse(String id, String name) {
    this(Collections.singletonList(new Voice(id, name)));
  }

  public record Voice(String id, String name) {
  }
}
