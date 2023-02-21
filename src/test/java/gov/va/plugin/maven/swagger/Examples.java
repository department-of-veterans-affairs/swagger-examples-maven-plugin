package gov.va.plugin.maven.swagger;

import java.util.HashMap;
import java.util.Map;

/** Examples used in unit tests. */
public class Examples {
  /**
   * A null example.
   *
   * @return a null example.
   */
  public static String nullExample() {
    return null;
  }

  /**
   * An example object.
   *
   * @return an example object.
   */
  public static Map<String, String> objectExample() {
    Map<String, String> map = new HashMap<>();
    map.put("object_example_1", "value_1");
    map.put("object_example_2", "value_2");
    return map;
  }

  /**
   * An example string.
   *
   * @return an example string.
   */
  public static String stringExample() {
    return "string_example";
  }
}
