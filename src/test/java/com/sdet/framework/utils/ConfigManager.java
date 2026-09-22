package com.sdet.framework.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

/**
 * Thread-safe, lazily initialised configuration singleton.
 *
 * <p>Every lookup resolves in the following order:
 * <ol>
 *     <li>JVM system property ({@code -Dbase.uri=...})</li>
 *     <li>OS environment variable ({@code BASE_URI=...})</li>
 *     <li>Environment-scoped key in {@code config.properties} ({@code prod.base.uri})</li>
 *     <li>Global key in {@code config.properties} ({@code base.uri})</li>
 * </ol>
 * This lets the same build run locally, against another environment, or in CI without code changes.
 */
public final class ConfigManager {

    private static final String CONFIG_FILE = "config.properties";
    private static final String DEFAULT_ENV = "prod";
    private static final String TOKEN_ENV_VARIABLE = "GOREST_TOKEN";

    private final Properties properties = new Properties();
    private final String environment;

    private ConfigManager() {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (in == null) {
                throw new IllegalStateException("Could not find '" + CONFIG_FILE + "' on the test classpath");
            }
            properties.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load '" + CONFIG_FILE + "'", e);
        }
        this.environment = lookup("test.env").orElse(DEFAULT_ENV).trim().toLowerCase(Locale.ROOT);
    }

    private static final class Holder {
        private static final ConfigManager INSTANCE = new ConfigManager();
    }

    public static ConfigManager getInstance() {
        return Holder.INSTANCE;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getBaseUri() {
        return getRequired("base.uri");
    }

    public String getBasePath() {
        return get("base.path").orElse("");
    }

    public int getConnectTimeoutMs() {
        return getInt("http.connect.timeout.ms", 10_000);
    }

    public int getSocketTimeoutMs() {
        return getInt("http.socket.timeout.ms", 20_000);
    }

    public boolean isConsoleLoggingEnabled() {
        return Boolean.parseBoolean(get("log.console.enabled").orElse("true"));
    }

    public String getTestDataEmailDomain() {
        return get("testdata.email.domain").orElse("sdet-framework.test");
    }

    /**
     * The bearer token is secret, so it is read only from a system property or the
     * {@code GOREST_TOKEN} environment variable, never from the committed properties file.
     */
    public Optional<String> getApiToken() {
        String fromProperty = System.getProperty("api.token");
        if (isPresent(fromProperty)) {
            return Optional.of(fromProperty.trim());
        }
        String fromEnv = System.getenv(TOKEN_ENV_VARIABLE);
        return isPresent(fromEnv) ? Optional.of(fromEnv.trim()) : Optional.empty();
    }

    /** Resolves {@code key} honouring the full override chain, including the environment scope. */
    public Optional<String> get(String key) {
        Optional<String> override = lookupOverride(key);
        if (override.isPresent()) {
            return override;
        }
        String scoped = properties.getProperty(environment + "." + key);
        if (isPresent(scoped)) {
            return Optional.of(scoped.trim());
        }
        return Optional.ofNullable(properties.getProperty(key)).map(String::trim).filter(ConfigManager::isPresent);
    }

    public String getRequired(String key) {
        return get(key).orElseThrow(() -> new IllegalStateException(
                "Missing required configuration '" + key + "' for environment '" + environment + "'"));
    }

    private int getInt(String key, int defaultValue) {
        return get(key).map(value -> {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Configuration '" + key + "' must be an integer but was '" + value + "'", e);
            }
        }).orElse(defaultValue);
    }

    /** Used only while bootstrapping, before the environment is known. */
    private Optional<String> lookup(String key) {
        return lookupOverride(key).or(() -> Optional.ofNullable(properties.getProperty(key)));
    }

    private static Optional<String> lookupOverride(String key) {
        String fromProperty = System.getProperty(key);
        if (isPresent(fromProperty)) {
            return Optional.of(fromProperty.trim());
        }
        String fromEnv = System.getenv(key.replace('.', '_').toUpperCase(Locale.ROOT));
        return isPresent(fromEnv) ? Optional.of(fromEnv.trim()) : Optional.empty();
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
