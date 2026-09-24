package com.finance.budget_buddy.controller;

import com.finance.budget_buddy.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;

class DatabaseErrorResponseTest {
    @Test
    void lockConflict_hasSafe409Response() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("C006"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(content().string(not(containsString("sensitive-value"))));
    }

    @Test
    void constraintConflict_doesNotExposeDatabaseDetails() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/constraint"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("C007"))
                .andExpect(content().string(not(containsString("sensitive-value"))));
    }

    @RestController
    static class FailingController {
        @GetMapping("/lock")
        String lock() { throw new CannotAcquireLockException("sensitive-value"); }

        @GetMapping("/constraint")
        String constraint() { throw new DataIntegrityViolationException("sensitive-value"); }
    }
}
