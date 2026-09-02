package com.clearledger.clear.ledger.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.clearledger.clear.ledger.account.Account;
import com.clearledger.clear.ledger.account.AccountRepository;
import com.clearledger.clear.ledger.idempotency.IdempotencyKeyRepository;
import com.clearledger.clear.ledger.ledger.LedgerEntryRepository;
import com.clearledger.clear.ledger.user.User;
import com.clearledger.clear.ledger.user.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class TransferServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @BeforeEach
    void cleanDatabase() {
        idempotencyKeyRepository.deleteAll();
        ledgerEntryRepository.deleteAll();
        transferRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void persistsTheEntireTransferAtomicallyInPostgres() {
        Instant now = Instant.now();

        User alice = userRepository.save(
                new User("alice@test.com", "not-a-real-password", now));
        User bob = userRepository.save(
                new User("bob@test.com", "not-a-real-password", now));

        Account from = accountRepository.save(
                new Account(alice.getId(), new BigDecimal("1000.00"), now));
        Account to = accountRepository.save(
                new Account(bob.getId(), new BigDecimal("500.00"), now));

        Transfer result = transferService.transfer(
                "integration-key-1",
                from.getId(),
                to.getId(),
                new BigDecimal("100.00"));

        Account updatedFrom = accountRepository.findById(from.getId()).orElseThrow();
        Account updatedTo = accountRepository.findById(to.getId()).orElseThrow();

        assertEquals(new BigDecimal("900.0000"), updatedFrom.getCachedBalance());
        assertEquals(new BigDecimal("600.0000"), updatedTo.getCachedBalance());
        assertEquals(1, transferRepository.count());
        assertEquals(2, ledgerEntryRepository.count());
        assertEquals(1, idempotencyKeyRepository.count());
        assertEquals(from.getId(), result.getFromAccountId());
        assertEquals(to.getId(), result.getToAccountId());
    }
}