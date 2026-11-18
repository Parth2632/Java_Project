# MathVerse

Gamified maths practice for school students, with adaptive difficulty, optional
in-browser attention estimates, and parent/teacher dashboards. A course project
(UCS503P, TIET). It handles children's data, so the rules below matter.

This repository is the Java version: the backend is Java (Spring Boot) and the
React frontend is the one from the original Python project. The Java backend
was ported from the Python one with the same API; the Python code is in the git
history only.

Read first: `docs/ARCHITECTURE.md` (how it works), `docs/PRIVACY.md` (what is
collected), `docs/JAVA_BACKEND.md` (how the Java backend works and how it was
ported), `docs/AUDIT_AND_PLAN.md` (why it is built this way).

## Commands

Backend, from `backend/` (Java 21 or newer; `./mvnw` on macOS/Linux):

```bash
mvnw test
mvnw spring-boot:run                                      # API on port 8001
mvnw package                                              # builds target/mathverse-backend-1.0.0.jar (needed by check:camera)
mvnw spring-boot:run -Dspring-boot.run.arguments=--demo   # create the three demo accounts (needs DEMO_MODE=true to log in)
mvnw spring-boot:run -Dspring-boot.run.arguments=--reset  # wipes ALL data; ask before running
```

Frontend, from `frontend/`:

```bash
npm run dev
npm run test
npm run typecheck
npm run lint
npm run build
npm run check:camera   # needs both servers running and the backend jar built
```

`backend/.env` must contain `JWT_SECRET_KEY` (at least 32 characters). Tests
set their own secret and use an in-memory database, so they need no `.env`.
On Windows, a running server keeps the jar open: stop it before `mvnw package`.

Before saying work is done, run `mvnw test` and, for frontend changes,
`npm run test`, `npm run typecheck` and `npm run lint`.

## Layout

Backend code is in `backend/src/main/java/com/mathverse/`, tests in
`backend/src/test/java/com/mathverse/`.

- `controller/` — `Auth`, `Session` (gameplay), `Guardian`, `Report`, `Health`
- `service/` — `Questions` and `StoryQuestions` (generator), `QuestionBank`,
  `Mastery` (BKT), `MasteryDisplay`, `Hints`, `Attention`, `Game` (scoring
  rules), `Progress`, `Missions`, `ReportBuilder`, `Curriculum`, `DemoData`,
  `SeedCommand`, `VerifyAttemptsCommand`
- `security/` — `AuthInterceptor`, `@SignedIn`, `@CurrentUser` (who is calling)
  and `Access.studentForViewer` (what they may see); `Passwords`, `Tokens`
- `config/` — `Settings` (environment and `.env`), `ErrorHandler` (FastAPI-style
  error bodies), `JsonConfig`, `WebConfig` (CORS)
- `model/` — the five tables and their repositories; `dto/` — request and
  response shapes
- `frontend/src/lib/api.ts` — the only place that calls the backend
- `frontend/src/lib/attention/` — camera tracking; `detector.ts` is the only
  file that touches MediaPipe
- `frontend/src/components/dashboard/` — the parent/teacher report and charts

## Rules that must not be broken

1. **The server owns the truth.** It generates questions, keeps the answer,
   judges it, and awards points, levels and unlocks. Never trust a score,
   points value or user id sent by the client. Identity comes only from the
   bearer token (`@CurrentUser`, filled by `AuthInterceptor`).
2. **The camera is always optional and stays in the browser.** No image or
   video is stored, drawn to a canvas, or uploaded. Only the four-number
   summary is sent. Do not add any endpoint or field that could carry image
   data; unknown request fields are rejected
   (`fail-on-unknown-properties=true`), and `ApiShapeTest` checks every endpoint.
3. **Attention never affects anything academic.** Mastery, difficulty, points,
   unlocks, hints and report observations must not read attention data.
   `Mastery.updateMastery(pMastery, isCorrect)` and
   `Hints.hintFor(questionText, topic, plainHint)` have no attention parameter;
   keep it that way.
4. **Missing attention data is NULL, meaning "not measured".** Never convert it
   to 0, a default, or a label, and leave it out of averages. Use `Double`,
   `Integer`, `Boolean`, never primitives, for anything that can be missing.
