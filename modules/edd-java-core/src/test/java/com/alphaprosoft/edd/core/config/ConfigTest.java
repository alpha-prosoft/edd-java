package com.alphaprosoft.edd.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class ConfigTest {

  @Test
  void precedenceDefaultsThenFileThenPropsThenEnv() {
    Properties file = new Properties();
    file.setProperty("token.iss", "from-file");
    file.setProperty("token.aud", "from-file");
    Properties sysProps = new Properties();
    sysProps.setProperty("edd.token.aud", "from-props");

    Config config =
        Config.builder()
            .defaults(Map.of("token.iss", "from-default", "token.realm-claim", "realm"))
            .fromProperties(file)
            .fromProperties(sysProps)
            .fromEnvironment(Map.of("EDD_TOKEN_ISS", "from-env"))
            .build();

    assertEquals("from-env", config.get("token.iss", "?"), "env wins");
    assertEquals("from-props", config.get("token.aud", "?"), "props beat file");
    assertEquals(
        "realm", config.get("token.realm-claim", "?"), "default survives when unset elsewhere");
  }

  @Test
  void relaxedBindingIgnoresCaseAndDashes() {
    Config config =
        Config.builder().fromEnvironment(Map.of("EDD_TOKEN_ROLESCLAIMS", "groups,roles")).build();
    // requested with kebab-case + dots; env used run-together caps — they bind to the same key
    assertEquals(List.of("groups", "roles"), config.getList("token.roles-claims"));
    assertEquals(List.of("groups", "roles"), config.getList("TOKEN.RolesClaims"));
  }

  @Test
  void subView() {
    Config config =
        Config.builder()
            .fromProperties(props("token.iss", "i", "token.aud", "a", "store.prefix", "p"))
            .build();
    Config token = config.sub("token");
    assertEquals("i", token.get("iss", "?"));
    assertEquals("a", token.get("aud", "?"));
    assertFalse(token.has("prefix"));
  }

  @Test
  void typedGetters() {
    Config config =
        Config.builder()
            .fromProperties(props("a.num", "42", "a.flag", "true", "a.list", "x, y ,z"))
            .build();
    assertEquals(42, config.getInt("a.num", 0));
    assertTrue(config.getBoolean("a.flag", false));
    assertEquals(List.of("x", "y", "z"), config.getList("a.list"));
    assertEquals(7, config.getInt("a.missing", 7), "fallback when absent");
  }

  @Test
  void nestedSourceFlattens() {
    Config config =
        Config.builder()
            .source(Map.of("token", Map.of("iss", "x", "rolesClaims", "groups")), "")
            .build();
    assertEquals("x", config.get("token.iss", "?"));
    assertEquals(List.of("groups"), config.getList("token.roles-claims"));
  }

  private static Properties props(String... kv) {
    Properties p = new Properties();
    for (int i = 0; i < kv.length; i += 2) {
      p.setProperty(kv[i], kv[i + 1]);
    }
    return p;
  }
}
