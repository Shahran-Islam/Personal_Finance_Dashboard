package com.shahran.finance.model;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class Models {
  private Models() {}

  public enum Type {
    INCOME,
    EXPENSE
  }

  public record Category(long id, String name) {}

  public record TransactionInput(
      @NotBlank @Size(max = 200) String description,
      @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
      @NotNull Type type,
      @NotNull @Positive Long categoryId,
      @NotNull LocalDate date) {}

  public record Transaction(
      long id, String description, BigDecimal amount, Type type, long categoryId, LocalDate date) {}

  public record BudgetInput(
      @NotNull @Positive Long categoryId,
      @NotBlank @Pattern(regexp = "[0-9]{4}-(0[1-9]|1[0-2])") String month,
      @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount) {}

  public record Budget(long id, long categoryId, String month, BigDecimal amount) {}

  public record BudgetProgress(
      long categoryId,
      String category,
      BigDecimal budget,
      BigDecimal spent,
      BigDecimal remaining) {}

  public record Summary(
      String month,
      BigDecimal income,
      BigDecimal expenses,
      BigDecimal balance,
      List<BudgetProgress> budgets) {}
}
