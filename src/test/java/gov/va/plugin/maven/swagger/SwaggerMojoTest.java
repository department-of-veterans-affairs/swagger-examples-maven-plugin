package gov.va.plugin.maven.swagger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import gov.va.plugin.maven.swagger.ExampleInjector.Format;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.maven.model.Build;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.configuration.DefaultPlexusConfiguration;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Tests for SwaggerMojo. */
public class SwaggerMojoTest {
  private static final String OUTPUT_DIRECTORY = "/path/to/output";

  private SwaggerMojo getSwaggerMojo() {
    Build build = new Build();
    build.setOutputDirectory(OUTPUT_DIRECTORY);
    MavenProject project = new MavenProject();
    project.setBuild(build);
    SwaggerMojo mojo = new SwaggerMojo();
    mojo.setProject(project);
    return mojo;
  }

  /**
   * Test with a blank example key.
   *
   * <p>Assert that a MojoExecutionException is thrown.
   */
  @Test
  public void testBlankExampleKey() {
    PlexusConfiguration file = new DefaultPlexusConfiguration("file");
    file.setAttribute("file", "/path/to/file.json");
    file.setAttribute("format", "JSON");
    PlexusConfiguration example = new DefaultPlexusConfiguration("example");
    example.setAttribute("source", "package.Class#method");
    SwaggerMojo mojo = getSwaggerMojo();
    mojo.setFiles(List.of(file));
    mojo.setExamples(List.of(example));
    assertThrows(MojoExecutionException.class, mojo::execute);
  }

  /**
   * Test with a blank example source.
   *
   * <p>Assert that a MojoExecutionException is thrown.
   */
  @Test
  public void testBlankExampleSource() {
    PlexusConfiguration file = new DefaultPlexusConfiguration("file");
    file.setAttribute("file", "/path/to/file.json");
    file.setAttribute("format", "JSON");
    PlexusConfiguration example = new DefaultPlexusConfiguration("example");
    example.setAttribute("key", "key");
    SwaggerMojo mojo = getSwaggerMojo();
    mojo.setFiles(List.of(file));
    mojo.setExamples(List.of(example));
    assertThrows(MojoExecutionException.class, mojo::execute);
  }

  /**
   * Test with a blank file.
   *
   * <p>Assert that a MojoExecutionException is thrown.
   */
  @Test
  public void testBlankFile() {
    PlexusConfiguration file = new DefaultPlexusConfiguration("file");
    file.setAttribute("format", "JSON");
    SwaggerMojo mojo = getSwaggerMojo();
    mojo.setFiles(List.of(file));
    assertThrows(MojoExecutionException.class, mojo::execute);
  }

  /**
   * Test returning a custom list of files.
   *
   * <p>Assert that each of the expected custom values are present.
   *
   * <p>Assert that there are no additional entries.
   */
  @Test
  public void testFilesCustom() {
    PlexusConfiguration file1 = new DefaultPlexusConfiguration("file");
    file1.setAttribute("file", "/path/to/file1.json");
    file1.setAttribute("format", "JSON");
    PlexusConfiguration file2 = new DefaultPlexusConfiguration("file");
    file2.setAttribute("file", "/path/to/file2.yaml");
    file2.setAttribute("format", "YAML");
    PlexusConfiguration file3 = new DefaultPlexusConfiguration("file");
    file3.setAttribute("file", "/path/to/file3.json");
    file3.setAttribute("format", "JSON");
    List<PlexusConfiguration> configFiles = List.of(file1, file2, file3);
    SwaggerMojo mojo = getSwaggerMojo();
    mojo.setFiles(configFiles);
    Map<File, Format> files = mojo.files();
    for (PlexusConfiguration configFile : configFiles) {
      assertEquals(
          configFile.getAttribute("format"),
          files.get(new File(configFile.getAttribute("file"))).name());
    }
    assertEquals(configFiles.size(), files.size());
  }

  /**
   * Test returning the default list of files.
   *
   * <p>Assert that each of the expected default values are present.
   *
   * <p>Assert that there are no additional entries.
   */
  @Test
  public void testFilesDefault() {
    SwaggerMojo mojo = getSwaggerMojo();
    mojo.setFiles(Collections.emptyList());
    Map<File, Format> files = mojo.files();
    for (Map.Entry<String, Format> expectedEntry : SwaggerMojo.DEFAULT_FILES.entrySet()) {
      File expectedFile = new File(OUTPUT_DIRECTORY + "/" + expectedEntry.getKey());
      assertEquals(expectedEntry.getValue(), files.get(expectedFile));
    }
    assertEquals(SwaggerMojo.DEFAULT_FILES.size(), files.size());
  }

