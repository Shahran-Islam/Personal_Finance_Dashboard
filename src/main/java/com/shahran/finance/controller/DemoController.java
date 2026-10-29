package com.shahran.finance.controller;

import com.shahran.finance.model.Models.*;
import com.shahran.finance.security.CurrentUser;
import com.shahran.finance.service.FinanceService;
import java.math.BigDecimal;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class DemoController {
  private final FinanceService finance;
  private final JdbcTemplate jdbc;
  private final CurrentUser currentUser;

  public DemoController(FinanceService finance, JdbcTemplate jdbc, CurrentUser currentUser) {
    this.finance = finance;
    this.jdbc = jdbc;
    this.currentUser = currentUser;
  }

  @PostMapping("/api/demo")
  @Transactional
  public ResponseEntity<Void> demo(@RequestParam String month) {
    var start = finance.month(month);
    // Serialize sample insertion for an account so double clicks cannot duplicate a month.
    jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, currentUser.id());
    if (!finance.transactions(month, 1, 0).isEmpty() || !finance.budgets(month).isEmpty())
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Sample data needs an empty month");
    Object[][] rows = {
      {"Monthly salary", "4250.00", Type.INCOME, 1L, 1},
      {"Apartment rent", "1450.00", Type.EXPENSE, 2L, 2},
      {"Weekly groceries", "86.42", Type.EXPENSE, 3L, 3},
      {"Metro pass", "132.00", Type.EXPENSE, 4L, 4},
      {"Coffee with a friend", "12.50", Type.EXPENSE, 5L, 6},
      {"Freelance project", "650.00", Type.INCOME, 7L, 10},
      {"Farmers market", "54.80", Type.EXPENSE, 3L, 11},
      {"Dinner out", "64.00", Type.EXPENSE, 5L, 14},
      {"Movie night", "28.00", Type.EXPENSE, 6L, 17},
      {"Grocery restock", "97.25", Type.EXPENSE, 3L, 20},
      {"Bookshop", "32.00", Type.EXPENSE, 6L, 23},
      {"Lunch downtown", "18.75", Type.EXPENSE, 5L, 25}
    };
    for (var row : rows)
      finance.createTransaction(
          new TransactionInput(
              (String) row[0],
              new BigDecimal((String) row[1]),
              (Type) row[2],
              (Long) row[3],
              start.withDayOfMonth((Integer) row[4])));
    finance.createBudget(new BudgetInput(3L, month, new BigDecimal("400")));
    finance.createBudget(new BudgetInput(5L, month, new BigDecimal("200")));
    finance.createBudget(new BudgetInput(6L, month, new BigDecimal("150")));
    return ResponseEntity.status(201).build();
  }
}
