# swagger-maven-plugin

Maven Plugin to extend functionality of _swagger.core.v3:swagger-maven-plugin_.

The current implementation injects examples into existing `openapi.*` files during the **compile** phase of the build.

It will search the files for the following pattern and then use reflection to dynamically replace the content.

```
"example" : "${exampleKey:package.Class#staticMethod}"
```

It will map the return type to the format specified for each file (`JSON` or `YAML`). It will ignore examples that do not match this pattern to ensure valid examples and other placeholders are not affected. It will skip (and warn) examples that can not be found (e.g. class or method does not exist).

Default examples can be overridden using configuration. See usage below.

The examples are expected to be on the **plugin's** classpath. This allows them to live in the project's _src/main_ or in an external library referenced as a plugin dependency.

## Usage

Import the plugin in your project by adding the following configuration in your `plugins` block.

```
  <plugin>
    <groupId>gov.va.plugin.maven</groupId>
    <artifactId>swagger-maven-plugin</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <!-- Optional external examples lib -->
    <dependencies>
      <dependency>
        <groupId>gov.va.health.apis</groupId>
        <artifactId>r4-examples</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <scope>runtime</scope>
      </dependency>
    </dependencies>
    <executions>
      <execution>
        <id>inject-examples</id>
        <phase>compile</phase>
        <goals>
          <goal>inject</goal>
        </goals>
        <configuration>
          <!-- List of files (and formats) -->
          <files>
            <file file="${project.build.outputDirectory}/openapi.json" format="JSON"/>
            <file file="${project.build.outputDirectory}/openapi.yaml" format="YAML"/>
          </files>
          <!-- Optional list of examples to override -->
          <examples>
            <example key="metadata" source="my.custom.examples.Examples#metadata"/>
            <example key="operationOutcome" source="my.custom.examples.Examples#operationOutcome"/>
          </examples>
        </configuration>
      </execution>
    </executions>
  </plugin>
```

### Configuration for `files.file`
| Attribute  | Description              |
| ---------- | ------------------------ |
| file       | (required) The file path |
| format     | (required) `JSON` or `YAML`  |

### Configuration for `examples.example`
To override a default example, specify one or more `examples` in the configuration.

| Attribute  | Description                   |
| ---------- | ----------------------------- |
| key        | (required) The example key    |
| source     | (required) The example source |

## Future usage
The following topics have been discussed for future plugin enhancements.

* Inject arbitrary examples (without placeholders) using configuration
* Incorporate _maven-antrun-plugin_ regex replacements
* Generate _openapi.*_ files (extend _swagger.core.v3:swagger-maven-plugin_ and invoke `super.execute()`)
