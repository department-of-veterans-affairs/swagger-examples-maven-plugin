package gov.va.plugin.maven.swagger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.Value;
import org.junit.jupiter.api.Test;

public class JacksonConfigTest {

  @Test
  @SneakyThrows
  public void canCreateYamlMapper() {
    ObjectMapper mapper = JacksonConfig.createMapper(new YAMLFactory());
    CandyYaml actual = mapper.readValue("ya: neat\nml: 1", CandyYaml.class);
    assertEquals(actual, CandyYaml.builder().ya("neat").ml(1).build());
  }

  @Test
  @SneakyThrows
  public void defaultConstructorIsUsedWhenAvailable() {
    ObjectMapper mapper = JacksonConfig.createMapper();
    HasPrivateDefaultConstructor actual =
        mapper.readValue("{\"ok\":\"hey yah\"}", HasPrivateDefaultConstructor.class);
    HasPrivateDefaultConstructor expected =
        HasPrivateDefaultConstructor.unconventional().ok("hey yah").build();
    assertEquals(actual, expected);
  }

  @Test
  public void hasEasyToUseMapperSupplier() {
    Supplier<ObjectMapper> supplier = JacksonConfig::createMapper;
    assertNotNull(supplier.get());
  }

  @Test
  @SneakyThrows
  public void trimsWhiteSpace() {
    CandyYaml in = CandyYaml.builder().ya("   spaces    ").ml(1).build();
    ObjectMapper mapper = JacksonConfig.createMapper();
    assertEquals("{\"ya\":\"spaces\",\"ml\":1}", mapper.writeValueAsString(in));
  }

  @Value
  @Builder
  public static class CandyYaml {
    String ya;
    int ml;
  }

  @Data
  @NoArgsConstructor(access = AccessLevel.PRIVATE)
  @AllArgsConstructor
  @Builder(builderClassName = "CannotAutoDetactMe", builderMethodName = "unconventional")
  public static class HasPrivateDefaultConstructor {
    String ok;
  }
}
