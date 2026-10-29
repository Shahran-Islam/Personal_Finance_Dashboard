package com.shahran.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    properties =
        "spring.datasource.url=jdbc:h2:mem:accounts;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class AccountSecurityTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired JdbcTemplate jdbc;
  @Autowired PasswordEncoder encoder;
  String transaction =
      "{\"description\":\"Groceries\",\"amount\":12.50,\"type\":\"EXPENSE\",\"categoryId\":3,\"date\":\"2026-10-10\"}";
  String budget = "{\"categoryId\":3,\"amount\":100,\"month\":\"2026-10\"}";

  void userAccount(String email) {
    jdbc.update(
        "INSERT INTO users(name,email,password_hash) VALUES(?,?,?)",
        "Test",
        email,
        encoder.encode("A-long-test-password"));
  }

  @Test
  void registrationHashesPasswordAndNormalizesEmail() throws Exception {
    String body =
        "{\"name\":\"Alice\",\"email\":\"Alice@Example.com\",\"password\":\"A-long-test-password\"}";
    mvc.perform(
            post("/api/auth/register").with(csrf()).contentType("application/json").content(body))
        .andExpect(status().isCreated());
    String hash =
        jdbc.queryForObject(
            "SELECT password_hash FROM users WHERE email='alice@example.com'", String.class);
    assertNotEquals("A-long-test-password", hash);
    assertTrue(encoder.matches("A-long-test-password", hash));
    mvc.perform(
            post("/api/auth/register").with(csrf()).contentType("application/json").content(body))
        .andExpect(status().isConflict());
    mvc.perform(
            post("/api/auth/register")
                .with(csrf())
                .contentType("application/json")
                .content(body.replace("A-long-test-password", "short")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void loginSessionAndLogoutWork() throws Exception {
    userAccount("alice@example.com");
    var result =
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .param("email", "ALICE@example.com")
                    .param("password", "A-long-test-password"))
            .andExpect(status().isNoContent())
            .andReturn();
    var session = (MockHttpSession) result.getRequest().getSession(false);
    assertNotNull(session);
    mvc.perform(get("/api/auth/me").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("alice@example.com"))
        .andExpect(jsonPath("$.password_hash").doesNotExist());
    mvc.perform(post("/api/auth/logout").session(session).with(csrf()))
        .andExpect(status().isNoContent());
    assertTrue(session.isInvalid());
    mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/auth/login")
                .with(csrf())
                .param("email", "alice@example.com")
                .param("password", "wrong"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void csrfAndAuthenticationAreRequired() throws Exception {
    mvc.perform(get("/")).andExpect(status().isOk());
    mvc.perform(get("/api/auth/csrf"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").isString());
    mvc.perform(get("/api/transactions?month=2026-10")).andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/auth/login").param("email", "a@example.com").param("password", "anything"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/transactions")
                .with(user("alice@example.com"))
                .contentType("application/json")
                .content(transaction))
        .andExpect(status().isForbidden());
  }

  @Test
  void accountsCannotReadModifyOrDeleteEachOthersRecords() throws Exception {
    userAccount("alice@example.com");
    userAccount("bob@example.com");
    long transactionId =
        mapper
            .readTree(
                mvc.perform(
                        post("/api/transactions")
                            .with(user("alice@example.com"))
                            .with(csrf())
                            .contentType("application/json")
                            .content(transaction))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asLong();
    long budgetId =
        mapper
            .readTree(
                mvc.perform(
                        post("/api/budgets")
                            .with(user("alice@example.com"))
                            .with(csrf())
                            .contentType("application/json")
                            .content(budget))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asLong();
    for (String resource : new String[] {"transactions/" + transactionId, "budgets/" + budgetId}) {
      mvc.perform(get("/api/" + resource).with(user("bob@example.com")))
          .andExpect(status().isNotFound());
      mvc.perform(delete("/api/" + resource).with(user("bob@example.com")).with(csrf()))
          .andExpect(status().isNotFound());
      mvc.perform(
              put("/api/" + resource)
                  .with(user("bob@example.com"))
                  .with(csrf())
                  .contentType("application/json")
                  .content(resource.startsWith("transactions") ? transaction : budget))
          .andExpect(status().isNotFound());
    }
    mvc.perform(get("/api/transactions?month=2026-10").with(user("bob@example.com")))
        .andExpect(jsonPath("$.length()").value(0));
    mvc.perform(get("/api/budgets?month=2026-10").with(user("bob@example.com")))
        .andExpect(jsonPath("$.length()").value(0));
    mvc.perform(get("/api/summaries?month=2026-10").with(user("bob@example.com")))
        .andExpect(jsonPath("$.expenses").value(0));
    mvc.perform(
            post("/api/budgets")
                .with(user("bob@example.com"))
                .with(csrf())
                .contentType("application/json")
                .content(budget))
        .andExpect(status().isCreated());
    mvc.perform(get("/api/summaries?month=2026-10").with(user("bob@example.com")))
        .andExpect(jsonPath("$.budgets[0].spent").value(0));
    mvc.perform(get("/api/summaries?month=2026-10").with(user("alice@example.com")))
        .andExpect(jsonPath("$.expenses").value(12.5))
        .andExpect(jsonPath("$.budgets[0].spent").value(12.5));
  }

  @Test
  void anonymousLegacyRowsStayPrivateAndCannotBeClaimed() throws Exception {
    userAccount("alice@example.com");
    jdbc.update(
        "INSERT INTO transactions(description,amount,type,category_id,transaction_date)"
            + " VALUES('Legacy',99,'EXPENSE',3,'2026-10-01')");
    mvc.perform(get("/api/summaries?month=2026-10").with(user("alice@example.com")))
        .andExpect(jsonPath("$.expenses").value(0));
    mvc.perform(
            post("/api/transactions")
                .with(user("alice@example.com"))
                .with(csrf())
                .contentType("application/json")
                .content(transaction.replace("}", ",\"userId\":999}")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void sampleMonthIsAtomicAndNotDuplicated() throws Exception {
    userAccount("alice@example.com");
    mvc.perform(post("/api/demo?month=2026-10").with(user("alice@example.com")).with(csrf()))
        .andExpect(status().isCreated());
    mvc.perform(post("/api/demo?month=2026-10").with(user("alice@example.com")).with(csrf()))
        .andExpect(status().isConflict());
    mvc.perform(get("/api/transactions?month=2026-10").with(user("alice@example.com")))
        .andExpect(jsonPath("$.length()").value(12));
    mvc.perform(get("/api/summaries?month=2026-10").with(user("alice@example.com")))
        .andExpect(jsonPath("$.income").value(4900));
  }
}