5. **Nothing is fabricated.** Every dashboard number comes from stored rows. No
   sample values, hardcoded badges, or invented summaries. Empty data shows an
   empty state.
6. **No medical claims.** Never describe the attention estimate as diagnosing
   ADHD or any condition, or as a measure of ability. It is "an estimate, not a
   medical assessment".
7. **Demo login** stays off by default (`DEMO_MODE=false`), returns only seeded
   `is_demo` accounts, never creates users, and demo and real accounts are never
   linked.
8. **Questions are deterministic.** The generator computes each answer. A
   language model may only reword an existing verified hint; it never writes
   questions or maths, and the game must work with no `GROQ_API_KEY`. Story
   questions (`StoryQuestions`) follow the same rule. Serve questions through
   `QuestionBank.questionOrBackup`, which falls back to a fixed bank only if
   the generator throws.
9. **Guardians see a student only through a link** made with that student's link
   code. Use `Access.studentForViewer` for any endpoint that returns a
   student's data.
10. **The API contract is fixed.** The unchanged frontend depends on the exact
    paths, snake_case JSON names, status codes and error bodies
    (`{"detail": ...}`; 422 as a list of `{type, loc, msg}`). `ContractTest`
    checks them.

Tests enforce most of these (T1–T12, mapped in `docs/ARCHITECTURE.md`). If a
change makes one of those tests fail, the change is wrong, not the test.

## Conventions

- Keep it simple and explainable; this is a student project, not enterprise
  software, and the Java code will be examined in a viva. Prefer fixing over
  rewriting, and avoid new dependencies.
- Backend: controllers stay thin, logic lives in `service/`. Plain classes and
  records; no Lombok; the full Spring Security filter chain is deliberately not
  used (only its bcrypt class).
- Copy Python's rounding (`Rounding`: halves to even) wherever a value is
  rounded, so numbers match what the original backend produced.
- Frontend: all requests go through `api()` in `lib/api.ts`; shared state lives
  in `AuthContext` and `GameContext`; only the login token is kept in
  `localStorage`.
- Mastery uses BKT with the learning step applied only after correct answers
  (a deliberate change from textbook BKT, documented in `Mastery.java`).
  Do not "fix" it back.
- Write tests with the feature. New question types, stories included, need a
  case in `Solver.solve` (in the backend tests).
- Charts are single-series inline SVG in `TrendCharts.tsx`; no chart library.

## Working rules for this repository

- `origin` (github.com/Parth2632/Java_Project) is a shared repository. **Never
  push without explicit permission.** Local commits are fine.
- Do larger pieces of work on a branch, stop when a piece is finished, and
  report: files changed, what was implemented, tests run and their results,
  remaining issues, and how to test by hand.
- Never drop an agreed requirement silently. If something conflicts, say so and
  propose a resolution.
- Secrets live in `backend/.env` (git-ignored). Never print or commit them.
  `*.db` files and `backend/target/` are git-ignored and must stay that way.

## Current state (updated 2026-11-18)

The Java port was pushed to `origin/main` on 2026-11-18 with the user's
permission. Later pushes still need it each time.

- The Java backend was built in phases 1–6 (branches `java-p1-accounts` …
  `java-p6-switch`, all contained in `main`). Every one of the original 443
  Python tests has a Java counterpart; with the Java-only checks, 503 backend
  tests pass.
- With the unchanged frontend: 225 frontend tests pass; typecheck and build
  pass; lint has 0 errors and 2 old warnings. A full browser walkthrough
  worked, and `npm run check:camera` passed 32/32 against the Java backend.
- The same 81 requests were sent to the Python and Java backends: all status
  codes match. Remaining deliberate differences are listed in
  `docs/JAVA_BACKEND.md` (no `input`/`ctx` in 422 entries, bad-email wording,
  CORS pre-check header list).
- `frontend/scripts/camera-check.mjs` now checks stored rows with the backend's
  `--verify-attempts` command instead of a Python script (agreed with the user
  on 2026-11-17). Two comments in `frontend/src/lib` were updated to name Java
  files; no frontend code changed.
