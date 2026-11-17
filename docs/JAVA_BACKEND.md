# MathVerse Java backend: how it works

The Java backend in `backend/` replaced the original Python backend without
the frontend noticing: same paths, JSON, status codes and error bodies.
The Python code is the specification, and each Java file names its Python
partner. This file grows by one section per phase.

During phases 1–5 the Java code lived in `backend-java/`, beside the Python
code in `backend/`. In phase 6 (2026-11-17) it moved to `backend/` and the
Python code was removed; it remains in the git history (for example commit
`9db88b9`). Paths below are given as they are now.

## Running it

From `backend/`:

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

## Phase 2: game logic (no endpoints)

All of these are plain Java classes with static methods, except `Hints`, which
needs the settings. None of them touches the database or the web layer.

| Java (`service/`) | Python (`services/`) | What it does |
|---|---|---|
| `Mastery` | `mastery.py` | BKT update and the difficulty bands. Same four parameters, same 0.01–0.99 limits, learning step only after a correct answer. |
| `MasteryDisplay` | `mastery_display.py` | Band in words, percentage limited to 5–95 for display only. |
| `Game` | `game.py` | Points, streak and speed bonus, victory bonus, 7 to win and 7 to lose. Levels and progress need the database and come in phase 3. |
| `Curriculum` | `curriculum.py` | The six bosses, their topics and unlock order. |
| `Attention` | `attention.py` | Score and label from the two percentages. Never read by anything academic. |
| `Questions`, `StoryQuestions` | `questions.py`, `story_questions.py` | The generator: random numbers, computed answer, hint from the same numbers. About half are stories. |
| `QuestionBank` | `question_bank.py` | 36 fixed backup questions, used only if the generator throws. |
| `Hints`, `HintWriter`, `GroqHintWriter` | `hints.py` | The built-in hint, optionally reworded by Groq (6 s limit, falls back to the built-in hint, never needed). |
| `Rng` | `random.Random` | `randint`, `choice`, `shuffle` with Python's meaning. |
| `Rounding` | `round()` | Python's rounding, halves to even. |

### Decisions worth explaining

- **Same wording as Python, checked.** Question texts and hints were copied by
  hand. To check the copy, every question and hint shape (digits replaced by N)
  over 3,000 seeds per boss and tier was dumped from both backends and
  compared: all 68 forms are identical. The numbers themselves differ, because
  Java and Python draw different random numbers from the same seed.
- **Rounding.** Python's `round()` rounds an exact half to the even neighbour
  (`round(12.5) == 12`). `Math.round` would give 13, so some mastery and report
  percentages would differ by one. `Rounding` copies Python's rule.
- **Seeds.** `java.util.Random` gives nearly the same first number for seeds 0,
  1, 2, ..., which made tests with consecutive seeds pick the same backup
  question every time. `Rng` mixes the seed first. A given seed still gives the
  same sequence every run.
- **No attention input, by construction.** `Mastery.updateMastery(pMastery,
  isCorrect)` and `Hints.hintFor(questionText, topic, plainHint)` have no
  parameter that could carry attention data. Tests check the parameter lists by
  reflection and check that the code never mentions attention words.
- **The AI only rewords.** `GroqHintWriter` is given the verified hint and asked
  to re-say it. Without `GROQ_API_KEY` it is never called. Any error, an empty
  answer or one over 400 characters gives the built-in hint. Questions never use
  it (a test checks the question code never mentions it).
- **Tests replace parts on purpose.** Where the Python tests use `monkeypatch`,
  the Java code offers one plain switch: `QuestionBank.useGeneratorForTests(...)`
  to break the generator, and a stand-in `HintWriter` for the AI.

## Phase 3: missions

| Java | Python | What it does |
|---|---|---|
| `controller/SessionController` | `routers/sessions.py` | The four student-only endpoints below. |
| `service/Missions` | `routers/sessions.py` | Start a mission, hint, answer. |
| `service/Progress` | `services/game.py` | Missions won, level (1 + different bosses beaten), progress summary. |
| `dto/GameDtos` | `schemas.py` (Game part) | Request and response shapes. |

Endpoints: `GET /students/me/progress`, `POST /sessions` (201),
`GET /attempts/{attempt_id}/hint`, `POST /attempts/{attempt_id}/answer`.

### How an answer is handled (`Missions.answer`)

1. Find the attempt. Someone else's attempt gives 404, not 403, so attempt ids
   cannot be probed. Already answered, or the mission is over: 409.
2. **Academic track.** Correct means the chosen index equals the stored one; a
   time-out (`selected_index: null`) is wrong. Mastery is updated from
   right/wrong only. Points come from correctness, the streak and the time.
