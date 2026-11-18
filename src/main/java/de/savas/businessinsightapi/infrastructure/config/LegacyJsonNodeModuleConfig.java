package de.savas.businessinsightapi.infrastructure.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.module.SimpleModule;

/**
 * Spring Boot 4 serializes HTTP bodies with Jackson 3 ({@code tools.jackson}), while the domain and DTOs
 * use Jackson 2's {@link JsonNode} for free-form JSON (options, params, chart config, layouts...).
 * Jackson 3 cannot instantiate the abstract Jackson 2 type, so any request carrying a JSON object in those
 * fields failed with HTTP 500. This module bridges the two: JSON is passed through as a tree in both directions.
 */
@Configuration
public class LegacyJsonNodeModuleConfig {

    @Bean
    public JacksonModule legacyJsonNodeModule(ObjectMapper legacyMapper) {
        SimpleModule module = new SimpleModule("legacy-jackson2-jsonnode");

        module.addSerializer(JsonNode.class, new ValueSerializer<JsonNode>() {
            @Override
            public void serialize(JsonNode value, JsonGenerator gen, SerializationContext ctxt) {
                gen.writeRawValue(value.toString());
            }
        });

        module.addDeserializer(JsonNode.class, new ValueDeserializer<JsonNode>() {
            @Override
            public JsonNode deserialize(JsonParser p, DeserializationContext ctxt) {
                String json = ctxt.readTree(p).toString();
                try {
                    return legacyMapper.readTree(json);
                } catch (JsonProcessingException e) {
                    throw InvalidFormatException.from(p, "Invalid JSON value", json, JsonNode.class);
                }
            }
        });

        return module;
    }
}
