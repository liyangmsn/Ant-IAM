package com.antiam.config;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

@Configuration
public class JacksonWebConfig {

    static final ZoneId JSON_TIME_ZONE = ZoneId.of("Asia/Shanghai");

    static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    JsonMapperBuilderCustomizer antIamJsonMapperBuilderCustomizer() {
        return builder -> builder.addModule(antIamWebJavaTimeModule());
    }

    static SimpleModule antIamWebJavaTimeModule() {
        SimpleModule module = new SimpleModule("antIamWebJavaTimeModule");
        module.addSerializer(Instant.class, new InstantDateTimeSerializer());
        module.addDeserializer(Instant.class, new InstantDateTimeDeserializer());
        module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer());
        module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer());
        module.addSerializer(OffsetDateTime.class, new OffsetDateTimeSerializer());
        module.addDeserializer(OffsetDateTime.class, new OffsetDateTimeDeserializer());
        module.addSerializer(ZonedDateTime.class, new ZonedDateTimeSerializer());
        module.addDeserializer(ZonedDateTime.class, new ZonedDateTimeDeserializer());
        return module;
    }

    private static String formatInstant(Instant value) {
        return DATE_TIME_FORMATTER.format(value.atZone(JSON_TIME_ZONE));
    }

    private static String formatOffsetDateTime(OffsetDateTime value) {
        return DATE_TIME_FORMATTER.format(value.atZoneSameInstant(JSON_TIME_ZONE));
    }

    private static String formatZonedDateTime(ZonedDateTime value) {
        return DATE_TIME_FORMATTER.format(value.withZoneSameInstant(JSON_TIME_ZONE));
    }

    private static Instant parseInstant(JsonParser parser) throws JacksonException {
        String value = parser.getValueAsString();
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return LocalDateTime.parse(trimmed, DATE_TIME_FORMATTER).atZone(JSON_TIME_ZONE).toInstant();
        }
        catch (DateTimeParseException ignored) {
            // Fall through to legacy ISO formats.
        }
        try {
            return Instant.parse(trimmed);
        }
        catch (DateTimeParseException ignored) {
            // Fall through to offset and zoned formats.
        }
        try {
            return OffsetDateTime.parse(trimmed).toInstant();
        }
        catch (DateTimeParseException ignored) {
            // Fall through to zoned format.
        }
        try {
            return ZonedDateTime.parse(trimmed).toInstant();
        }
        catch (DateTimeParseException ex) {
            throw DatabindException.from(parser, "Invalid date time: " + value, ex);
        }
    }

    private static final class InstantDateTimeSerializer extends ValueSerializer<Instant> {

        @Override
        public void serialize(Instant value, JsonGenerator generator, SerializationContext context)
                throws JacksonException {
            generator.writeString(formatInstant(value));
        }
    }

    private static final class InstantDateTimeDeserializer extends ValueDeserializer<Instant> {

        @Override
        public Instant deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
            return parseInstant(parser);
        }
    }

    private static final class LocalDateTimeSerializer extends ValueSerializer<LocalDateTime> {

        @Override
        public void serialize(LocalDateTime value, JsonGenerator generator, SerializationContext context)
                throws JacksonException {
            generator.writeString(value.format(DATE_TIME_FORMATTER));
        }
    }

    private static final class LocalDateTimeDeserializer extends ValueDeserializer<LocalDateTime> {

        @Override
        public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
            String value = parser.getValueAsString();
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                return LocalDateTime.parse(value.trim(), DATE_TIME_FORMATTER);
            }
            catch (DateTimeParseException ex) {
                return LocalDateTime.ofInstant(parseInstant(parser), JSON_TIME_ZONE);
            }
        }
    }

    private static final class OffsetDateTimeSerializer extends ValueSerializer<OffsetDateTime> {

        @Override
        public void serialize(OffsetDateTime value, JsonGenerator generator, SerializationContext context)
                throws JacksonException {
            generator.writeString(formatOffsetDateTime(value));
        }
    }

    private static final class OffsetDateTimeDeserializer extends ValueDeserializer<OffsetDateTime> {

        @Override
        public OffsetDateTime deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
            Instant instant = parseInstant(parser);
            return instant == null ? null : instant.atZone(JSON_TIME_ZONE).toOffsetDateTime();
        }
    }

    private static final class ZonedDateTimeSerializer extends ValueSerializer<ZonedDateTime> {

        @Override
        public void serialize(ZonedDateTime value, JsonGenerator generator, SerializationContext context)
                throws JacksonException {
            generator.writeString(formatZonedDateTime(value));
        }
    }

    private static final class ZonedDateTimeDeserializer extends ValueDeserializer<ZonedDateTime> {

        @Override
        public ZonedDateTime deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
            Instant instant = parseInstant(parser);
            return instant == null ? null : instant.atZone(JSON_TIME_ZONE);
        }
    }
}
