## Planned: a Java backend for a separate project (not started)

The user needs the same application with a Java backend for a different
project (noted 2026-11-16). Nothing has been built. When it starts:

- **Scope:** port only the backend. The Python backend is about 2,000 lines:
  11 endpoints in 4 routers (`auth`, `sessions`, `guardians`, `reports`) and 8
  services. The React frontend is reused unchanged; it only needs
  `VITE_API_URL` pointed at the Java server.
- **The contract is the Python API.** Same paths, request and response JSON,
  status codes and error shapes, so the frontend and
  `npm run check:camera` work against either backend. Unknown request fields
  must still be rejected (the Python schemas use `extra="forbid"`).
- **Full handoff document: `docs/JAVA_BACKEND_HANDOFF.docx`.** It lists the
  endpoints, the rules, the logic to port, phases, and questions to ask first.
- **Where it goes (user's plan, 2026-11-16):** a teammate creates a new
  repository; all project files are copied there and the Python `backend/` is
  replaced by the Java one. In *this* repository the Python backend stays as
  it is; do not replace or modify it here.
- **Suggested stack, to confirm with the user first:** Spring Boot, Spring
  Security with JWT, Spring Data JPA, bcrypt, JUnit 5. Ask which database and
  Java version the other project requires before starting.
- **Every rule under "Rules that must not be broken" applies unchanged**,
  above all: the server owns the truth, attention never affects anything
  academic, missing attention stays NULL, demo login never creates accounts.
- **Port the logic exactly.** BKT parameters and the "learn only after a
  correct answer" variation (`services/mastery.py`), the 5-95 display cap
  (`mastery_display.py`), scoring and unlock rules (`game.py`), and the
  question generator's tiers (`questions.py`). Questions use randomness, so
  compare behaviour and ranges, not exact questions.
- **Port the tests with the code,** T1-T12 first (mapped in
  `docs/ARCHITECTURE.md`). The 351 Python tests are the specification.
- **Hints:** the Groq call is optional and must fall back to the built-in hint.

