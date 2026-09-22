package com.sdet.framework.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.Allure;
import io.qameta.allure.Step;
import io.restassured.module.jsv.JsonSchemaValidator;
import io.restassured.response.Response;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Strict JSON Schema (draft-04) contract validation for API responses.
 *
 * <p>Schemas live under {@code src/test/resources/schemas} and set
 * {@code additionalProperties: false}, so any undocumented field added by the API fails the build.
 * The schema used is attached to the Allure report next to the response it was checked against.
 */
public final class SchemaValidator {

    public static final String USER_SCHEMA = "schemas/user-schema.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SchemaValidator() {
    }

    /** Validates the whole response body against the schema at {@code schemaPath}. */
    @Step("Validate response body against JSON schema '{schemaPath}'")
    public static void assertMatchesSchema(Response response, String schemaPath) {
        attachSchema(schemaPath);
        response.then().assertThat().body(JsonSchemaValidator.matchesJsonSchemaInClasspath(schemaPath));
    }

    /**
     * Validates every element of a top-level JSON array against the schema at {@code schemaPath}.
     * Useful for list endpoints where one schema describes a single item.
     */
    @Step("Validate every item of the response array against JSON schema '{schemaPath}'")
    public static void assertEachItemMatchesSchema(Response response, String schemaPath) {
        attachSchema(schemaPath);
        JsonNode root;
        try {
            root = MAPPER.readTree(response.asString());
        } catch (IOException e) {
            throw new AssertionError("Response body is not valid JSON: " + response.asString(), e);
        }
        if (!root.isArray()) {
            throw new AssertionError("Expected a JSON array but got: " + root.getNodeType());
        }
        for (int i = 0; i < root.size(); i++) {
            JsonNode item = root.get(i);
            assertThat("Array item [" + i + "] " + item, item.toString(),
                    JsonSchemaValidator.matchesJsonSchemaInClasspath(schemaPath));
        }
    }

    private static void attachSchema(String schemaPath) {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(schemaPath)) {
            if (in == null) {
                throw new IllegalArgumentException("Schema not found on classpath: " + schemaPath);
            }
            Allure.addAttachment("JSON Schema: " + schemaPath, "application/json",
                    new String(in.readAllBytes(), StandardCharsets.UTF_8), ".json");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read schema " + schemaPath, e);
        }
    }
}
