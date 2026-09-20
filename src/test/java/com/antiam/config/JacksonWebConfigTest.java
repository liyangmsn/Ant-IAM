package com.antiam.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class JacksonWebConfigTest {

    private final JsonMapper mapper = JsonMapper.builder()
        .addModule(JacksonWebConfig.antIamWebJavaTimeModule())
        .build();

    @Test
    void serializesInstantAsLocalDateTimeText() {
        String json = mapper.writeValueAsString(new InstantPayload(Instant.parse("2026-06-25T08:03:04Z")));

        assertThat(json).isEqualTo("{\"createdAt\":\"2026-06-25 16:03:04\"}");
    }

    @Test
    void deserializesInstantFromLocalDateTimeText() {
        InstantPayload payload = mapper.readValue("{\"createdAt\":\"2026-06-25 16:03:04\"}", InstantPayload.class);

        assertThat(payload.createdAt()).isEqualTo(Instant.parse("2026-06-25T08:03:04Z"));
    }

    @Test
    void keepsIsoInstantInputCompatible() {
        InstantPayload payload = mapper.readValue("{\"createdAt\":\"2026-06-25T08:03:04Z\"}", InstantPayload.class);

        assertThat(payload.createdAt()).isEqualTo(Instant.parse("2026-06-25T08:03:04Z"));
    }

    private record InstantPayload(Instant createdAt) {
    }
}
