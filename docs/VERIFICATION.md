# Verification

Verified locally on October 29, 2026 using OpenJDK 22.0.2 with Java 21 compilation target, Maven 3.9.11, and Spring Boot 3.5.16.

- `mvn verify`: BUILD SUCCESS; 8 integration tests; 0 failures, 0 errors, 0 skipped.
- Packaged JAR: started successfully with a temporary H2 database.
- HTTP checks: all 15 requests from the Postman collection returned the expected statuses when replayed by a Python HTTP client, including CRUD, duplicate-budget rejection, validation, and missing-record handling.
- Summary checked over HTTP: expense total 45.00; budget 400.00; remaining 355.00.

The Postman desktop application itself was not used. PostgreSQL/Docker and GitHub Actions were not executed locally. The GitHub workflow targets Java 21 and runs the Maven test suite once published.
