package com.warehouse.slotting.controller;

import com.warehouse.slotting.exception.UseCaseException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiExceptionHandlerTest {
    @ParameterizedTest
    @CsvSource({"INVALID_INPUT,400", "NOT_FOUND,404", "CONFLICT,409"})
    void mapsUseCaseErrorToHttpStatus(UseCaseException.Kind kind, int expectedStatus) throws Exception {
        MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new ApiExceptionHandler())
                .build()
                .perform(get("/failure/{kind}", kind.name()))
                .andExpect(status().is(expectedStatus))
                .andExpect(status().reason("Use case failed"));
    }

    @RestController
    static class FailingController {
        @GetMapping("/failure/{kind}")
        void fail(@PathVariable UseCaseException.Kind kind) {
            throw new UseCaseException(kind, "Use case failed");
        }
    }
}
