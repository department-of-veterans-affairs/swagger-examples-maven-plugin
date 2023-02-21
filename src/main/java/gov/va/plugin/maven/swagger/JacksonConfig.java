package gov.va.plugin.maven.swagger;

import static org.apache.commons.lang3.StringUtils.trimToNull;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.SneakyThrows;

/**
 * Look for a deserialize annotation using the builder for immutable data types. This configuration
 * supports
 *
 * <ul>
 *   <li>Property level access
 *   <li>JDK 8 data type support, e.g. Optional
 *   <li>Java time support, e.g. Instant
 *   <li>Fails on unknown properties
 *   <li>Lombok &#64;Value &#64;Builder with out needing to specify Jackson annotations
 * </ul>
 *
 * Note: The builder will only be used of your class does not have a default constructor.
 *
 * <p>Your classes should look like one of these:
 *
 * <p>No additional Jackson annotations needed!
 *
 * <pre>
 * &#64;Value
 * &#64;Builder
 * &#64;JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
 * public class Easy {
 *    String ack;
 *    String bar;
 * }
 * </pre>
 *
 * Alternatively, you can specify the deserializer to be the builder.
 *
 * <pre>
 * &#64;JsonDeserialize(builder = Foo.FooBuilder.class)
 * &#64;Value
 * &#64;Builder
 * &#64;JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
 * public class Foo {
 *    String ack;
 *    String bar;
 * }
 * </pre>
 */
public class JacksonConfig {
  /** Return a configured Jackson ObjectMapper. This method is useful as a supplier function. */
  public static ObjectMapper createMapper() {
    return new JacksonConfig().objectMapper();
  }

  /**
   * Return a configured Jackson ObjectMapper that uses the given factory. Use this method to create
   * a Yaml mapper. This method is useful as a supplier function.
   */
  public static ObjectMapper createMapper(JsonFactory jsonFactory) {
    return new JacksonConfig().configureMapper(JsonMapper.builder(jsonFactory));
  }

  /** Configure the given mapper as described in the class-level documentation. */
  private ObjectMapper configureMapper(JsonMapper.Builder builder) {
    JsonMapper mapper =
        builder
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .enable(MapperFeature.AUTO_DETECT_FIELDS)
            .build();
    return mapper
        .registerModule(new Jdk8Module())
        .registerModule(new JavaTimeModule())
        .registerModule(new StringTrimModule())
        .setAnnotationIntrospector(new LombokAnnotationIntrospector())
        .setSerializationInclusion(Include.NON_NULL)
        .setVisibility(PropertyAccessor.ALL, Visibility.ANY);
  }

  /**
   * Return a ready to use mapper that will work with classes adhering to the conventions described
   * in the class-level documentation.
   */
  public ObjectMapper objectMapper() {
    return configureMapper(JsonMapper.builder());
  }

  /**
   * The lombok class annotation inspector provides support for this project's style of builders.
   * This allows @Value classes with @Builders to be automatically supported for deserialization. It
   * will look for a builder class using the standard Lombok naming conventions and assume builder
   * methods do not have a prefix, e.g. "property" instead of "setProperty" or "withProperty".
   * However, you can still use @JsonPOJOBuilder if you need to override this inspectors default
   * behavior.
   */
  private static class LombokAnnotationIntrospector extends JacksonAnnotationIntrospector {

    private static final long serialVersionUID = 1577708838997942118L;

    @Override
    public Class<?> findPOJOBuilder(AnnotatedClass ac) {
      /*
       * Attempt to allow the default mechanism work. However, if no builder is found using Jackson
       * annotations, try to find a lombok style builder.
       */
      Class<?> pojoBuilder = super.findPOJOBuilder(ac);
      if (pojoBuilder != null) {
        return pojoBuilder;
      }
      if (hasDefaultConstructor(ac.getAnnotated())) {
        return null;
      }
      String className = ac.getAnnotated().getSimpleName();
      String lombokBuilder = ac.getAnnotated().getName() + "$" + className + "Builder";
      try {
        return Class.forName(lombokBuilder);
      } catch (ClassNotFoundException e) {
        /* Default lombok builder does not exist. */
      }
      return null;
    }

    @Override
    public JsonPOJOBuilder.Value findPOJOBuilderConfig(AnnotatedClass ac) {
      if (ac.hasAnnotation(JsonPOJOBuilder.class)) {
        return super.findPOJOBuilderConfig(ac);
      }
      return new JsonPOJOBuilder.Value("build", "");
    }

    private boolean hasDefaultConstructor(Class<?> ac) {
      try {
        return ac.getDeclaredConstructor() != null;
      } catch (NoSuchMethodException e) {
        return false;
      }
    }
  }

  /** A module that adds a whitespace trimming String serializer. */
  private static class StringTrimModule extends SimpleModule {

    private static final long serialVersionUID = 291123238941070402L;

    StringTrimModule() {
      addSerializer(
          String.class,
          new StdScalarSerializer<String>(String.class, false) {

            private static final long serialVersionUID = -8540220138160469422L;

            @Override
            @SneakyThrows
            public void serialize(String value, JsonGenerator gen, SerializerProvider provider) {
              gen.writeString(trimToNull(value));
            }
          });
      addDeserializer(
          String.class,
          new StdScalarDeserializer<String>(String.class) {

            private static final long serialVersionUID = 3653344745316234311L;

            @Override
            @SneakyThrows
            public String deserialize(JsonParser p, DeserializationContext ctxt) {
              return trimToNull(p.getValueAsString());
            }
          });
    }
  }
}
