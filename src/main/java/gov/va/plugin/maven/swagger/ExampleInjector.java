package gov.va.plugin.maven.swagger;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.MojoExecutionException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

/** Utility for injecting examples into Swagger/OpenAPI artifacts. */
@Slf4j
public class ExampleInjector {
  /** Pattern for example placeholders (e.g. ${key:package.Class#staticMethod}) */
  private static final Pattern PATTERN = Pattern.compile("\\$\\{(.+):(.+)#(.+)\\}");

  /** JSON/YAML key node for examples */
  private static final String EXAMPLE_KEY = "example";

  /** Class path to use for loading examples */
  private ClassLoader classLoader;

  /** Examples to use as overrides */
  private Map<String, String> overrides;

  /** Construct a new ExampleInjector. */
  public ExampleInjector() {
    this.classLoader = this.getClass().getClassLoader();
  }

  /**
   * Construct a new ExampleInjector.
   *
   * <p>Overrides should be of the format:
   *
   * <pre>Map.of( "key", "package.Class#method" )</pre>
   *
   * @param classLoader The class loader to use for example reflection.
   * @param overrides A map of example overrides.
   */
  public ExampleInjector(ClassLoader classLoader, Map<String, String> overrides) {
    this.classLoader = classLoader;
    this.overrides = overrides;
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
   * @param placeholder The placeholder matching pattern {@link ExampleInjector#PATTERN}
   * @return an optional example.
   * @throws MojoExecutionException if a reflective type exception occurs.
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
        return Optional.of(method.invoke(null));
      } catch (ClassNotFoundException | NoSuchMethodException e) {
        log.warn("Example [{}] not found; skipping", key);
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
    JsonNode exampleJsonNode = mapper.readTree(mapper.writeValueAsString(example));
    ((ObjectNode) parent).set(EXAMPLE_KEY, exampleJsonNode);
  }

  /**
   * Inject examples into a file, using a given mapper.
   *
   * @param filename The full file path to work with.
   * @param mapper The mapper to use.
   * @throws MojoExecutionException if an execution error occurs.
   */
  public void injectSwaggerExamples(String filename, ObjectMapper mapper)
      throws MojoExecutionException {
    Path path = Paths.get(filename);
    File swaggerFile = path.toFile();

    try {
      JsonNode root = mapper.readTree(swaggerFile);
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
      mapper.writerWithDefaultPrettyPrinter().writeValue(swaggerFile, root);
    } catch (JsonProcessingException e) {
      throw new MojoExecutionException("Error processing JSON", e);
    } catch (IOException e) {
      throw new MojoExecutionException("Error while processing file", e);
    }
  }

  void setOverrides(Map<String, String> overrides) {
    this.overrides = overrides;
  }
}
