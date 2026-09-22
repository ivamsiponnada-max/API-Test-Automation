# API Test Automation Framework

![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk)
![REST Assured](https://img.shields.io/badge/REST%20Assured-5.5-green)
![TestNG](https://img.shields.io/badge/TestNG-7.10-red)
![Allure](https://img.shields.io/badge/Report-Allure-yellow)
![CI](https://img.shields.io/badge/CI-GitHub%20Actions-blue?logo=githubactions)

An API test automation framework built with **Java 17, REST Assured, TestNG and Allure**. It tests the
public [GoRest](https://gorest.co.in) `/users` API, which stores real data, and covers request chaining,
strict JSON Schema contract checks, data-driven testing, and CI with a published Allure report.

> **Live report:** `https://venkatasrivamsiponnada.github.io/API-Test-Automation/` (available after the first CI run on `main`)

---

## Highlights

| Capability | How it is implemented |
|---|---|
| **API client pattern** | Tests never call REST Assured directly; `RestClient` exposes business methods (`createUser`, `getUser`, ...) |
| **Request chaining** | `UserChainingTests`: `POST → extract id → GET → PUT → PATCH → DELETE → GET (404)` linked with `dependsOnMethods` |
| **Strict schema validation** | Draft-04 schema with `additionalProperties: false`; validates single objects and every item of list responses |
| **Data-driven testing** | `@DataProvider` backed by `testdata/user-data.json` (positive + negative) and a hardcoded gender × status matrix |
| **Logging & filters** | `AllureRestAssured` attaches every request/response to the report; a custom console filter logs method, URI, status, timing and a per-request `X-Request-Id` |
| **Secret hygiene** | Token read only from env var/system property; `Authorization` header blacklisted from all logs and attachments |
| **Config & environment routing** | `ConfigManager` resolves system property → env var → `<env>.key` → `key` |
| **Self-cleaning tests** | Every created user is tracked and deleted in `@AfterClass(alwaysRun = true)`, even when a test fails |
| **CI/CD** | GitHub Actions runs the suite, keeps Allure trend history, and publishes the report to GitHub Pages |

---

## Architecture

```mermaid
flowchart LR
    subgraph Tests["tests/"]
        BT[BaseTest<br/>lifecycle · cleanup · env]
        CT[UserChainingTests]
        DT[UserDataDrivenTests]
        CT --> BT
        DT --> BT
    end

    subgraph API["api/"]
        RC[RestClient<br/>@Step methods]
        EP[Endpoints]
        RC --> EP
    end

    subgraph Utils["utils/"]
        CM[ConfigManager]
        SV[SchemaValidator]
    end

    subgraph Models["models/"]
        UQ[UserRequest]
        UR[UserResponse]
    end

    subgraph Res["resources/"]
        CP[(config.properties)]
        SC[(schemas/user-schema.json)]
        TD[(testdata/user-data.json)]
    end

    Tests --> RC
    Tests --> SV
    Tests --> Models
    DT --> TD
    RC --> CM
    CM --> CP
    SV --> SC
    RC -- "HTTPS + Bearer token" --> GR[(GoRest API)]
    RC -- "AllureRestAssured filter" --> AL[[Allure results]]
```

**Request flow:** Test → `RestClient` method (Allure step) → shared `RequestSpecification`
(base URI, auth, timeouts, filters) → GoRest → `Response` → schema validation + POJO assertions.

---

## Project Structure

```
my-api-test-framework/
├── .github/workflows/maven.yml          # CI: test → Allure report → GitHub Pages
├── src/test/java/com/sdet/framework/
│   ├── api/
│   │   ├── Endpoints.java               # Resource paths
│   │   └── RestClient.java              # API client, request spec, logging filters
│   ├── models/
│   │   ├── UserRequest.java             # Request POJO (builder, NON_NULL for PATCH)
│   │   └── UserResponse.java            # Response POJO
│   ├── tests/
│   │   ├── BaseTest.java                # Suite/class lifecycle, cleanup, helpers
│   │   ├── UserChainingTests.java       # CRUD lifecycle chain + negative checks
│   │   └── UserDataDrivenTests.java     # JSON-driven + matrix-driven tests
│   └── utils/
│       ├── ConfigManager.java           # Layered configuration & env routing
│       └── SchemaValidator.java         # Strict JSON Schema assertions
├── src/test/resources/
│   ├── schemas/user-schema.json
│   ├── testdata/user-data.json
│   ├── config.properties
│   └── testng.xml
├── pom.xml
└── README.md
```

---

## Prerequisites

- **JDK 17+** (`java -version`)
- **Maven 3.9+** (`mvn -v`)
- A free **GoRest access token**: sign in at <https://gorest.co.in/consumer/login> and copy your token.
  GoRest requires a token for `POST`, `PUT`, `PATCH` and `DELETE`.

---

## Local Setup

```bash
git clone https://github.com/VenkataSriVamsiPonnada/API-Test-Automation.git
cd API-Test-Automation
```

Provide your token (never commit it):

```bash
# macOS / Linux
export GOREST_TOKEN=your_token_here

# Windows PowerShell
$env:GOREST_TOKEN = "your_token_here"
```

---

## Running the Tests

| Goal | Command |
|---|---|
| Run the full suite | `mvn clean test` |
| Pass the token inline | `mvn clean test -Dapi.token=your_token_here` |
| Target another environment | `mvn clean test -Dtest.env=staging` |
| Override the base URI | `mvn clean test -Dbase.uri=https://my-gateway.example.com` |
| Run a single class | `mvn clean test -Dtest=UserChainingTests` |
| Run a single method | `mvn clean test -Dtest=UserChainingTests#createUser` |
| Quieter console | `mvn clean test -Dlog.console.enabled=false` |

> `-Dtest=...` bypasses `testng.xml` and runs the selected classes directly.

### Allure Report

```bash
mvn allure:serve                 # build a report from target/allure-results and open it in a browser
mvn allure:report                # write a static report to target/site/allure-maven-plugin
```

The Allure CLI is downloaded automatically by `allure-maven`, so you don't need to install it.
Each test in the report shows its steps (`POST /users - create user ...`), the full HTTP request and
response, the JSON schema used, and the environment it ran against.

---

## Configuration

`src/test/resources/config.properties`:

```properties
test.env=prod
prod.base.uri=https://gorest.co.in
prod.base.path=/public/v2
staging.base.uri=https://gorest.co.in
staging.base.path=/public/v2
http.connect.timeout.ms=10000
http.socket.timeout.ms=20000
log.console.enabled=true
```

Resolution order for every key (highest first):

1. JVM system property: `-Dbase.uri=...`
2. Environment variable: `BASE_URI=...` (dots become underscores, upper-cased)
3. Environment-scoped key: `<test.env>.base.uri`
4. Global key: `base.uri`

The API token is the only secret. It is read from `-Dapi.token` or `GOREST_TOKEN` and never from the file.

---

## Test Coverage

**`UserChainingTests`** (full lifecycle, one user)

1. `createUser`: 201, schema, fields match, id extracted
2. `getCreatedUser`: GET by the extracted id
3. `updateUserWithPut`: full replacement, then read back to confirm it was saved
4. `patchUserStatus`: partial update; other fields unchanged
5. `deleteUser`: 204 with empty body
6. `deletedUserIsNotFound`: 404 `Resource not found`
7. `duplicateEmailIsRejected`: 422 on a duplicate email
8. `createWithoutTokenIsUnauthorized`: 401 without a bearer token

**`UserDataDrivenTests`**

- `createUserWithValidData`: one run per `validUsers` row in `user-data.json`
- `createUserWithInvalidDataIsRejected`: one run per `invalidUsers` row, asserting the field-level 422 error
- `listUsersFilteredByGenderAndStatus`: 2×2 gender × status matrix, validating every item against the schema

To add a scenario, add an object to `user-data.json`. No code changes are needed.
Use `"email": "<unique>"` to generate a collision-free valid address.

---

## CI/CD (GitHub Actions)

Workflow: [`.github/workflows/maven.yml`](.github/workflows/maven.yml)

**Triggers:** push and PR to `main`, a weekday schedule (06:00 UTC), and manual `workflow_dispatch`
with an optional environment input.

**Pipeline:**

1. Check out the code and set up Temurin JDK 17 with the Maven cache.
2. Run `mvn clean test` (`continue-on-error`, so a report is still produced when tests fail).
3. Restore `history/` from the `gh-pages` branch so Allure can show **trend charts** across runs.
4. Write `executor.json` so the report links back to the workflow run.
5. Run `mvn allure:report`.
6. Upload the raw results and the HTML report as a build artifact (kept 14 days).
7. On `main` (not PRs), publish the report to the `gh-pages` branch.
8. Fail the job if the test step failed, so the commit's status check stays accurate.

### One-time repository setup

1. **Add the token secret:** *Settings → Secrets and variables → Actions → New repository secret*
   - Name: `GOREST_TOKEN`
   - Value: your GoRest token
2. **Allow the workflow to push:** *Settings → Actions → General → Workflow permissions →*
   **Read and write permissions**.
3. **Enable Pages:** after the first successful run on `main`, go to *Settings → Pages →
   Build and deployment → Source: Deploy from a branch* and select **`gh-pages` / `(root)`**.

> Pull requests from forks don't receive repository secrets, so their runs fail fast in
> `BaseTest` with a clear "No API token configured" message.

---

## Design Notes

- **Why GoRest instead of ReqRes?** ReqRes is a mock: users you create are not stored, so
  `GET /users/{createdId}` returns 404 and a real CRUD chain cannot be tested. GoRest stores data.
- **Unique test data:** GoRest enforces unique emails, so every email is generated at runtime.
- **Sequential execution:** the suite runs sequentially to stay within the public API's rate limits.
  For a private API, set `parallel="classes"` in `testng.xml`.
- **Soft assertions** are used for field-by-field comparisons, so one failure reports every mismatched field.

---

## License

MIT
