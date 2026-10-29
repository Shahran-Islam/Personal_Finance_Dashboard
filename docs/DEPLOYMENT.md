# Running and hosting Still

The app serves its own HTML, CSS, JavaScript, and API from one Spring Boot process. Accounts use your database, passwords use BCrypt, and sessions use an HttpOnly cookie. There are no third-party runtime APIs, CDNs, analytics, or external login providers.

## Local use

On Windows, double-click `run-app.cmd`. It detects a JDK installed under your `.jdks` directory if Java is not already configured. Otherwise install JDK 21+ and set `JAVA_HOME`. The Maven wrapper downloads Maven and dependencies on the first run. Open `http://localhost:8080`, create an account, and start adding transactions. Keep the terminal open while using the app.

On macOS/Linux, run `chmod +x mvnw`, then `./mvnw spring-boot:run` with JDK 21+. Local accounts and financial records persist in `data/finance.mv.db`. Restarting the application signs everyone out; their records remain in the database.

## Docker and PostgreSQL

Copy `.env.example` to `.env`, replace the password, then run:

```sh
docker compose up --build -d
```

Open `http://localhost:8080`. The PostgreSQL database is stored in the `finance-data` Docker volume. Do not use `docker compose down -v` unless you intend to delete that database.

## Public deployment

Use a Java/container host with a persistent PostgreSQL database and an HTTPS endpoint. The repository includes a Dockerfile; configure your host to build it from the repository root. Supply these environment variables through the hosting dashboard, not Git:

| Variable | Value |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `production` |
| `DATABASE_URL` | A JDBC URL such as `jdbc:postgresql://HOST:5432/finance?sslmode=require` |
| `DATABASE_USERNAME` | Your database username |
| `DATABASE_PASSWORD` | Your database password |
| `PORT` | The port supplied by the host, typically 8080 |

The production profile listens on all container interfaces and requires Secure session cookies. Use HTTPS for every public request; direct HTTP login will not persist the secure session. Set the health check path to `/health`. Keep the database private and enable scheduled backups on the host. Start with one application instance because sessions and authentication rate limits are held in memory.

Forwarded headers are ignored by default. Behind a reverse proxy, configure trusted proxy handling for that specific hosting environment before public launch; otherwise authentication rate limits may count all visitors as the proxy's address. Do not broadly trust client-supplied forwarding headers. For multiple instances, use shared sessions and an edge/shared rate limiter.

After deploying, verify registration, login, logout, record persistence after restart, and account isolation using two test accounts. Check that the session cookie is Secure, HttpOnly, and SameSite=Lax. Avoid using real financial data until your deployment checks and backups are complete.

Hosting is not provisioned by this repository. You must connect your hosting account, choose its region and plan, and supply the database environment variables. No paid service is required to run the app locally.

## Existing API databases

Flyway V2 adds users and ownership without deleting existing anonymous transactions or budgets. Older rows have a null owner and remain inaccessible through the API. Never automatically assign them to the first person who registers. If you have legacy data to retain, back up the database and deliberately assign it to a verified user ID through an administrator migration.

## Current limits

This version includes registration, sign-in/out, session expiry, CSRF protection, BCrypt password hashing, account-scoped queries, and a bounded per-address authentication limit. Email verification, forgotten-password recovery, MFA, and bank synchronization are not included. Use a password manager: there is no email password-reset flow in this version.
