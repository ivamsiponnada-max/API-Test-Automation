package com.sdet.framework.tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdet.framework.models.UserRequest;
import com.sdet.framework.models.UserResponse;
import com.sdet.framework.utils.SchemaValidator;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Owner;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Data-driven coverage of the {@code /users} resource.
 *
 * <ul>
 *     <li>Positive and negative create scenarios come from {@code testdata/user-data.json},
 *         so adding a case only means editing that file.</li>
 *     <li>List filtering runs over a hardcoded gender x status matrix, since those
 *         combinations are fixed by the API contract.</li>
 * </ul>
 */
@Epic("User Management")
@Feature("User data validation")
@Owner("sdet")
public class UserDataDrivenTests extends BaseTest {

    private static final String TEST_DATA_FILE = "testdata/user-data.json";
    private static final String UNIQUE_EMAIL_TOKEN = "<unique>";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** One positive create scenario from the JSON test data. */
    public record ValidUserCase(String caseName, String name, String gender, String status) {
        @Override
        public String toString() {
            return caseName;
        }
    }

    /** One negative create scenario and the validation error it must produce. */
    public record InvalidUserCase(String caseName, String name, String email, String gender, String status,
                                  String expectedField, String expectedMessage) {
        @Override
        public String toString() {
            return caseName;
        }
    }

    /** Root of {@code user-data.json}. */
    public record UserTestData(List<ValidUserCase> validUsers, List<InvalidUserCase> invalidUsers) {
    }

    // ------------------------------------------------------------------ data providers

    @DataProvider(name = "validUsers")
    public Object[][] validUsers() {
        return toRows(loadTestData().validUsers());
    }

    @DataProvider(name = "invalidUsers")
    public Object[][] invalidUsers() {
        return toRows(loadTestData().invalidUsers());
    }

    @DataProvider(name = "filterMatrix")
    public Object[][] filterMatrix() {
        return new Object[][]{
                {"male", "active"},
                {"male", "inactive"},
                {"female", "active"},
                {"female", "inactive"}
        };
    }

    // ------------------------------------------------------------------ tests

    @Test(dataProvider = "validUsers", description = "Create a user from each valid data row")
    @Story("Create user - valid data")
    @Severity(SeverityLevel.CRITICAL)
    public void createUserWithValidData(ValidUserCase testCase) {
        Allure.getLifecycle().updateTestCase(result -> result.setName("Create valid user: " + testCase.caseName()));

        UserRequest request = UserRequest.builder()
                .name(testCase.name())
                .email(uniqueEmail("dd." + testCase.caseName()))
                .gender(testCase.gender())
                .status(testCase.status())
                .build();

        Response response = userClient.createUser(request);

        assertStatus(response, 201);
        UserResponse created = response.as(UserResponse.class);
        registerForCleanup(created.getId());

        SchemaValidator.assertMatchesSchema(response, SchemaValidator.USER_SCHEMA);

        SoftAssert soft = new SoftAssert();
        soft.assertEquals(created.getName(), request.getName(), "name");
        soft.assertEquals(created.getEmail(), request.getEmail(), "email");
        soft.assertEquals(created.getGender(), request.getGender(), "gender");
        soft.assertEquals(created.getStatus(), request.getStatus(), "status");
        soft.assertAll();
    }

    @Test(dataProvider = "invalidUsers", description = "Reject each invalid data row with a field-level 422")
    @Story("Create user - invalid data")
    @Severity(SeverityLevel.NORMAL)
    public void createUserWithInvalidDataIsRejected(InvalidUserCase testCase) {
        Allure.getLifecycle().updateTestCase(result -> result.setName("Reject invalid user: " + testCase.caseName()));

        String email = UNIQUE_EMAIL_TOKEN.equals(testCase.email())
                ? uniqueEmail("dd.invalid." + testCase.expectedField())
                : testCase.email();

        UserRequest request = UserRequest.builder()
                .name(testCase.name())
                .email(email)
                .gender(testCase.gender())
                .status(testCase.status())
                .build();

        Response response = userClient.createUser(request);

        if (response.getStatusCode() == 201) {
            // Defensive: never leave data behind if the API unexpectedly accepts the payload.
            registerForCleanup(response.as(UserResponse.class).getId());
        }
        assertStatus(response, 422);

        List<Map<String, String>> errors = response.jsonPath().getList("$");
        assertFalse(errors.isEmpty(), "422 response should list validation errors");
        assertTrue(errors.stream().anyMatch(e -> testCase.expectedField().equals(e.get("field"))
                        && e.get("message") != null
                        && e.get("message").contains(testCase.expectedMessage())),
                "Expected error {field='" + testCase.expectedField() + "', message contains '"
                        + testCase.expectedMessage() + "'} but got: " + errors);
    }

    @Test(dataProvider = "filterMatrix", description = "List endpoint honours gender and status filters")
    @Story("List users - filtering")
    @Severity(SeverityLevel.NORMAL)
    public void listUsersFilteredByGenderAndStatus(String gender, String status) {
        Allure.getLifecycle().updateTestCase(result -> result.setName("Filter users by gender=" + gender + ", status=" + status));

        Response response = userClient.listUsers(Map.of("gender", gender, "status", status));

        assertStatus(response, 200);
        SchemaValidator.assertEachItemMatchesSchema(response, SchemaValidator.USER_SCHEMA);

        List<UserResponse> users = List.of(response.as(UserResponse[].class));
        assertFalse(users.isEmpty(), "Expected at least one user for gender=" + gender + ", status=" + status);

        SoftAssert soft = new SoftAssert();
        for (UserResponse user : users) {
            soft.assertEquals(user.getGender(), gender, "gender of user " + user.getId());
            soft.assertEquals(user.getStatus(), status, "status of user " + user.getId());
        }
        soft.assertAll();
    }

    // ------------------------------------------------------------------ helpers

    private static UserTestData loadTestData() {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(TEST_DATA_FILE)) {
            if (in == null) {
                throw new IllegalStateException("Test data file not found on classpath: " + TEST_DATA_FILE);
            }
            return MAPPER.readValue(in, UserTestData.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse test data file " + TEST_DATA_FILE, e);
        }
    }

    private static Object[][] toRows(List<?> cases) {
        if (cases == null || cases.isEmpty()) {
            throw new IllegalStateException("No test cases found in " + TEST_DATA_FILE);
        }
        return cases.stream().map(c -> new Object[]{c}).toArray(Object[][]::new);
    }
}