3. **Attention track.** The four numbers are copied onto the attempt with a
   score and label, or every column stays NULL when the camera was not used.
   Nothing in the academic track reads them.
4. Update the mission: 7 correct wins (plus the victory bonus and a possible
   new level), 7 wrong loses. If it is still going, the next question is
   generated and stored, and sent without its answer.

All of this is one database transaction, so a failure part-way leaves nothing
half-saved.

### Decisions worth explaining

- **The user is reloaded inside the transaction.** The sign-in check loads the
  user at the start of the request; the answer code loads the current row again
  before changing points and level, so it never writes stale values.
- **The hint is not one transaction.** Marking "hint used" is saved first, then
  the hint is fetched. The AI service can take up to 6 seconds, and the single
  database connection is not held while waiting.
- **Fractional numbers are rejected.** `selected_index: 1.5` gives 422, as in
  Python; by default the JSON library would have quietly cut it to 1.
- **No /openapi.json.** FastAPI publishes a description of the API there; the
  frontend never uses it. The Python test that searched it for image fields is
  replaced by `ApiShapeTest`, which inspects every real endpoint, parameter,
  content type and request/response field (nested ones included) directly.

## Phase 4: guardians, reports, demo data

| Java | Python | What it does |
|---|---|---|
| `controller/GuardianController`, `service/Guardians` | `routers/guardians.py` | `POST /guardians/links` (201), `GET /guardians/students`. Parents and teachers only. |
| `controller/ReportController` | `routers/reports.py` | `GET /students/{student_id}/report?tz_offset_minutes=…` |
| `security/Access` | `deps.student_for_viewer` | The student may see their own report; a guardian only a linked student's. Everyone else: 403, also for ids that do not exist. |
| `service/ReportBuilder` | `services/report_builder.py` | Every report number, counted from stored rows. |
| `service/DemoData`, `service/SeedCommand` | `seed.py` | The demo accounts with three replayed missions; the `--demo` and `--reset` commands. |
| `dto/ReportDtos` | `schemas.py` (Guardians, Reports) | Response shapes. |

### Running the seed command

From `backend/` (needs `JWT_SECRET_KEY`, like the server):

```bash
mvnw spring-boot:run -Dspring-boot.run.arguments=--demo
```

`--demo` creates the three demo accounts with history and is safe to run
again. `--reset` deletes all data and recreates empty tables: do not run it on
a database you want to keep. With either flag the program does its job and
exits without starting the web server. Demo login also needs `DEMO_MODE=true`.

### Decisions worth explaining

- **Links are the only way in.** A guardian finds a student only by the link
  code the student's own account shows. A wrong code, a code for a demo child
  (or, for a demo guardian, a real child's code) all give the same 404, so
  nothing is revealed.
- **Attention stays in its own section.** Accuracy, mastery, trends, badges and
  observations are computed from answers only. Attention averages skip
  unmeasured questions (they are never counted as 0), and a day without camera
  data shows `null`, not 0.
- **Rules copied exactly.** Python's rounding, its tie-breaking in "weakest" and
  "strongest" topic (the first one listed wins), the 14-day window, the
  thresholds for observations (10 answers, 5 per topic, 5 per week) and the
  badge rules are all the same.
- **Dates and times.** Days follow the viewer's clock (`tz_offset_minutes`,
  -840 to 840). Times are sent as Python writes them, e.g.
  `2026-11-15T13:25:29.281000`. SQLite through Java keeps times to the
  millisecond, so the last three digits are 0; Python kept microseconds.
- **The demo data is sample data, and different.** It replays the same three
  missions with the same answers as Python, so counts, wins and the level are
  the same (30 answers, 2 wins, level 3). The questions and the camera numbers
  come from Java's random numbers, so they differ from Python's demo.
- **`--reset`** uses JPA's schema manager to drop and recreate the tables,
  as `drop_all` and `create_all` did in Python.

## Phase 5: proof with the unchanged frontend (2026-11-17)

Run on this machine with the Java backend on port 8001 and the frontend on 5173.

| Check | Result |
|---|---|
| Frontend `npm run test` | 225 passed |
| Frontend `typecheck`, `build` | pass |
| Frontend `lint` | 0 errors, the 2 old warnings |
| Walkthrough in a browser, by an assistant | sign-up with a 422 shown as "password: String should have at least 8 characters"; a full Goblin mission won (one hint, one wrong answer, +1320 points exactly as the rules give, level 2); profile; parent sign-up, wrong code refused, link with the code in lower case, full parent dashboard; demo teacher login with the demo child's dashboard. No browser errors other than the two caused on purpose. |
| `npm run check:camera` | 32 of 32 passed in 4 of 5 runs. In one run the first scenario timed out waiting 30 s for the camera to start in headless Chrome (before any answer was sent); not seen again in three further runs; cause unknown. |
| Same 81 requests to the Python and Java servers | Same status code for all 81. Same JSON fields, types and values everywhere except the three differences below. |

