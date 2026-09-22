package com.sdet.framework.tests;

import com.sdet.framework.api.RestClient;
import com.sdet.framework.models.UserRequest;
import com.sdet.framework.models.UserResponse;
import com.sdet.framework.utils.SchemaValidator;
import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Owner;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * End-to-end request chaining over the full user lifecycle:
 * <pre>
 *   POST (create) -> extract id -> GET -> PUT -> PATCH -> DELETE -> GET (404)
 * </pre>
 * Each step depends on the previous one via {@code dependsOnMethods}. If a step fails, the steps
 * after it are reported as skipped rather than failing for an unrelated reason.
 */
@Epic("User Management")
@Feature("User CRUD lifecycle")
@Owner("sdet")
public class UserChainingTests extends BaseTest {

    private UserRequest createdPayload;
    private UserRequest updatedPayload;
    private long userId;

    @Test(description = "Create a user and extract its id for the chain")
    @Story("Create user")
    @Severity(SeverityLevel.BLOCKER)
    @Description("POST a new user, validate 201 + strict schema, and store the generated id for later requests.")
    public void createUser() {
        createdPayload = UserRequest.builder()
                .name("Chain Test User")
                .email(uniqueEmail("chain.create"))
                .gender("female")
                .status("active")
                .build();

        Response response = userClient.createUser(createdPayload);

        assertStatus(response, 201);
        SchemaValidator.assertMatchesSchema(response, SchemaValidator.USER_SCHEMA);

        UserResponse created = response.as(UserResponse.class);
        assertNotNull(created.getId(), "Created user must have an id");
        assertTrue(created.getId() > 0, "Created user id must be positive");
        assertUserMatches(created, createdPayload);

        userId = created.getId();
        registerForCleanup(userId);
        Allure.parameter("userId", userId);
    }

    @Test(dependsOnMethods = "createUser", description = "Fetch the created user by the extracted id")
    @Story("Read user")
    @Severity(SeverityLevel.CRITICAL)
    public void getCreatedUser() {
        Response response = userClient.getUser(userId);

        assertStatus(response, 200);
        SchemaValidator.assertMatchesSchema(response, SchemaValidator.USER_SCHEMA);

        UserResponse fetched = response.as(UserResponse.class);
        assertEquals(fetched.getId().longValue(), userId, "Fetched id should match the created id");
        assertUserMatches(fetched, createdPayload);
    }

    @Test(dependsOnMethods = "getCreatedUser", description = "Replace every field of the user with PUT")
    @Story("Update user")
    @Severity(SeverityLevel.CRITICAL)
    public void updateUserWithPut() {
        updatedPayload = UserRequest.builder()
                .name("Chain Test User Updated")
                .email(uniqueEmail("chain.updated"))
                .gender("male")
                .status("inactive")
                .build();

        Response response = userClient.updateUser(userId, updatedPayload);

        assertStatus(response, 200);
        SchemaValidator.assertMatchesSchema(response, SchemaValidator.USER_SCHEMA);
        assertUserMatches(response.as(UserResponse.class), updatedPayload);

        // Read back to confirm the update was saved, not just echoed back.
        Response readBack = userClient.getUser(userId);
        assertStatus(readBack, 200);
        assertUserMatches(readBack.as(UserResponse.class), updatedPayload);
    }

    @Test(dependsOnMethods = "updateUserWithPut", description = "Change a single field with PATCH")
    @Story("Update user")
    @Severity(SeverityLevel.NORMAL)
    @Description("PATCH only the status; every other field must keep the value set by the PUT step.")
    public void patchUserStatus() {
        UserRequest patch = UserRequest.builder().status("active").build();

        Response response = userClient.patchUser(userId, patch);

        assertStatus(response, 200);
        SchemaValidator.assertMatchesSchema(response, SchemaValidator.USER_SCHEMA);

        UserRequest expected = updatedPayload.toBuilder().status("active").build();
        assertUserMatches(response.as(UserResponse.class), expected);
    }

    @Test(dependsOnMethods = "patchUserStatus", description = "Delete the user")
    @Story("Delete user")
    @Severity(SeverityLevel.CRITICAL)
    public void deleteUser() {
        Response response = userClient.deleteUser(userId);

        assertStatus(response, 204);
        assertTrue(response.asString().isEmpty(), "DELETE should return an empty body");
        unregisterFromCleanup(userId);
    }

    @Test(dependsOnMethods = "deleteUser", description = "A deleted user can no longer be fetched")
    @Story("Delete user")
    @Severity(SeverityLevel.CRITICAL)
    public void deletedUserIsNotFound() {
        Response response = userClient.getUser(userId);

        assertStatus(response, 404);
        assertEquals(response.jsonPath().getString("message"), "Resource not found");
    }

    @Test(description = "Creating a user with an email that already exists is rejected")
    @Story("Create user")
    @Severity(SeverityLevel.NORMAL)
    public void duplicateEmailIsRejected() {
        UserRequest original = UserRequest.builder()
                .name("Duplicate Email Original")
                .email(uniqueEmail("chain.duplicate"))
                .gender("male")
                .status("active")
                .build();

        Response first = userClient.createUser(original);
        assertStatus(first, 201);
        registerForCleanup(first.as(UserResponse.class).getId());

        Response second = userClient.createUser(original.toBuilder().name("Duplicate Email Copy").build());

        assertStatus(second, 422);
        List<Map<String, String>> errors = second.jsonPath().getList("$");
        assertTrue(errors.stream().anyMatch(e -> "email".equals(e.get("field"))
                        && e.get("message") != null && e.get("message").contains("already been taken")),
                "Expected an 'email has already been taken' error but got: " + errors);
    }

    @Test(description = "Write operations without a bearer token are rejected")
    @Story("Security")
    @Severity(SeverityLevel.BLOCKER)
    public void createWithoutTokenIsUnauthorized() {
        UserRequest user = UserRequest.builder()
                .name("No Token User")
                .email(uniqueEmail("chain.notoken"))
                .gender("female")
                .status("active")
                .build();

        Response response = RestClient.unauthenticated().createUser(user);

        assertStatus(response, 401);
        assertEquals(response.jsonPath().getString("message"), "Authentication failed");
    }

    private void assertUserMatches(UserResponse actual, UserRequest expected) {
        SoftAssert soft = new SoftAssert();
        soft.assertEquals(actual.getName(), expected.getName(), "name");
        soft.assertEquals(actual.getEmail(), expected.getEmail(), "email");
        soft.assertEquals(actual.getGender(), expected.getGender(), "gender");
        soft.assertEquals(actual.getStatus(), expected.getStatus(), "status");
        soft.assertAll();
    }
}
