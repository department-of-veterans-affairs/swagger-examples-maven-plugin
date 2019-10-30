package gov.va.plugin.maven.swagger;

import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
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

  private static final String ARBITRARY_FOLDER = "/path/to/output";

  private SwaggerMojo getSwaggerMojo() {
    Build build = new Build();
    build.setOutputDirectory(ARBITRARY_FOLDER);
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
    Assert.assertEquals(new File(ARBITRARY_FOLDER).toURI().toURL(), urls[urls.length - 1]);
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
   * Test a normal JSON flow.
   *
   * <p>Assert that the mock object is called with the expected parameters.
   */
  @Test
  public void testNormalFlowJSON() throws Exception {
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
            Mockito.eq(file.getAttribute("file")),
            Mockito.argThat(mapper -> mapper.getFactory().getClass().equals(YAMLFactory.class)));
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
            Mockito.eq(file.getAttribute("file")),
            Mockito.argThat(mapper -> !mapper.getFactory().getClass().equals(YAMLFactory.class)));
  }
}
