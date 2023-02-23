package gov.va.plugin.maven.swagger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.MojoExecutionException;

/** Utility for injecting examples into Swagger/OpenAPI artifacts. */
@Slf4j
@AllArgsConstructor
public class ExampleInjector {
  /** Pattern for example placeholders (e.g. ${key:package.Class#staticMethod}). */
  private static final Pattern PATTERN = Pattern.compile("\\$\\{(.+):(.+)#(.+)\\}");

  /** JSON/YAML key node for examples. */
  private static final String EXAMPLE_KEY = "example";

  /** Class path to use for loading examples. */
  private ClassLoader classLoader;

  /**
   * Examples to use as overrides.
   *
   * <p>The format of this map is as follows:
   *
   * <pre>Map.of( "key", "package.Class#staticMethod" )</pre>
   */
  private Map<String, String> overrides;

  /**
   * Attempt to determine a mapper from a given format and file.
   *
   * <p>If format is null, attempt to infer from the given file.
   *
   * <p>This implementation uses a simplistic technique to by looking at the file extension.
   *
   * @param format The format (may be null).
   * @param file The file to infer from.
   * @return an optional {@link ObjectMapper}.
   * @throws IOException in the event of a file handling exception.
   */
  static Optional<ObjectMapper> getMapper(Format format, File file) throws IOException {
    if (format != null) {
      return Optional.of(format.getMapper());
    }
    Format inferredFormat = Format.lookup(FilenameUtils.getExtension(file.getCanonicalPath()));
    if (inferredFormat != null) {
      return Optional.of(inferredFormat.getMapper());
    }
    return Optional.empty();
  }

  /**
   * Sort JSON nodes.
   *
   * @param node The parent node.
   */
  private static void sortObjectNode(ObjectNode node) {
    Iterable<Map.Entry<String, JsonNode>> iterable = () -> node.fields();
    List<Map.Entry<String, JsonNode>> elements =
        StreamSupport.stream(iterable.spliterator(), false)
            .sorted((left, right) -> left.getKey().compareToIgnoreCase(right.getKey()))
            .collect(Collectors.toList());
    node.removeAll();
    for (Map.Entry<String, JsonNode> element : elements) {
      node.set(element.getKey(), element.getValue());
    }
  }

  /**
   * Return an example, given a placeholder.
   *
   * <p>Placeholders that don't match the pattern {@link ExampleInjector#PATTERN} will be ignored.
   *
   * <p>Placeholders that match the expected pattern but a) can not be invoked or b) return null
   * will cause a {@link MojoExecutionException} to be thrown.
   *
   * @param placeholder The placeholder.
   * @return an optional example.
   * @throws MojoExecutionException if a failure condition cited above occurs.
   */
  private Optional<Object> example(String placeholder) throws MojoExecutionException {
    Matcher matcher = PATTERN.matcher(placeholder);
    if (matcher.find()) {
      String key = matcher.group(1);
      try {
        Class<?> clazz;
        Method method;
        log.info("Injecting example [{}]", key);
        /* Override the default example if instructed */
        if (overrides != null && overrides.containsKey(key)) {
          String[] classAndMethod = StringUtils.split(overrides.get(key), "#");
          if (classAndMethod.length == 2) {
            clazz = classLoader.loadClass(classAndMethod[0]);
            method = clazz.getMethod(classAndMethod[1]);
          } else {
            throw new MojoExecutionException(
                "Override [" + key + "] does not match pattern of package.Class#staticMethod]");
          }
        } else {
          clazz = classLoader.loadClass(matcher.group(2));
          method = clazz.getMethod(matcher.group(3));
        }
        method.setAccessible(true);
        Object example = method.invoke(null);
        if (example == null) {
          throw new MojoExecutionException("Example must not be null");
        }
        return Optional.of(example);
      } catch (ReflectiveOperationException e) {
        throw new MojoExecutionException("Failed to inject example [" + key + "]", e);
      }
    } else {
      log.warn(
          "Example [{}] does not match pattern of ${key:package.Class#staticMethod}; skipped",
          placeholder);
    }
    return Optional.empty();
  }

  /**
   * Inject an example into a parent using a mapper.
   *
   * @param example The example to inject.
   * @param parent The parent of the example.
   * @param mapper The mapper to use.
   * @throws IOException if a file related exception occurs.
   */
  private void inject(Object example, JsonNode parent, ObjectMapper mapper) throws IOException {
    String exampleString = mapper.writeValueAsString(example);
    JsonNode exampleJsonNode = mapper.readTree(exampleString);
    ((ObjectNode) parent).set(EXAMPLE_KEY, exampleJsonNode);
  }

  /**
   * Inject examples into a file with a given format.
   *
   * <p>If format is null, attempt to infer the file format.
   *
   * @param file The file to work with.
   * @param format The format to use.
   * @throws MojoExecutionException if an execution error occurs.
   */
  public void injectSwaggerExamples(File file, Format format) throws MojoExecutionException {
    try {
      log.info("Processing {}", file.getCanonicalPath());
      ObjectMapper mapper =
          getMapper(format, file).orElseThrow(() -> new MojoExecutionException("Unknown mapper"));
      JsonNode root = mapper.readTree(file);
      List<JsonNode> parents = root.findParents(EXAMPLE_KEY);
      for (final JsonNode parent : parents) {
        if (parent.get(EXAMPLE_KEY).isTextual()) {
          final String placeholder = parent.get(EXAMPLE_KEY).asText();
          Optional<Object> example = example(placeholder);
          if (example.isPresent()) {
            inject(example.get(), parent, mapper);
          }
        }
      }
      sortObjectNode((ObjectNode) root.get("paths"));
      sortObjectNode((ObjectNode) root.get("components").get("schemas"));
      mapper.writerWithDefaultPrettyPrinter().writeValue(file, root);
    } catch (JsonProcessingException e) {
      throw new MojoExecutionException("Error processing JSON", e);
    } catch (IOException e) {
      throw new MojoExecutionException("Error while processing file", e);
    }
  }

  /** Supported file formats and associated mappers. */
  public enum Format {
    JSON {
      @Override
      public ObjectMapper getMapper() {
        return JacksonConfig.createMapper();
      }
    },
    YAML {
      @Override
      public ObjectMapper getMapper() {
        return JacksonConfig.createMapper(new YAMLFactory());
      }
    };

    /**
     * Null-safe case-insensitive lookup.
     *
     * @param name The name to lookup.
     * @return the matching Format or null.
     */
    public static Format lookup(String name) {
      for (Format format : values()) {
        if (StringUtils.equalsIgnoreCase(format.name(), name)) {
          return format;
        }
      }
      return null;
    }

    /** Mapper that supports this file type. */
    public abstract ObjectMapper getMapper();
  }
}
