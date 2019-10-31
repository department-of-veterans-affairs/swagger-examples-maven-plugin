package gov.va.plugin.maven.swagger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.va.plugin.maven.swagger.ExampleInjector.Format;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for ExampleInjector. */
public class ExampleInjectorTest {

  private static final Path TEST_RESOURCES = Paths.get("src", "test", "resources");

  @Rule public TemporaryFolder workingDirectory = new TemporaryFolder();

  private File jsonFile;

  private File yamlFile;

  private ExampleInjector getExampleInjector() {
    return getExampleInjector(null);
  }

  private ExampleInjector getExampleInjector(Map<String, String> overrides) {
    return new ExampleInjector(ExampleInjector.class.getClassLoader(), overrides);
  }

  /** Before each test, copy the input files to a temporary location for processing. */
  @Before
  public void setupFiles() throws IOException {
    jsonFile = workingDirectory.newFile("openapi.json");
    FileUtils.copyFile(TEST_RESOURCES.resolve("openapi.json").toFile(), jsonFile);
    yamlFile = workingDirectory.newFile("openapi.yaml");
    FileUtils.copyFile(TEST_RESOURCES.resolve("openapi.yaml").toFile(), yamlFile);
  }

  /**
   * Test handling of a ClassNotFoundException by overriding a default example with a missing class.
   *
   * <p>Assert that a MojoExecutionException is thrown.
   */
  @Test(expected = MojoExecutionException.class)
  public void testClassNotFound() throws Exception {
    ExampleInjector exampleInjector =
        getExampleInjector(Map.of("period", "gov.va.plugin.maven.swagger.Missing#stringExample"));
    exampleInjector.injectSwaggerExamples(jsonFile, Format.JSON);
  }

  /**
   * Test the use of a format/mapper that does not match the input file.
   *
   * <p>Assert that a MojoExecutionException is thrown.
   */
  @Test(expected = MojoExecutionException.class)
  public void testIncorrectFormat() throws Exception {
    ExampleInjector injector = getExampleInjector();
    injector.injectSwaggerExamples(yamlFile, Format.JSON);
  }

  /**
   * Test handling of a MethodNotFoundException by overriding a default example with a valid class
   * but missing method.
   *
   * <p>Assert that a MojoExecutionException is thrown.
   */
  @Test(expected = MojoExecutionException.class)
  public void testMethodNotFound() throws Exception {
    ExampleInjector exampleInjector =
        getExampleInjector(Map.of("period", "gov.va.plugin.maven.swagger.Examples#missing"));
    exampleInjector.injectSwaggerExamples(jsonFile, Format.JSON);
  }

  /**
   * Test a missing input file.
   *
   * <p>Assert that a MojoExecutionException is thrown.
   */
  @Test(expected = MojoExecutionException.class)
  public void testMissingFile() throws Exception {
    ExampleInjector injector = getExampleInjector();
    injector.injectSwaggerExamples(new File("missing.json"), Format.JSON);
  }

  /** Test the normal JSON flow. */
  @Test
  public void testNormalJson() throws Exception {
    Format format = Format.JSON;
    ExampleInjector exampleInjector = getExampleInjector();
    exampleInjector.injectSwaggerExamples(jsonFile, format);
    JsonNode root = format.getMapper().readTree(jsonFile);
    normalAssertions(root, format.getMapper());
  }

  /** Test the normal inferred JSON flow. */
  @Test
  public void testNormalJsonInferred() throws Exception {
    ObjectMapper mapper = Format.JSON.getMapper();
    ExampleInjector exampleInjector = getExampleInjector();
    exampleInjector.injectSwaggerExamples(jsonFile, null);
    JsonNode root = mapper.readTree(jsonFile);
    normalAssertions(root, mapper);
  }

  /** Test the normal YAML flow. */
  @Test
  public void testNormalYaml() throws Exception {
    Format format = Format.YAML;
    ExampleInjector exampleInjector = getExampleInjector();
    exampleInjector.injectSwaggerExamples(yamlFile, format);
    JsonNode root = format.getMapper().readTree(yamlFile);
    normalAssertions(root, format.getMapper());
  }

  /** Test the normal inferred YAML flow. */
  @Test
  public void testNormalYamlInferred() throws Exception {
    ObjectMapper mapper = Format.YAML.getMapper();
    ExampleInjector exampleInjector = getExampleInjector();
    exampleInjector.injectSwaggerExamples(yamlFile, null);
    JsonNode root = mapper.readTree(yamlFile);
    normalAssertions(root, mapper);
  }

  /**
   * Common assertions for the normal execution flow.
   *
   * <p>Assert that the examples in the output match the expected default values.
   *
   * <p>Assert that the sorting is applied correctly.
   *
   * @param root The root node.
   * @param mapper The mapper to use.
   */
  private void normalAssertions(JsonNode root, ObjectMapper mapper) throws IOException {
    Assert.assertEquals(
        "SWAGGER_EXAMPLE_METADATA",
        root.get("paths")
            .get("/metadata")
            .get("get")
            .get("responses")
            .get("200")
            .get("content")
            .get("application/json+fhir")
            .get("example")
            .asText());
    Assert.assertEquals(
        Examples.stringExample(),
        root.get("components").get("schemas").get("Period").get("example").asText());
    Assert.assertEquals(
        mapper.readTree(mapper.writeValueAsString(Examples.objectExample())),
        root.get("components").get("schemas").get("Quantity").get("example"));
    Iterator<String> pathIterator = root.get("paths").fieldNames();
    Assert.assertEquals("/metadata", pathIterator.next());
    Assert.assertEquals("/zzz", pathIterator.next());
    Iterator<String> schemaIterator = root.get("components").get("schemas").fieldNames();
    Assert.assertEquals("Period", schemaIterator.next());
    Assert.assertEquals("Quantity", schemaIterator.next());
  }

  /**
   * Test normal override flow.
   *
   * <p>Assert that the examples in the output match the overrides.
   */
  @Test
  public void testOverride() throws Exception {
    Format format = Format.JSON;
    ExampleInjector exampleInjector =
        getExampleInjector(
            Map.of(
                "period",
                "gov.va.plugin.maven.swagger.Examples#objectExample",
                "quantity",
                "gov.va.plugin.maven.swagger.Examples#stringExample"));
    exampleInjector.injectSwaggerExamples(jsonFile, format);
    JsonNode root = format.getMapper().readTree(jsonFile);
    Assert.assertEquals(
        format
            .getMapper()
            .readTree(format.getMapper().writeValueAsString(Examples.objectExample())),
        root.get("components").get("schemas").get("Period").get("example"));
    Assert.assertEquals(
        Examples.stringExample(),
        root.get("components").get("schemas").get("Quantity").get("example").asText());
  }

  /**
   * Test handling of an incorrectly formatted override example.
   *
   * <p>Assert that the plugin throws a MojoExecutionException.
   */
  @Test(expected = MojoExecutionException.class)
  public void testOverrideInvalid() throws Exception {
    ExampleInjector exampleInjector = getExampleInjector(Map.of("period", "package::method"));
    exampleInjector.injectSwaggerExamples(jsonFile, Format.JSON);
  }

  /**
   * Test handling of an unknown format/mapper.
   *
   * <p>Assert that the plugin throws a MojoExecutionException.
   */
  @Test(expected = MojoExecutionException.class)
  public void testUnknownMapper() throws Exception {
    ExampleInjector exampleInjector = getExampleInjector(Map.of("period", "package::method"));
    exampleInjector.injectSwaggerExamples(workingDirectory.newFile("openapi.txt"), null);
  }
}
