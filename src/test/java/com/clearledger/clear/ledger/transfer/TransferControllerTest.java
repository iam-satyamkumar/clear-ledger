package com.clearledger.clear.ledger.transfer;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clearledger.clear.ledger.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.springframework.orm.ObjectOptimisticLockingFailureException;

@WebMvcTest(TransferController.class)
@Import(GlobalExceptionHandler.class)
class TransferControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransferService transferService;

    @Test
    void returnsApiErrorWhenAmountIsNotPositive() throws Exception {
        mockMvc.perform(post("/transfers")
                        .header("Idempotency-Key", "http-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fromAccountId": 1,
                                  "toAccountId": 2,
                                  "amount": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("amount: amount must be greater than 0"));

        verifyNoInteractions(transferService);
    }

    @Test
    void returnsApiErrorWhenIdempotencyHeaderIsMissing() throws Exception {
        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fromAccountId": 1,
                                  "toAccountId": 2,
                                  "amount": 100
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("Idempotency-Key header is required"));

        verifyNoInteractions(transferService);
    }

@Test
void returnsConflictWhenOptimisticLockFails() throws Exception {
    when(transferService.transfer(any(), any(), any(), any()))
            .thenThrow(new ObjectOptimisticLockingFailureException(
                    "Account", 1L));

    mockMvc.perform(post("/transfers")
                    .header("Idempotency-Key", "http-key-conflict")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "fromAccountId": 1,
                              "toAccountId": 2,
                              "amount": 100
                            }
                            """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.message")
                    .value("Account was updated by another transfer; please retry"));
}
}