- The effects code is under `frontend/src/lib/effects/` and
  `frontend/src/components/effects/`. It is frontend only, takes game events
  from server responses, and has no access to camera or attention data.
  Review all sounds at `http://localhost:5173/?soundboard=1`.
- `connect-src` in `frontend/vite.config.ts` refuses MediaPipe's usage log to
  Google; do not remove it.

## Remaining issues

Needs the user (an assistant cannot do these):

1. **Real webcam testing is partly done** (2026-11-16, with the Python backend:
   one laptop webcam, Chrome, one person; `docs/WEBCAM_TEST_PLAN.md` section 8).
   On 2026-11-18 the user checked it again with the Java backend on the same
   laptop and reported it working (no numbers recorded; section 9). The user
   decided the section 8 (Python-backend) numbers serve as this version's
   results, labelled as such; their facing percentages used the old limit 25. Still
   untested on real hardware: a fresh
   permission prompt, permission denied, no camera, camera in use elsewhere,
   lighting, other cameras and positions, Edge, Firefox, Safari, phones, slow
   devices. Say "checked on one device", never "verified".
2. **Head-angle limits are based on one run** (`YAW_LIMIT_DEG` 30,
   `PITCH_LIMIT_DEG` 20 in `lib/attention/summary.ts`). The user approved
   raising the turn limit from 25 to 30 on 2026-11-16. The real run has not been
   repeated with 30. Looking down at the keyboard or lap is still counted as
   facing the screen. Change nothing further without the user's approval.
3. **Pushing to `origin`** needs the user's explicit permission each time
   (given once, on 2026-11-18, for the Java port).
4. **Deployment is postponed by the user** (2026-11-16). The approach chosen
   then (for the Python version): one Docker container on one free host, with
   the backend also serving the built frontend from the same address; SQLite
   kept, demo accounts recreated at startup; fonts self-hosted. For Java this
   means a JDK 21 image running the jar. Remember: build the frontend with an
   API address that means "same address" (and teach the `connect-src` rule in
   `vite.config.ts` about it), keep `backend/.env` out of the image, and set a
   fresh `JWT_SECRET_KEY` on the host. Deploy as a demo, not for real children.
5. **Groq API key:** this repository has no key configured; hints use the
   built-in text. A real Groq call from the Java backend has not been tested
   (only its fallbacks). Never print a key.
6. **`frontend/public/laugh.mp3`** (6.6 s) is unused and nobody has listened to
   it. Decide: use it or delete it.
7. **CI was added on 2026-11-18** (`.github/workflows/ci.yml`, approved by the
   user): `mvnw test` on Java 21, and the frontend's test, typecheck, lint and
   build on Node 20. Its first run on GitHub has not been checked yet.
   `check:camera` is not in CI (it needs both servers and a browser).
8. **`backend/.env` and `backend/mathverse.db`** on this machine were created
   during phase 5 (a generated secret, test and demo accounts). `DEMO_MODE`
   was turned off on 2026-11-18; set it to `true` to show the demo buttons.
   `.claude/launch.json` (committed) starts the built jar and the frontend
   dev server; it uses `cmd`, so it works on Windows only.

Known and open:

9. In F5, a special move plays *instead of* the ordinary move. The plan said
   "adds"; the user was told and has not objected.
10. The camera test readout overlaps the combo counter (developer-only).
11. The defeat screen and in-mission signature moves are covered by tests only,
    not seen in a browser.
12. `check:camera` timed out once in five runs against the Java backend (the
    first scenario waited 30 s for the camera to start in headless Chrome);
    not seen again. One unexplained half-second stall was also seen once in
    the original F5 performance check.
13. The Java demo history replays the same answers as Python's but with
    different random questions and camera numbers; counts, wins and level
    match.

## Known gaps

- Real-device camera checklist: `docs/WEBCAM_TEST_PLAN.md` (partly run).
  `?attentionDebug=1` shows a text-only readout of the live numbers for it.
- "On screen" means head direction, not eye gaze; head-angle thresholds are
  untuned.
- BKT parameters are textbook defaults, so mastery can swing to 1% or 99%
  quickly.
- Camera consent is per device; real use with children needs verified
  parent/guardian consent and controls (see `docs/PRIVACY.md`).
- Fonts load from Google Fonts. Character names and artwork belong to Marvel.
