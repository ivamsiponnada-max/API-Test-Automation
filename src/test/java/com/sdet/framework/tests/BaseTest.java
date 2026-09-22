package com.sdet.framework.tests;

import com.sdet.framework.api.RestClient;
import com.sdet.framework.utils.ConfigManager;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.testng.Assert.assertEquals;

/**
 * Common lifecycle for every API test class.
 *
 * <ul>
 *     <li>Checks the configuration before the suite runs and fails fast if the API token is missing.</li>
 *     <li>Writes Allure's {@code environment.properties} so the report shows where the run pointed.</li>
 *     <li>Gives each test class its own {@link RestClient}.</li>
 *     <li>Tracks every user a test creates and deletes leftovers after the class, so the
 *         shared public API is not littered with test data even when assertions fail.</li>
 * </ul>
 */
public abstract class BaseTest {

    protected static final Logger LOG = LoggerFactory.getLogger(BaseTest.class);
    protected static final ConfigManager CONFIG = ConfigManager.getInstance();

    protected RestClient userClient;

    private final Set<Long> createdUserIds = ConcurrentHashMap.newKeySet();

    @BeforeSuite(alwaysRun = true)
    public void verifyConfiguration() {
        LOG.info("Running against environment '{}' -> {}{}", CONFIG.getEnvironment(), CONFIG.getBaseUri(), CONFIG.getBasePath());
        writeAllureEnvironment();
        if (CONFIG.getApiToken().isEmpty()) {
            throw new IllegalStateException(
                    "No API token configured. Get a free token at https://gorest.co.in/consumer/login and supply it via "
                            + "the GOREST_TOKEN environment variable or -Dapi.token=<token>.");
        }
    }

    @BeforeClass(alwaysRun = true)
    public void initClient() {
        userClient = new RestClient();
    }

    @AfterClass(alwaysRun = true)
    public void deleteCreatedUsers() {
        for (Long id : createdUserIds) {
            try {
                int status = userClient.deleteUser(id).getStatusCode();
                LOG.info("Cleanup: DELETE user {} -> {}", id, status);
            } catch (RuntimeException e) {
                LOG.warn("Cleanup: could not delete user {}: {}", id, e.getMessage());
            }
        }
        createdUserIds.clear();
    }

    /** Registers a user for automatic deletion after the test class finishes. */
    protected void registerForCleanup(long userId) {
        createdUserIds.add(userId);
    }

    /** Call once a test has deleted a user itself, so cleanup does not try again. */
    protected void unregisterFromCleanup(long userId) {
        createdUserIds.remove(userId);
    }

    /** Collision-free email, because GoRest enforces globally unique addresses. */
    protected String uniqueEmail(String prefix) {
        String safePrefix = prefix.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", ".").replaceAll("(^\\.|\\.$)", "");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return safePrefix + "." + suffix + "@" + CONFIG.getTestDataEmailDomain();
    }

    @Step("Verify HTTP status is {expectedStatus}")
    protected void assertStatus(Response response, int expectedStatus) {
        assertEquals(response.getStatusCode(), expectedStatus,
                "Unexpected HTTP status. Response body: " + response.asString());
    }

    private void writeAllureEnvironment() {
        Path resultsDir = Paths.get(System.getProperty("allure.results.directory", "target/allure-results"));
        Properties env = new Properties();
        env.setProperty("Environment", CONFIG.getEnvironment());
        env.setProperty("Base.URI", CONFIG.getBaseUri() + CONFIG.getBasePath());
        env.setProperty("Java.Version", System.getProperty("java.version"));
        env.setProperty("OS", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        try {
            Files.createDirectories(resultsDir);
            try (OutputStream out = Files.newOutputStream(resultsDir.resolve("environment.properties"))) {
                env.store(out, "Allure environment");
            }
        } catch (IOException e) {
            LOG.warn("Could not write Allure environment.properties: {}", e.getMessage());
        }
    }
}
