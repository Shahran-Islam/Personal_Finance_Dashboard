package com.shahran.finance.repository;

import com.shahran.finance.model.Models.*;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

@Repository
public class FinanceRepository {
  private final JdbcTemplate jdbc;

  public FinanceRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private final RowMapper<Transaction> transactionMapper =
      (rs, row) ->
          new Transaction(
              rs.getLong("id"),
              rs.getString("description"),
              rs.getBigDecimal("amount"),
              Type.valueOf(rs.getString("type")),
              rs.getLong("category_id"),
              rs.getDate("transaction_date").toLocalDate());
  private final RowMapper<Budget> budgetMapper =
      (rs, row) ->
          new Budget(
              rs.getLong("id"),
              rs.getLong("category_id"),
              rs.getDate("budget_month").toLocalDate().toString().substring(0, 7),
              rs.getBigDecimal("amount"));

  public List<Category> categories() {
    return jdbc.query(
        "SELECT id, name FROM categories ORDER BY id",
        (rs, n) -> new Category(rs.getLong(1), rs.getString(2)));
  }

  public boolean categoryExists(long id) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM categories WHERE id=?", Long.class, id) > 0;
  }

  public Optional<Transaction> transaction(long userId, long id) {
    return jdbc
        .query("SELECT * FROM transactions WHERE id=? AND user_id=?", transactionMapper, id, userId)
        .stream()
        .findFirst();
  }

  public List<Transaction> transactions(
      long userId, LocalDate from, LocalDate until, int limit, int offset) {
    return jdbc.query(
        "SELECT * FROM transactions WHERE user_id=? AND transaction_date >= ? AND transaction_date"
            + " < ? ORDER BY transaction_date DESC, id DESC LIMIT ? OFFSET ?",
        transactionMapper,
        userId,
        from,
        until,
        limit,
        offset);
  }

  public long insertTransaction(long userId, TransactionInput input) {
    return new SimpleJdbcInsert(jdbc)
        .withTableName("transactions")
        .usingGeneratedKeyColumns("id")
        .executeAndReturnKey(
            Map.of(
                "user_id",
                userId,
                "description",
                input.description().trim(),
                "amount",
                input.amount(),
                "type",
                input.type().name(),
                "category_id",
                input.categoryId(),
                "transaction_date",
                Date.valueOf(input.date())))
        .longValue();
  }

  public int updateTransaction(long userId, long id, TransactionInput input) {
    return jdbc.update(
        "UPDATE transactions SET description=?, amount=?, type=?, category_id=?, transaction_date=?"
            + " WHERE id=? AND user_id=?",
        input.description().trim(),
        input.amount(),
        input.type().name(),
        input.categoryId(),
        input.date(),
        id,
        userId);
  }

  public int deleteTransaction(long userId, long id) {
    return jdbc.update("DELETE FROM transactions WHERE id=? AND user_id=?", id, userId);
  }

  public Optional<Budget> budget(long userId, long id) {
    return jdbc
        .query("SELECT * FROM budgets WHERE id=? AND user_id=?", budgetMapper, id, userId)
        .stream()
        .findFirst();
  }

  public List<Budget> budgets(long userId, LocalDate month) {
    return jdbc.query(
        "SELECT * FROM budgets WHERE budget_month=? AND user_id=? ORDER BY category_id",
        budgetMapper,
        month,
        userId);
  }

  public long insertBudget(long userId, BudgetInput input, LocalDate month) {
    return new SimpleJdbcInsert(jdbc)
        .withTableName("budgets")
        .usingGeneratedKeyColumns("id")
        .executeAndReturnKey(
            Map.of(
                "user_id",
                userId,
                "category_id",
                input.categoryId(),
                "budget_month",
                Date.valueOf(month),
                "amount",
                input.amount()))
        .longValue();
  }

  public int updateBudget(long userId, long id, BudgetInput input, LocalDate month) {
    return jdbc.update(
        "UPDATE budgets SET category_id=?, budget_month=?, amount=? WHERE id=? AND user_id=?",
        input.categoryId(),
        month,
        input.amount(),
        id,
        userId);
  }

  public int deleteBudget(long userId, long id) {
    return jdbc.update("DELETE FROM budgets WHERE id=? AND user_id=?", id, userId);
  }

  public BigDecimal total(long userId, Type type, LocalDate from, LocalDate until) {
    return jdbc.queryForObject(
        "SELECT COALESCE(SUM(amount),0) FROM transactions WHERE user_id=? AND type=? AND"
            + " transaction_date >= ? AND transaction_date < ?",
        BigDecimal.class,
        userId,
        type.name(),
        from,
        until);
  }

  public List<BudgetProgress> progress(long userId, LocalDate from, LocalDate until) {
    return jdbc.query(
        """
SELECT b.category_id, c.name, b.amount, COALESCE(SUM(t.amount),0) AS spent
FROM budgets b JOIN categories c ON c.id=b.category_id
LEFT JOIN transactions t ON t.user_id=b.user_id AND t.category_id=b.category_id AND t.type='EXPENSE'
    AND t.transaction_date >= ? AND t.transaction_date < ?
WHERE b.budget_month=? AND b.user_id=? GROUP BY b.category_id, c.name, b.amount ORDER BY c.name
""",
        (rs, n) ->
            new BudgetProgress(
                rs.getLong("category_id"),
                rs.getString("name"),
                rs.getBigDecimal("amount"),
                rs.getBigDecimal("spent"),
                rs.getBigDecimal("amount").subtract(rs.getBigDecimal("spent"))),
        from,
        until,
        from,
        userId);
  }
}
