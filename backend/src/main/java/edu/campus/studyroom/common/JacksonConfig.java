package edu.campus.studyroom.common;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Jackson conventions:
 * - all Long IDs serialize as strings (JS safe integers);
 * - date-times serialize as ISO-8601 with +08:00 offset (Asia/Shanghai);
 * - request date-times MUST carry an explicit offset; naive input is rejected.
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter ISO_OFFSET =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXX");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer studyroomJacksonCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule();
            module.addSerializer(Long.class, new JsonSerializer<Long>() {
                @Override
                public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers)
                        throws IOException {
                    gen.writeString(value == null ? null : value.toString());
                }
            });
            module.addSerializer(long.class, new JsonSerializer<Long>() {
                @Override
                public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers)
                        throws IOException {
                    gen.writeString(value == null ? null : Long.toString(value));
                }
            });
            module.addSerializer(OffsetDateTime.class, new JsonSerializer<OffsetDateTime>() {
                @Override
                public void serialize(OffsetDateTime value, JsonGenerator gen, SerializerProvider serializers)
                        throws IOException {
                    gen.writeString(value.format(ISO_OFFSET));
                }
            });
            module.addDeserializer(OffsetDateTime.class, new JsonDeserializer<OffsetDateTime>() {
                @Override
                public OffsetDateTime deserialize(JsonParser p, DeserializationContext ctxt)
                        throws IOException {
                    String text = p.getValueAsString();
                    try {
                        // Requires an explicit offset such as +08:00; naive input is rejected.
                        return OffsetDateTime.parse(text);
                    } catch (DateTimeParseException e) {
                        throw ctxt.weirdStringException(text, OffsetDateTime.class,
                                "must be ISO-8601 with an explicit offset, e.g. 2026-10-15T09:00:00+08:00");
                    }
                }
            });
            builder.modules(module, new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        };
    }
}
