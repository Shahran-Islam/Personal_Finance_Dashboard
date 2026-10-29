package com.shahran.finance;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    properties =
        "spring.datasource.url=jdbc:h2:mem:test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
@org.springframework.security.test.context.support.WithMockUser(username = "test@example.com")
class FinanceApiTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
  @Autowired org.springframework.web.context.WebApplicationContext context;

  @org.junit.jupiter.api.BeforeEach
  void prepareUser() {
    jdbc.update(
        "INSERT INTO users(name,email,password_hash) VALUES(?,?,?)",
        "Test",
        "test@example.com",
        "unused");
    mvc =
        org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
            .apply(
                org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                    .springSecurity())
            .defaultRequest(
                get("/")
                    .with(
                        org.springframework.security.test.web.servlet.request
                            .SecurityMockMvcRequestPostProcessors.csrf()))
            .build();
  }

  String transaction(String amount, String type, String date) {
    return "{\"description\":\"Test\",\"amount\":"
        + amount
        + ",\"type\":\""
        + type
        + "\",\"categoryId\":3,\"date\":\""
        + date
        + "\"}";
  }

  long create(String resource, String body) throws Exception {
    var response =
        mvc.perform(post("/api/" + resource).contentType("application/json").content(body))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andReturn();
    return mapper.readTree(response.getResponse().getContentAsString()).get("id").asLong();
  }

  @Test
  void transactionCrud() throws Exception {
    long id = create("transactions", transaction("12.50", "EXPENSE", "2026-10-02"));
    mvc.perform(get("/api/transactions/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.amount").value(12.50));
    mvc.perform(
            put("/api/transactions/" + id)
                .contentType("application/json")
                .content(transaction("15.25", "EXPENSE", "2026-10-02")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.amount").value(15.25));
    mvc.perform(delete("/api/transactions/" + id)).andExpect(status().isNoContent());
    mvc.perform(get("/api/transactions/" + id)).andExpect(status().isNotFound());
  }

  @Test
  void validationRejectsInvalidMoneyAndCategory() throws Exception {
    for (String value : new String[] {"0", "-1", "1.001", "10000000000"})
      mvc.perform(
              post("/api/transactions")
                  .contentType("application/json")
                  .content(transaction(value, "EXPENSE", "2026-10-02")))
          .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/transactions")
                .contentType("application/json")
                .content(
                    transaction("2", "EXPENSE", "2026-10-02")
                        .replace("\"categoryId\":3", "\"categoryId\":999")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void summaryHasExactTotalsAndMonthBoundaries() throws Exception {
    create("transactions", transaction("100.10", "INCOME", "2026-10-01"));
    create("transactions", transaction("20.20", "EXPENSE", "2026-10-31"));
    create("transactions", transaction("50", "EXPENSE", "2026-11-01"));
    create("transactions", transaction("50", "EXPENSE", "2026-09-30"));
    create("budgets", "{\"categoryId\":3,\"month\":\"2026-10\",\"amount\":15}");
    mvc.perform(get("/api/summaries?month=2026-10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.income").value(100.10))
        .andExpect(jsonPath("$.expenses").value(20.20))
        .andExpect(jsonPath("$.balance").value(79.90))
        .andExpect(jsonPath("$.budgets[0].remaining").value(-5.20));
  }

  @Test
  void budgetCrudAndDuplicateConflict() throws Exception {
    String body = "{\"categoryId\":3,\"month\":\"2026-10\",\"amount\":200}";
    long id = create("budgets", body);
    mvc.perform(post("/api/budgets").contentType("application/json").content(body))
        .andExpect(status().isConflict());
    mvc.perform(
            put("/api/budgets/" + id)
                .contentType("application/json")
                .content(body.replace("200", "300")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.amount").value(300));
    mvc.perform(get("/api/budgets?month=2026-10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
    mvc.perform(delete("/api/budgets/" + id)).andExpect(status().isNoContent());
    mvc.perform(get("/api/budgets/" + id)).andExpect(status().isNotFound());
  }

  @Test
  void emptySummaryAndBadQueries() throws Exception {
    mvc.perform(get("/api/summaries?month=2026-10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.balance").value(0));
    for (String query :
        new String[] {
          "month=2026-13", "month=wrong", "month=2026-10&limit=101", "month=2026-10&offset=-1"
        }) mvc.perform(get("/api/transactions?" + query)).andExpect(status().isBadRequest());
    mvc.perform(get("/api/transactions")).andExpect(status().isBadRequest());
  }

  @Test
  void paginationIsStable() throws Exception {
    create("transactions", transaction("1", "EXPENSE", "2026-10-01"));
    long latest = create("transactions", transaction("2", "EXPENSE", "2026-10-01"));
    mvc.perform(get("/api/transactions?month=2026-10&limit=1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(latest));
    mvc.perform(get("/api/transactions?month=2026-10&limit=1&offset=1"))
        .andExpect(jsonPath("$[0].amount").value(1));
  }

  @Test
  void malformedJsonAndUnknownTypeAreBadRequests() throws Exception {
    for (String body :
        new String[] {
          "{", transaction("1", "OTHER", "2026-10-01"), transaction("1", "EXPENSE", "2026-02-30")
        })
      mvc.perform(post("/api/transactions").contentType("application/json").content(body))
          .andExpect(status().isBadRequest());
  }

  @Test
  void missingWritesReturn404() throws Exception {
    mvc.perform(delete("/api/transactions/999")).andExpect(status().isNotFound());
    mvc.perform(
            put("/api/transactions/999")
                .contentType("application/json")
                .content(transaction("1", "EXPENSE", "2026-10-01")))
        .andExpect(status().isNotFound());
    mvc.perform(delete("/api/budgets/999")).andExpect(status().isNotFound());
  }
}
