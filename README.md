# Java Full Stack — Task Board with Persistent Storage

I built this full-stack reference to demonstrate how I design a small web application end to end without hiding the architecture behind external services. It has a Java 17 backend, HTTP endpoints, a responsive JavaScript frontend, data persistence and an automated integration test that exercises the running server.

## Architecture

`Browser (HTML/JavaScript) → Java HttpServer → synchronized task service → atomic file persistence`

## Run locally

Requires **JDK 17 or later**.

```bash
mkdir -p out/classes
javac -encoding UTF-8 -d out/classes src/main/java/portfolio/TaskServer.java
java -cp out/classes portfolio.TaskServer --port 8080 --data out/tasks.tsv
```

Open `http://127.0.0.1:8080`. Add tasks, complete/reopen them, and restart the server to confirm persistence.

```bash
python -m pip install pytest
python -m pytest -q
```

## API

| Method | Route | Function |
|---|---|---|
| GET | `/api/health` | Health check |
| GET | `/api/tasks` | Task list |
| POST | `/api/tasks` | Form body `title=<text>` |
| POST | `/api/tasks/{id}/toggle` | Toggle completion |

## Security boundary

This is a localhost-only demo server with no authentication or TLS. It must not be exposed publicly until authentication, authorization, rate limits, request protections and a production database are implemented. The task store uses an atomic temporary-file replacement and encodes titles safely; the UI uses `textContent` instead of unsafe HTML insertion.