### Differences between the two backends that remain (deliberately)

1. **422 entries have no `input` or `ctx`.** Pydantic adds the rejected value
   and limits (e.g. `"ctx": {"min_length": 8}`) to each error. The Java
   errors have `type`, `loc` and `msg`; the frontend reads only `loc` and `msg`.
2. **Bad email wording.** Pydantic names the exact reason ("An email address
   must have an @-sign."); Java says "The email address is not valid." Both
   start "value is not a valid email address:".
3. **CORS pre-check header list.** Python's answer lists every allowed header;
   Java's lists the ones the browser asked about. Browsers accept both.

### Fixed during phase 5 (found by the side-by-side comparison)

- A number sent where text is expected (`"name": 5`, `"boss_id": 5`) was
  quietly turned into text; it is now 422, as in Python (`config/JsonConfig`).
- A wrong password no longer carries `WWW-Authenticate: Bearer`; only a
  missing or invalid token does, as in Python.
- Error `type` words now match Pydantic: `string_too_short`, `string_too_long`,
  `int_from_float`; a broken JSON body reports where it broke.

## The camera check's row check

`npm run check:camera` ends by checking the database rows its answers created.
In Python that was `backend/tools/verify_attempts.py`. It is now a command of
the Java backend, `service/VerifyAttemptsCommand`, with the same four checks
and the same wording:

```bash
java -jar target/mathverse-backend-1.0.0.jar --verify-attempts "[{\"id\": 12, \"measured\": true}]"
```

It only reads, and it prints one line of JSON (Spring's own log output is
switched off for this command, because the caller reads the output as JSON).

`frontend/scripts/camera-check.mjs` was changed in one place, agreed with the
user on 2026-11-17: it calls this command instead of the Python script, and
looked for the backend in `backend-java/` first, then `backend/`; since phase 6
it simply uses `backend/`. It needs the jar built
first (`mvnw package`) and `java` on the PATH. Nothing in the app itself
(`frontend/src`) was changed.

## Test counts

The Python suite in this repository has **443** tests (the handoff document
says 351; that was an earlier count). All 443 pass on the Python backend.

Every Python test now has a Java counterpart: 443 ported, plus 60 Java-only
checks, 503 in all.

| Python file | Python | Java file(s) | Java |
|---|---|---|---|
| `test_auth.py` | 25 | `AuthTest` | 25 |
| `test_mastery.py` | 207 | `MasteryTest` + `MasteryApiTest` | 203 + 4 |
| `test_mastery_display.py` | 11 | `MasteryDisplayTest` + `GameApiTest` + `MasteryDisplayApiTest` | 6 + 1 + 4 (+1 Java-only) |
| `test_hints.py` | 8 | `HintsTest` + `GameApiTest` | 7 + 1 |
| `test_questions.py` | 61 | `QuestionsTest` | 61 |
| `test_story_questions.py` | 92 | `StoryQuestionsTest` + `GameApiTest` | 86 + 6 |
| `test_sessions.py` | 16 | `SessionsTest` + `ApiShapeTest` | 15 + 1 (+3 Java-only) |
| `test_guardians.py` | 8 | `GuardiansTest` | 8 (+2 Java-only) |
| `test_reports.py` | 2 | `ReportsTest` | 2 |
| `test_dashboard.py` | 13 | `DashboardTest` | 13 |
| (none) | | `ContractTest` | 47 Java-only: JSON names, error bodies, tokens, CORS, emails |
| (none) | | `SeedTest` | 4 Java-only: demo history rules, same data every time, `--demo` and `--reset` |
| (`tools/verify_attempts.py`, untested) | | `VerifyAttemptsTest` | 3 Java-only: real rows pass, each kind of problem is reported |

T1–T12 in Java: T1, T2 and T5 in `SessionsTest` (T5 also `ApiShapeTest`); T6
and T7 in `MasteryTest` and `MasteryApiTest`; T8 and T9 in `AuthTest` (T9 also
`GuardiansTest`); T10 in `GuardiansTest` and `DashboardTest`; T11 in
`DashboardTest`; T12 in `GuardiansTest` and `DashboardTest`. T3 and T4 are
frontend tests only.
