package com.alphaprosoft.edd.core.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * One configuration view for the whole framework. Every decision a module/filter would otherwise
 * hard-code (JWT issuer, the claim that holds roles, a table prefix, …) is read from here, so it
 * can be set without code changes.
 *
 * <p><b>Sources, lowest to highest precedence:</b> programmatic defaults → a config file ({@code
 * .properties}, path overridable) → Java system properties ({@code -Dedd.*}) → environment
 * variables ({@code EDD_*}). A later source overrides an earlier one key-by-key.
 *
 * <p><b>Relaxed binding (Spring-style).</b> Keys are case-insensitive and dashes are ignored; an
 * env var's {@code _} is a path separator and the {@code EDD_}/{@code edd.} prefix is stripped. So
 * {@code EDD_TOKEN_ISS}, {@code -Dedd.token.iss}, and a file line {@code token.iss=…} all set the
 * same key, readable as {@code get("token.iss")}; {@code EDD_TOKEN_ROLESCLAIMS} ⇔ {@code
 * token.roles-claims}.
 *
 * <p>Each module reads its own namespace via {@link #sub(String)} (e.g. {@code
 * config.sub("token")}), so modules cannot collide and all bind the same way. Dependency-free by
 * design (lives in core).
 */
public final class Config {

  /** Env var / property prefix that marks a key as edd configuration. */
  public static final String ENV_PREFIX = "EDD_";

  public static final String PROP_PREFIX = "edd.";

  /** Where to find the config file; overridable via this key (env {@code EDD_CONFIG_FILE}). */
  public static final String CONFIG_FILE_KEY = "config.file";

  public static final String DEFAULT_CONFIG_FILE = "edd.properties";

  private final Map<String, String> values;

  private Config(Map<String, String> values) {
    this.values = Map.copyOf(values);
  }

  /**
   * Standard chain: config file (path from {@code EDD_CONFIG_FILE}/{@code edd.config.file}) → props
   * → env.
   */
  public static Config load() {
    return builder().fromFile().fromSystemProperties().fromEnvironment().build();
  }

  public static Builder builder() {
    return new Builder();
  }

  static String canon(String key) {
    return key.toLowerCase(Locale.ROOT).replace("-", "");
  }

  public boolean has(String key) {
    return values.containsKey(canon(key));
  }

  public Optional<String> get(String key) {
    return Optional.ofNullable(values.get(canon(key)));
  }

  public String get(String key, String fallback) {
    return values.getOrDefault(canon(key), fallback);
  }

  public int getInt(String key, int fallback) {
    String v = values.get(canon(key));
    return v == null ? fallback : Integer.parseInt(v.trim());
  }

  public long getLong(String key, long fallback) {
    String v = values.get(canon(key));
    return v == null ? fallback : Long.parseLong(v.trim());
  }

  public boolean getBoolean(String key, boolean fallback) {
    String v = values.get(canon(key));
    return v == null ? fallback : Boolean.parseBoolean(v.trim());
  }

  /** A comma-separated value as a trimmed list; empty when the key is absent or blank. */
  public List<String> getList(String key) {
    String v = values.get(canon(key));
    if (v == null || v.isBlank()) {
      return List.of();
    }
    List<String> out = new ArrayList<>();
    for (String part : v.split(",")) {
      if (!part.isBlank()) {
        out.add(part.trim());
      }
    }
    return out;
  }

  /**
   * A view rooted at {@code prefix} — {@code config.sub("token").get("iss")} reads {@code
   * token.iss}.
   */
  public Config sub(String prefix) {
    String p = canon(prefix) + ".";
    Map<String, String> nested = new LinkedHashMap<>();
    values.forEach(
        (k, v) -> {
          if (k.startsWith(p)) {
            nested.put(k.substring(p.length()), v);
          }
        });
    return new Config(nested);
  }

  public Map<String, String> asMap() {
    return values;
  }

  public static final class Builder {
    private final Map<String, String> merged = new LinkedHashMap<>();

    private Builder() {}

    /** Seed the lowest layer; any later source overrides these. */
    public Builder defaults(Map<String, ?> defaults) {
      return source(defaults, "");
    }

    /** A generic (possibly nested) source — nested maps are flattened to dotted keys. */
    public Builder source(Map<String, ?> source, String prefix) {
      flatten(prefix, source, merged);
      return this;
    }

    public Builder fromProperties(Properties properties) {
      properties
          .stringPropertyNames()
          .forEach(
              name -> {
                String key =
                    name.toLowerCase(Locale.ROOT).startsWith(PROP_PREFIX)
                        ? name.substring(PROP_PREFIX.length())
                        : name;
                merged.put(canon(key), properties.getProperty(name));
              });
      return this;
    }

    /**
     * Load a {@code .properties} file if it exists (the path resolved from existing config +
     * defaults).
     */
    public Builder fromFile() {
      String path = resolved(CONFIG_FILE_KEY, DEFAULT_CONFIG_FILE);
      Path file = Path.of(path);
      if (Files.isRegularFile(file)) {
        Properties props = new Properties();
        try (var in = Files.newInputStream(file)) {
          props.load(in);
        } catch (IOException e) {
          throw new UncheckedIOException("Failed to read config file " + file, e);
        }
        return fromProperties(props);
      }
      try (InputStream in =
          Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
        if (in != null) {
          Properties props = new Properties();
          props.load(in);
          return fromProperties(props);
        }
      } catch (IOException e) {
        throw new UncheckedIOException("Failed to read config resource " + path, e);
      }
      return this;
    }

    public Builder fromFile(Path file) {
      Properties props = new Properties();
      try (var in = Files.newInputStream(file)) {
        props.load(in);
      } catch (IOException e) {
        throw new UncheckedIOException("Failed to read config file " + file, e);
      }
      return fromProperties(props);
    }

    public Builder fromSystemProperties() {
      System.getProperties()
          .stringPropertyNames()
          .forEach(
              name -> {
                if (name.toLowerCase(Locale.ROOT).startsWith(PROP_PREFIX)) {
                  merged.put(canon(name.substring(PROP_PREFIX.length())), System.getProperty(name));
                }
              });
      return this;
    }

    public Builder fromEnvironment() {
      return fromEnvironment(System.getenv());
    }

    Builder fromEnvironment(Map<String, String> env) {
      env.forEach(
          (name, value) -> {
            if (name.toUpperCase(Locale.ROOT).startsWith(ENV_PREFIX)) {
              String key = name.substring(ENV_PREFIX.length()).replace("_", ".");
              merged.put(canon(key), value);
            }
          });
      return this;
    }

    public Config build() {
      return new Config(merged);
    }

    /**
     * Read a key from what's merged so far (used to resolve the config-file path before loading
     * it).
     */
    private String resolved(String key, String fallback) {
      String envName = ENV_PREFIX + key.toUpperCase(Locale.ROOT).replace(".", "_");
      String env = System.getenv(envName);
      if (env != null) {
        return env;
      }
      String prop = System.getProperty(PROP_PREFIX + key);
      if (prop != null) {
        return prop;
      }
      return merged.getOrDefault(canon(key), fallback);
    }

    private static void flatten(String prefix, Map<?, ?> source, Map<String, String> into) {
      source.forEach(
          (k, v) -> {
            String key = prefix.isEmpty() ? String.valueOf(k) : prefix + "." + k;
            if (v instanceof Map<?, ?> nested) {
              flatten(key, nested, into);
            } else if (v != null) {
              into.put(canon(key), String.valueOf(v));
            }
          });
    }
  }
}
