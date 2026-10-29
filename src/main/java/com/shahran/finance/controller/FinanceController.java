package com.shahran.finance.controller;

import com.shahran.finance.model.Models.*;
import com.shahran.finance.service.FinanceService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class FinanceController {
  private final FinanceService service;

  public FinanceController(FinanceService service) {
    this.service = service;
  }

  @GetMapping("/categories")
  public List<Category> categories() {
    return service.categories();
  }

  @GetMapping("/transactions")
  public List<Transaction> transactions(
      @RequestParam String month,
      @RequestParam(defaultValue = "25") int limit,
      @RequestParam(defaultValue = "0") int offset) {
    return service.transactions(month, limit, offset);
  }

  @GetMapping("/transactions/{id}")
  public Transaction transaction(@PathVariable long id) {
    return service.transaction(id);
  }

  @PostMapping("/transactions")
  public ResponseEntity<Transaction> createTransaction(@Valid @RequestBody TransactionInput input) {
    var result = service.createTransaction(input);
    return ResponseEntity.created(URI.create("/api/transactions/" + result.id())).body(result);
  }

  @PutMapping("/transactions/{id}")
  public Transaction updateTransaction(
      @PathVariable long id, @Valid @RequestBody TransactionInput input) {
    return service.updateTransaction(id, input);
  }

  @DeleteMapping("/transactions/{id}")
  public ResponseEntity<Void> deleteTransaction(@PathVariable long id) {
    service.deleteTransaction(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/budgets")
  public List<Budget> budgets(@RequestParam String month) {
    return service.budgets(month);
  }

  @GetMapping("/budgets/{id}")
  public Budget budget(@PathVariable long id) {
    return service.budget(id);
  }

  @PostMapping("/budgets")
  public ResponseEntity<Budget> createBudget(@Valid @RequestBody BudgetInput input) {
    var result = service.createBudget(input);
    return ResponseEntity.created(URI.create("/api/budgets/" + result.id())).body(result);
  }

  @PutMapping("/budgets/{id}")
  public Budget updateBudget(@PathVariable long id, @Valid @RequestBody BudgetInput input) {
    return service.updateBudget(id, input);
  }

  @DeleteMapping("/budgets/{id}")
  public ResponseEntity<Void> deleteBudget(@PathVariable long id) {
    service.deleteBudget(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/summaries")
  public Summary summary(@RequestParam String month) {
    return service.summary(month);
  }
}
