# Code walkthrough

Start with `SecurityConfig`. Public routes serve static assets, registration, CSRF tokens, and a health check. Spring Security handles form login, session fixation protection, and logout. Passwords are encoded with BCrypt. Requests that change state require a CSRF token, including login and registration.

`AuthController` validates signup input, normalizes email addresses, stores a password hash, and returns only public account fields. The browser then signs in using a form-encoded request and retrieves a fresh CSRF token. The session ID stays in an HttpOnly cookie; the frontend does not store credentials or tokens in localStorage.

Follow `POST /api/transactions` through `FinanceController`, `FinanceService`, and `FinanceRepository`. The service obtains the authenticated user's ID through `CurrentUser`; request bodies never choose their owner. Every transaction/budget lookup, update, delete, and aggregation includes the current user ID. An unowned resource returns 404.

`V1__create_tables.sql` defines categories, transactions, and budgets. `V2__user_accounts.sql` adds users and account ownership, preserving existing anonymous rows without exposing them. The unique budget constraint includes user, category, and month.

The summary uses SQL aggregates and BigDecimal values. Its budget join includes both user and category so another account's expenses cannot leak into a budget. A repeatable-read transaction keeps the summary's queries consistent. Negative remaining funds indicate overspending.

The frontend consists of `index.html`, `app.css`, and `app.js`. The API helper attaches a CSRF header to writes, retains same-origin cookies, and handles expired sessions. Rendering escapes text before adding it to HTML. User-entered descriptions in CSV exports are quoted and guarded against spreadsheet formula injection. The overview, tables, budgets, and reports share the same selected month. Request generation IDs discard stale responses after a month change or logout.

`FinanceApiTest` covers the financial API. `AccountSecurityTest` covers password hashing, real login sessions, CSRF enforcement, account isolation, legacy data, and demo insertion. MockMvc exercises the real controller/service/repository path using an isolated H2 database.

Next useful extensions: PostgreSQL integration tests with Testcontainers, server-side category reports for large datasets, email verification and password reset, and shared session storage for multiple instances.
