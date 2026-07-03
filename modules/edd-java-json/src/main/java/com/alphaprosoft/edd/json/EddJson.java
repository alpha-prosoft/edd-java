package com.alphaprosoft.edd.json;

import com.alphaprosoft.edd.command.Command;
import com.alphaprosoft.edd.command.CommandResponse;
import com.alphaprosoft.edd.command.Event;
import com.alphaprosoft.edd.command.Identity;
import com.alphaprosoft.edd.core.Aggregate;
import com.alphaprosoft.edd.core.TypeRegistry;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.Map;

/**
 * Storage codec producing Kubernetes-style entity documents — {@code {kind, name, meta?, spec}} —
 * with no Jackson annotations on the domain records. Any {@link Event}, {@link Identity}, {@link
 * Command} or {@link Aggregate} value is written as {@code {"kind":…, "name":…, "spec":{…}}}
 * (kind/name from {@link TypeRegistry}); on read the {@code (kind,name)} resolves the concrete
 * record class and {@code spec} is deserialized into it. Works standalone (an event row) and nested
 * (events inside a {@link CommandResponse}). Provenance {@code meta} is added/read as a sibling by
 * the store.
 */
public final class EddJson {

  /** Plain mapper for the {@code spec} body — no envelope, just the record's fields. */
  private static final ObjectMapper SPEC = JsonMapper.builder().build();

  private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {};

  public static final ObjectMapper MAPPER = build();

  private EddJson() {}

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "status")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = CommandResponse.Success.class, name = "success"),
    @JsonSubTypes.Type(value = CommandResponse.Failure.class, name = "failure")
  })
  private interface CommandResponseMixin {}

  private static ObjectMapper build() {
    SimpleModule module = new SimpleModule();
    addEnvelope(module, Event.class);
    addEnvelope(module, Identity.class);
    addEnvelope(module, Command.class);
    addEnvelope(module, Aggregate.class);
    ObjectMapper mapper = JsonMapper.builder().build();
    mapper.registerModule(module);
    mapper.addMixIn(CommandResponse.class, CommandResponseMixin.class);
    return mapper;
  }

  private static <B> void addEnvelope(SimpleModule module, Class<B> base) {
    module.addSerializer(
        base,
        new JsonSerializer<B>() {
          @Override
          public void serialize(B value, JsonGenerator gen, SerializerProvider provider)
              throws IOException {
            gen.writeStartObject();
            gen.writeStringField("kind", TypeRegistry.kindOf(value.getClass()));
            gen.writeStringField("name", TypeRegistry.nameOf(value.getClass()));
            gen.writeFieldName("spec");
            gen.writeTree(SPEC.valueToTree(value));
            gen.writeEndObject();
          }
        });
    module.addDeserializer(
        base,
        new JsonDeserializer<B>() {
          @Override
          public B deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
            JsonNode node = parser.readValueAsTree();
            Class<?> type =
                TypeRegistry.resolve(node.get("kind").asText(), node.get("name").asText());
            return base.cast(SPEC.treeToValue(node.get("spec"), type));
          }
        });
  }

  /** Build a stored entity document: {@code {kind, name, meta, spec}}. */
  public static String envelope(Object entity, Map<String, String> meta) {
    ObjectNode node = MAPPER.valueToTree(entity);
    node.set("meta", MAPPER.valueToTree(meta));
    return node.toString();
  }

  public static JsonNode read(String json) {
    try {
      return MAPPER.readTree(json);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read JSON", e);
    }
  }

  /** Deserialize the entity from a document (uses its {@code kind}/{@code name}/{@code spec}). */
  public static <T> T spec(JsonNode document, Class<T> baseType) {
    try {
      return MAPPER.treeToValue(document, baseType);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to deserialize entity", e);
    }
  }

  public static Map<String, String> meta(JsonNode document) {
    JsonNode meta = document.get("meta");
    return meta == null || meta.isNull() ? Map.of() : MAPPER.convertValue(meta, STRING_MAP);
  }

  /**
   * Serialize a value with the codec (e.g. a {@link CommandResponse}; nested events get envelopes).
   */
  public static String toJson(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to serialize " + value, e);
    }
  }

  public static <T> T fromJson(String json, Class<T> type) {
    try {
      return MAPPER.readValue(json, type);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to deserialize " + type.getName(), e);
    }
  }
}