  /**
   * Test the custom class loader.
   *
   * <p>Assert that the returned ClassLoader contains the custom entry.
   */
  @Test
  public void testGetClassLoaderWithOutputPath() throws Exception {
    SwaggerMojo mojo = getSwaggerMojo();
    ClassLoader classLoader = mojo.getClasspath();
    URL[] urls = ((URLClassLoader) classLoader).getURLs();
    assertEquals(new File(OUTPUT_DIRECTORY).toURI().toURL(), urls[urls.length - 1]);
  }

  /**
   * Test with an invalid file format.
   *
   * <p>Assert that a MojoExecutionException is thrown.
   */
  @Test
  public void testInvalidFileFormat() {
    PlexusConfiguration file = new DefaultPlexusConfiguration("file");
    file.setAttribute("file", "/path/to/file.invalid");
    file.setAttribute("format", "invalid");
    SwaggerMojo mojo = getSwaggerMojo();
    mojo.setFiles(List.of(file));
    assertThrows(MojoExecutionException.class, mojo::execute);
  }

  /**
   * Test with an missing file format.
   *
   * <p>Assert that a MojoExecutionException is thrown.
   */
  @Test
  public void testMissingFormat() throws Exception {
    ExampleInjector exampleInjector = Mockito.mock(ExampleInjector.class);
    PlexusConfiguration file = new DefaultPlexusConfiguration("file");
    file.setAttribute("file", "/path/to/file.unknown");
    SwaggerMojo mojo = getSwaggerMojo();
    mojo.setFiles(List.of(file));
    mojo.setExamples(Collections.emptyList());
    mojo.setExampleInjector(exampleInjector);
    mojo.execute();
    Mockito.verify(exampleInjector)
        .injectSwaggerExamples(
            Mockito.argThat(f -> f.equals(new File(file.getAttribute("file")))),
            Mockito.argThat(Objects::isNull));
  }

  /**
   * Test a normal JSON flow.
   *
   * <p>Assert that the mock object is called with the expected parameters.
   */
  @Test
  public void testNormalFlowJSON() throws Exception {
    ExampleInjector exampleInjector = Mockito.mock(ExampleInjector.class);
    PlexusConfiguration file = new DefaultPlexusConfiguration("file");
    file.setAttribute("file", "/path/to/file.json");
    file.setAttribute("format", "JSON");
    PlexusConfiguration example = new DefaultPlexusConfiguration("example");
    example.setAttribute("key", "key");
    example.setAttribute("source", "package.Class#method");
    SwaggerMojo mojo = getSwaggerMojo();
    mojo.setFiles(List.of(file));
    mojo.setExamples(List.of(example));
    mojo.setExampleInjector(exampleInjector);
    mojo.execute();
    Mockito.verify(exampleInjector)
        .injectSwaggerExamples(
            Mockito.argThat(fileArg -> fileArg.equals(new File(file.getAttribute("file")))),
            Mockito.argThat(formatArg -> formatArg == Format.JSON));
  }

  /**
   * Test a normal YAML flow.
   *
   * <p>Assert that the mock object is called with the expected parameters.
   */
  @Test
  public void testNormalFlowYAML() throws Exception {
    ExampleInjector exampleInjector = Mockito.mock(ExampleInjector.class);
    PlexusConfiguration file = new DefaultPlexusConfiguration("file");
    file.setAttribute("file", "/path/to/file.json");
    file.setAttribute("format", "YAML");
    PlexusConfiguration example = new DefaultPlexusConfiguration("example");
    example.setAttribute("key", "key");
    example.setAttribute("source", "package.Class#method");
    SwaggerMojo mojo = getSwaggerMojo();
    mojo.setFiles(List.of(file));
    mojo.setExamples(List.of(example));
    mojo.setExampleInjector(exampleInjector);
    mojo.execute();
    Mockito.verify(exampleInjector)
        .injectSwaggerExamples(
            Mockito.argThat(fileArg -> fileArg.equals(new File(file.getAttribute("file")))),
            Mockito.argThat(formatArg -> formatArg == Format.YAML));
  }
}
