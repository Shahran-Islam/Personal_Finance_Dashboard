# Verification

Checked locally on October 29, 2026 using OpenJDK 22.0.2 with Java 21 compilation target, Maven 3.9.11, and Spring Boot 3.5.16.

- `mvn verify`: 14 integration tests passed, with no failures or skipped tests.
- Authentication checks cover BCrypt password hashing, email normalization, duplicate registration, real login sessions, logout, and CSRF enforcement.
- Isolation checks cover transaction/budget reads, edits, deletes, lists, summary totals, and joins across two accounts. Anonymous legacy rows remain inaccessible.
- All 21 authenticated requests in the Postman collection passed when replayed over HTTP with a Python client and a cookie jar, including signup, CSRF refresh, login, finance CRUD, and logout.
- Browser checks covered registration, login, sample data, transaction creation/edit/deletion, search, pagination, budget creation, reports, and CSV download.
- Confirmed the sample account and financial records persisted after restarting the packaged app; login was required again after restart.
- Checked desktop and phone layouts. Corrected the mobile table container so the table scrolls within its panel.
- Checked JavaScript syntax and the staged Git diff for whitespace errors.

The Postman desktop application, PostgreSQL, and Docker runtime were not exercised locally. H2 integration tests and browser checks do not substitute for checking a future production database or hosting environment. GitHub Actions runs the integration suite on Java 21 when this source is published.
