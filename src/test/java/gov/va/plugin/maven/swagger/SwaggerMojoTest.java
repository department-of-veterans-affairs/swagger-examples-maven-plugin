package gov.va.plugin.maven.swagger;

import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import gov.va.plugin.maven.swagger.SwaggerMojo.Format;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.maven.model.Build;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.configuration.DefaultPlexusConfiguration;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.junit.Assert;
import org.junit.Test;
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
  @Test(expected = MojoExecutionException.class)
  public void testBlankExampleKey() throws Exception {
    PlexusConfiguration file = new DefaultPlexusConfiguration("file");
    file.setAttribute("file", "/path/to/file.json");
    file.setAttribute("format", "JSON");
    PlexusConfiguration example = new DefaultPlexusConfiguration("example");
    example.setAttribute("source", "package.Class#method");
    SwaggerMojo mojo = getSwaggerMojo();
    mojo.setFiles(Arrays.asList(file));
    mojo.setExamples(Arrays.asList(example));
    mojo.execute();
  }

  /**
   * Test with a blank example source.
   *
   * <p>Assert that a MojoExecutionException is thrown.
   */
  @Test(expected = MojoExecutionException.class)
  public void testBlankExampleSource() throws Exception {
    PlexusConfiguration file = new DefaultPlexusConfiguration("file");
    file.setAttribute("file", "/path/to/file.json");
    file.setAttribute("format", "JSON");
    PlexusConfiguration example = new DefaultPlexusConfiguration("example");
    example.setAttribute("key", "key");
    SwaggerMojo mojo = getSwaggerMojo();
    mojo.setFiles(Arrays.asList(file));
    mojo.setExamples(Arrays.asList(example));
    mojo.execute();
  }

  /**
   * Test with a blank file.
   *
   * <p>Assert that a MojoExecutionException is thrown.
   */
  @Test(expected = MojoExecutionException.class)
  public void testBlankFile() throws Exception {
    PlexusConfiguration file = new DefaultPlexusConfiguration("file");
    file.setAttribute("format", "JSON");
    SwaggerMojo mojo = getSwaggerMojo();
    mojo.setFiles(Arrays.asList(file));
    mojo.execute();
  }

  /**
   * Test with a blank file format.
   *
   * <p>Assert that a MojoExecutionException is thrown.
   */
  @Test(expected = MojoExecutionException.class)
  public void testBlankFormat() throws Exception {
    PlexusConfiguration file = new DefaultPlexusConfiguration("file");
    file.setAttribute("file", "/path/to/file.unknown");
    SwaggerMojo mojo = getSwaggerMojo();
    mojo.setFiles(Arrays.asList(file));
    mojo.execute();
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
    Assert.assertEquals(new File(OUTPUT_DIRECTORY).toURI().toURL(), urls[urls.length - 1]);
  }

  /**
   * Test with an invalid file format.
   *
   * <p>Assert that a MojoExecutionException is thrown.
   */
  @Test(expected = MojoExecutionException.class)
  public void testInvalidFileFormat() throws Exception {
    PlexusConfiguration file = new DefaultPlexusConfiguration("file");
    file.setAttribute("file", "/path/to/file.invalid");
    file.setAttribute("format", "invalid");
    SwaggerMojo mojo = getSwaggerMojo();
    mojo.setFiles(Arrays.asList(file));
    mojo.execute();
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
    mojo.setFiles(Arrays.asList(file));
    mojo.setExamples(Arrays.asList(example));
    mojo.setExampleInjector(exampleInjector);
    mojo.execute();
    Mockito.verify(exampleInjector)
        .injectSwaggerExamples(
            Mockito.argThat(f -> f.equals(new File(file.getAttribute("file")))),
            Mockito.argThat(mapper -> mapper.getFactory().getClass().equals(YAMLFactory.class)));
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
    mojo.setFiles(Arrays.asList(file));
    mojo.setExamples(Arrays.asList(example));
    mojo.setExampleInjector(exampleInjector);
    mojo.execute();
    Mockito.verify(exampleInjector)
        .injectSwaggerExamples(
            Mockito.argThat(f -> f.equals(new File(file.getAttribute("file")))),
            Mockito.argThat(mapper -> !mapper.getFactory().getClass().equals(YAMLFactory.class)));
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
      Assert.assertTrue(expectedEntry.getValue().equals(files.get(expectedFile)));
    }
    Assert.assertEquals(SwaggerMojo.DEFAULT_FILES.size(), files.size());
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
    List<PlexusConfiguration> configFiles = Arrays.asList(file1, file2, file3);
    SwaggerMojo mojo = getSwaggerMojo();
    mojo.setFiles(configFiles);
    Map<File, Format> files = mojo.files();
    for (PlexusConfiguration configFile : configFiles) {
      Assert.assertTrue(
          configFile
              .getAttribute("format")
              .equals(files.get(new File(configFile.getAttribute("file"))).name()));
    }
    Assert.assertEquals(configFiles.size(), files.size());
  }
}
