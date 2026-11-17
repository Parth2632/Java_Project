# MathVerse Java backend: how it works

The Java backend in `backend-java/` replaces the Python backend in `backend/`
without the frontend noticing: same paths, JSON, status codes and error bodies.
The Python code is the specification, and each Java file names its Python
partner. This file grows by one section per phase.

## Running it

From `backend-java/`:

```bash
copy .env.example .env      # then set JWT_SECRET_KEY (at least 32 characters)
mvnw test                   # run all tests (in-memory database, needs no .env)
mvnw spring-boot:run        # start the API on http://localhost:8001
```

`mvnw` is the Maven wrapper: the first run downloads Maven 3.9.16 (its checksum
is pinned in `.mvn/wrapper/maven-wrapper.properties`) and the libraries, so
nothing has to be installed except Java 21 or newer.

## Phase 1: setup and accounts

### The pieces

| Java | Python | What it does |
|---|---|---|
| `MathVerseApplication` | `main.py` | Starts the server. Reads `.env` as defaults that real environment variables override. |
| `config/Settings` | `config.py` | Every setting in one place. Refuses to start without `JWT_SECRET_KEY`. |
| `config/WebConfig` | `main.py` (CORS) | Lets the frontend on port 5173 call the API; registers the sign-in check. |
| `config/ErrorHandler`, `ApiException` | FastAPI built-ins, `HTTPException` | Turns every error into FastAPI's body shape. |
| `model/*` | `models.py` | The five tables as classes, same table and column names. |
| `model/*Repository` | SQLAlchemy queries | Database queries; Spring writes the SQL from method names. |
| `security/Passwords` | `security.py` | bcrypt hashing. |
| `security/Tokens` | `security.py` | JWT login tokens, HS256, `sub` = user id, 24 h expiry. |
| `security/SignedIn`, `AuthInterceptor`, `CurrentUser` | `deps.py` | Who is calling, and may they call this endpoint. |
| `dto/*` | `schemas.py` | Request and response shapes, with their checks. |
| `service/AuthService` | `routers/auth.py` | Register, log in, demo login. |
| `service/DemoData` | `seed.py` | Creates the three demo accounts. |
| `controller/*` | `routers/*` | Thin endpoint classes that hand work to the services. |

### How a request flows

1. The CORS filter adds the headers the browser needs (also on errors, so the
   frontend can read a 401).
2. `AuthInterceptor` runs before the endpoint. If the endpoint is marked
   `@SignedIn`, it reads `Authorization: Bearer <token>`, checks the token's
   signature and expiry, and loads the user. No valid token gives 401; the wrong
   role gives 403. This happens before the body is read, just as FastAPI checks
   dependencies first.
3. Spring reads the JSON body into a record (for example `RegisterRequest`) and
   runs the checks written on it (`@NotNull`, `@Size`, `@ValidEmail`, ...).
4. The controller calls a service, which does the work inside a database
   transaction and returns a response record.
5. Spring writes the record as JSON. Any exception goes to `ErrorHandler`.

### Decisions worth explaining

- **Identity comes only from the token.** Endpoints get the user through a
  `@CurrentUser User user` parameter that only the interceptor can fill. No
  request body has a user id field, and unknown fields are rejected.
- **Why not the full Spring Security library?** It would do the same job through
  a filter chain that is much harder to explain. Only its bcrypt class is used.
- **snake_case JSON.** Java names are camelCase (`accessToken`); one setting in
  `application.properties` turns them into the names the frontend expects
  (`access_token`).
- **Unknown fields are rejected** (`fail-on-unknown-properties=true`), like
  `extra="forbid"` in Python. Sending `{"points": 1000000}` to register gives 422.
- **Error bodies match FastAPI.** Most errors are `{"detail": "message"}`.
  Validation errors are `{"detail": [{"type", "loc", "msg"}]}`, listed in field
  order. The frontend shows `"<last part of loc>: <msg>"`, so these must match.
- **Null is not zero.** Columns that can mean "not answered" or "not measured"
  use `Integer`, `Double`, `Boolean`, which can be null. `int` and `double` would
  quietly turn a missing value into 0.
- **One database connection.** SQLite lets only one writer in at a time, so a
  pool of one connection avoids "database is locked" errors. Fine for a class
  project; a bigger deployment would use PostgreSQL.
- **Demo login** checks the role first (422 for an unknown role, as FastAPI
  does), then returns 404 unless `DEMO_MODE` is on, then returns only an
  `is_demo` account. It never creates one.

### Small differences from Python

- `JWT_SECRET_KEY` must be at least 32 characters; the Java JWT library refuses
  shorter keys for HS256. Python accepted any length.
- The "demo accounts not created" message names the seed command generally; the
  Java seed command is added in phase 4.
- The `type` field inside 422 errors is close to Pydantic's but not always the
  same word (for example `string_length` for both too short and too long). The
  frontend does not read it.

### Tests

`ApiTest` is the Java version of `conftest.py`: it starts the whole app against
an in-memory SQLite database, empties it before each test, and offers
`register(...)`, `postJson(...)` and `getPath(...)` helpers.

| Python file | Python tests | Java file | Java tests | Notes |
|---|---|---|---|---|
| `test_auth.py` | 25 | `AuthTest` | 22 | 3 cases of `test_endpoints_require_a_token` wait for their endpoints (phases 3 and 4); the report checks in the last test wait for phase 4 |
| (none) | | `ContractTest` | 43 | JSON names, error bodies, tokens, CORS, emails |
