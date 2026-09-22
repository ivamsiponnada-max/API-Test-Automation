package com.sdet.framework.api;

import com.sdet.framework.models.UserRequest;
import com.sdet.framework.utils.ConfigManager;
import io.qameta.allure.Step;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.LogConfig;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.http.ContentType;
import io.restassured.mapper.ObjectMapperType;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import io.restassured.specification.RequestSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;

/**
 * API client for the GoRest {@code /users} resource.
 *
 * <p>Tests talk to the API only through this class, never through raw REST Assured calls, so
 * transport concerns (base URI, auth, timeouts, logging, reporting) live in one place and tests
 * read as business intent. Every public method is an Allure {@link Step}, and each HTTP exchange
 * is attached to the report by {@link AllureRestAssured}.
 */
public class RestClient {

    private static final String AUTHORIZATION = "Authorization";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    private final RequestSpecification baseSpec;

    /** Authenticated client using the token from {@link ConfigManager#getApiToken()}. */
    public RestClient() {
        this(ConfigManager.getInstance().getApiToken().orElse(null));
    }

    private RestClient(String bearerToken) {
        this.baseSpec = buildSpec(bearerToken);
    }

    /** Client that sends no Authorization header, for negative security tests. */
    public static RestClient unauthenticated() {
        return new RestClient(null);
    }

    @Step("POST /users - create user {user}")
    public Response createUser(UserRequest user) {
        return request()
                .body(user)
                .when()
                .post(Endpoints.USERS);
    }

    @Step("GET /users/{id} - fetch user")
    public Response getUser(long id) {
        return request()
                .pathParam(Endpoints.PATH_PARAM_ID, id)
                .when()
                .get(Endpoints.USER_BY_ID);
    }

    @Step("GET /users - list users with filters {queryParams}")
    public Response listUsers(Map<String, ?> queryParams) {
        return request()
                .queryParams(queryParams)
                .when()
                .get(Endpoints.USERS);
    }

    @Step("PUT /users/{id} - replace user with {user}")
    public Response updateUser(long id, UserRequest user) {
        return request()
                .pathParam(Endpoints.PATH_PARAM_ID, id)
                .body(user)
                .when()
                .put(Endpoints.USER_BY_ID);
    }

    @Step("PATCH /users/{id} - partially update user with {user}")
    public Response patchUser(long id, UserRequest user) {
        return request()
                .pathParam(Endpoints.PATH_PARAM_ID, id)
                .body(user)
                .when()
                .patch(Endpoints.USER_BY_ID);
    }

    @Step("DELETE /users/{id} - delete user")
    public Response deleteUser(long id) {
        return request()
                .pathParam(Endpoints.PATH_PARAM_ID, id)
                .when()
                .delete(Endpoints.USER_BY_ID);
    }

    /** Fresh specification per call, with a unique id so a single exchange can be traced in logs. */
    private RequestSpecification request() {
        return given()
                .spec(baseSpec)
                .header(REQUEST_ID_HEADER, UUID.randomUUID().toString());
    }

    private static RequestSpecification buildSpec(String bearerToken) {
        ConfigManager config = ConfigManager.getInstance();

        RestAssuredConfig restAssuredConfig = RestAssuredConfig.config()
                .httpClient(HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", config.getConnectTimeoutMs())
                        .setParam("http.socket.timeout", config.getSocketTimeoutMs()))
                // Keeps the bearer token out of both console logs and Allure attachments.
                .logConfig(LogConfig.logConfig()
                        .blacklistHeader(AUTHORIZATION)
                        .enableLoggingOfRequestAndResponseIfValidationFails())
                .objectMapperConfig(ObjectMapperConfig.objectMapperConfig()
                        .defaultObjectMapperType(ObjectMapperType.JACKSON_2));

        RequestSpecBuilder builder = new RequestSpecBuilder()
                .setBaseUri(config.getBaseUri())
                .setBasePath(config.getBasePath())
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .setConfig(restAssuredConfig)
                .addFilter(new AllureRestAssured());

        if (config.isConsoleLoggingEnabled()) {
            builder.addFilter(new ConsoleLoggingFilter());
        }
        if (bearerToken != null && !bearerToken.isBlank()) {
            builder.addHeader(AUTHORIZATION, "Bearer " + bearerToken);
        }
        return builder.build();
    }

    /**
     * Compact one-line-per-exchange console log with timing. Full request/response detail goes
     * to Allure; bodies are logged at DEBUG so CI output stays readable. Never logs headers,
     * so credentials cannot leak.
     */
    static final class ConsoleLoggingFilter implements Filter {

        private static final Logger LOG = LoggerFactory.getLogger(ConsoleLoggingFilter.class);

        @Override
        public Response filter(FilterableRequestSpecification requestSpec,
                               FilterableResponseSpecification responseSpec,
                               FilterContext context) {
            long start = System.nanoTime();
            Response response = context.next(requestSpec, responseSpec);
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            LOG.info("{} {} -> {} ({} ms) [{}]",
                    requestSpec.getMethod(),
                    requestSpec.getURI(),
                    response.getStatusCode(),
                    elapsedMs,
                    requestSpec.getHeaders().getValue(REQUEST_ID_HEADER));

            if (LOG.isDebugEnabled()) {
                Object body = requestSpec.getBody();
                if (body != null) {
                    LOG.debug("Request body: {}", body);
                }
                LOG.debug("Response body: {}", response.asString());
            }
            return response;
        }
    }
}
