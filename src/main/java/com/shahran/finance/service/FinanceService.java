package com.shahran.finance.service;

import com.shahran.finance.model.Models.*;
import com.shahran.finance.repository.FinanceRepository;
import java.time.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class FinanceService {
  private final FinanceRepository repository;

  public FinanceService(FinanceRepository repository) {
    this.repository = repository;
  }

  private ResponseStatusException missing() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found");
  }

  private void checkCategory(long id) {
    if (!repository.categoryExists(id))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category does not exist");
  }

  public LocalDate month(String value) {
    if (value == null || !value.matches("[0-9]{4}-(0[1-9]|1[0-2])"))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Month must use YYYY-MM format");
    var parsed = YearMonth.parse(value);
    if (parsed.getYear() < 1 || parsed.getYear() > 9998)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Year must be between 0001 and 9998");
    return parsed.atDay(1);
  }

  public List<Category> categories() {
    return repository.categories();
  }

  public Transaction transaction(long id) {
    return repository.transaction(id).orElseThrow(this::missing);
  }

  public List<Transaction> transactions(String month, int limit, int offset) {
    if (limit < 1 || limit > 100 || offset < 0)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Limit must be 1-100 and offset must be nonnegative");
    LocalDate start = month(month);
    return repository.transactions(start, start.plusMonths(1), limit, offset);
  }

  private void checkTransaction(TransactionInput input) {
    checkCategory(input.categoryId());
    if (input.date().getYear() < 1 || input.date().getYear() > 9998)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Year must be between 0001 and 9998");
  }

  public Transaction createTransaction(TransactionInput input) {
    checkTransaction(input);
    return transaction(repository.insertTransaction(input));
  }

  public Transaction updateTransaction(long id, TransactionInput input) {
    checkTransaction(input);
    if (repository.updateTransaction(id, input) == 0) throw missing();
    return transaction(id);
  }

  public void deleteTransaction(long id) {
    if (repository.deleteTransaction(id) == 0) throw missing();
  }

  public Budget budget(long id) {
    return repository.budget(id).orElseThrow(this::missing);
  }

  public List<Budget> budgets(String month) {
    return repository.budgets(month(month));
  }

  public Budget createBudget(BudgetInput input) {
    checkCategory(input.categoryId());
    return budget(repository.insertBudget(input, month(input.month())));
  }

  public Budget updateBudget(long id, BudgetInput input) {
    checkCategory(input.categoryId());
    if (repository.updateBudget(id, input, month(input.month())) == 0) throw missing();
    return budget(id);
  }

  public void deleteBudget(long id) {
    if (repository.deleteBudget(id) == 0) throw missing();
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ, readOnly = true)
  public Summary summary(String month) {
    LocalDate start = month(month), end = start.plusMonths(1);
    var income = repository.total(Type.INCOME, start, end);
    var expenses = repository.total(Type.EXPENSE, start, end);
    return new Summary(
        month, income, expenses, income.subtract(expenses), repository.progress(start, end));
  }
}
