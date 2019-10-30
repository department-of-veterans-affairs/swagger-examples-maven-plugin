package gov.va.plugin.maven.swagger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import gov.va.api.health.autoconfig.configuration.JacksonConfig;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.configuration.PlexusConfiguration;

/**
 * Maven Mojo that injects examples into a Swagger/OpenAPI artifact. This plugin may be extended in
 * the future to cover additional aspects of Swagger/OpenAPI generation.
 *
 * <p>The plugin will search for example nodes in JSON/YAML defined in configuration. Nodes that
 * match a pattern of <code>${key:package.Class#method}</code> will be replaced using reflection to
 * load resources on the plugin's classpath.
 *
 * <p>Default examples can be overridden by configuration which specifies a key and a source (<code>
 * package.Class#method</code>).
 *
 * <p>Any examples that can not be loaded will be skipped to ensure backwards compatibility with
 * previous <code>SWAGGER_EXAMPLE_*</code> patterns. The only exception to this is an override that
 * does not match the expected pattern, which will fail the build.
 */
@Mojo(
  name = "inject",
  defaultPhase = LifecyclePhase.COMPILE,
  requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME
)
public class SwaggerMojo extends AbstractMojo {

  /* Lazily initialized */
  @Parameter(property = "examples")
  private List<PlexusConfiguration> examples;

  /* Lazily initialized */
  @Parameter(property = "files")
  private List<PlexusConfiguration> files;

  @Parameter(defaultValue = "${project}", required = true, readonly = true)
  private MavenProject project;

  private ExampleInjector exampleInjector;

  /** Entry point for this plugin. */
  public void execute() throws MojoExecutionException, MojoFailureException {
    for (PlexusConfiguration file : files) {
      if (StringUtils.isAnyBlank(file.getAttribute("file"), file.getAttribute("format"))) {
        throw new MojoExecutionException("File and format must not be blank");
      }
      if (Format.lookup(file.getAttribute("format")) == null) {
        throw new MojoExecutionException(
            "File format must be one of " + Arrays.toString(Format.values()));
      }
    }
    for (PlexusConfiguration example : examples) {
      if (StringUtils.isAnyBlank(example.getAttribute("key"), example.getAttribute("source"))) {
        throw new MojoExecutionException("Example key and source must not be blank");
      }
    }
    Map<String, String> overrides =
        examples
            .stream()
            .collect(Collectors.toMap(o -> o.getAttribute("key"), o -> o.getAttribute("source")));
    for (PlexusConfiguration file : files) {
      getExampleInjector(getClasspath(), overrides)
          .injectSwaggerExamples(
              file.getAttribute("file"), Format.lookup(file.getAttribute("format")).getMapper());
    }
  }

  /**
   * Build a custom ClassLoader that includes the target directory of the current project. This
   * allows the plugin to work with sources generated as part of the compile phase (in addition to
   * the project's dependencies declared in the Mojo annotation).
   *
   * @return a custom ClassLoader.
   */
  ClassLoader getClasspath() throws MojoFailureException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    if (project != null) {
      try {
        URL u = new File(project.getBuild().getOutputDirectory()).toURI().toURL();
        return URLClassLoader.newInstance(new URL[] {u}, this.getClass().getClassLoader());
      } catch (MalformedURLException e) {
        throw new MojoFailureException("Unable to build custom ClassLoader", e);
      }
    }
    return classLoader;
  }

  /* Lazy initialization */
  ExampleInjector getExampleInjector(ClassLoader classLoader, Map<String, String> overrides) {
    if (this.exampleInjector == null) {
      this.exampleInjector = new ExampleInjector(classLoader, overrides);
    }
    return exampleInjector;
  }

  void setExampleInjector(ExampleInjector exampleInjector) {
    this.exampleInjector = exampleInjector;
  }

  void setExamples(List<PlexusConfiguration> examples) {
    this.examples = examples;
  }

  void setFiles(List<PlexusConfiguration> files) {
    this.files = files;
  }

  void setProject(MavenProject project) {
    this.project = project;
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
