# Code walkthrough

Start with `FinanceController`. Follow `POST /api/transactions` to `FinanceService.createTransaction`, then `FinanceRepository.insertTransaction`. The controller validates the body, the service checks the category and date range, and the repository inserts the row and returns its generated ID. The service reads the created row back. The controller returns it with `201 Created` and a Location header.

Next read `V1__create_tables.sql`. Foreign keys keep category references valid; decimal columns avoid floating-point money errors; the budget uniqueness constraint enforces one budget per category per month even when requests race.

Then read `summary`. Income and expenses are aggregated in SQL rather than loading every transaction into memory. The budget query uses a left join so budgets with no spending still appear. The expense and date conditions belong in the join; moving them into the WHERE clause would remove those empty budgets. A repeatable-read transaction keeps the summary's queries on a consistent snapshot.

Finally read `FinanceApiTest`. `MockMvc` sends requests through Spring's real controller/service/repository stack. Each test rolls back its database writes. The summary test includes dates just outside October to catch month-filtering errors and verifies negative remaining funds for an overspent budget.

## Decisions to understand

- JDBC keeps the database work visible and avoids an ORM layer for three small tables.
- `BigDecimal` and `NUMERIC(12,2)` preserve cents. Negative amounts are rejected; transaction type determines direction.
- All IDs and filters are SQL parameters rather than concatenated input.
- Request records separate input validation from database mapping.
- H2 makes the first run simple. PostgreSQL has its own runtime profile; H2 compatibility mode is not a substitute for PostgreSQL testing.
- Offset pagination is easy to use, but newly inserted rows can move page boundaries between requests. Cursor pagination would be a useful extension.
- There is no category management or authentication yet. Keep those as explicit next features rather than implying they already exist.

## Suggested personal changes

1. Add a category filter to transaction listing and test it together with the month filter.
2. Add an endpoint that groups monthly expenses by category.
3. Add PostgreSQL integration tests using Testcontainers.
4. Build your own dashboard against this API after adding authentication if you intend to deploy writes publicly.

Describe features you have actually run and understand. The Postman collection is ready to use, but only say you tested it in Postman after running it yourself.
