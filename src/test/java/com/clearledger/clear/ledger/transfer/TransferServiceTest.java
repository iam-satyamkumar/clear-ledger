package com.clearledger.clear.ledger.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.clearledger.clear.ledger.account.Account;
import com.clearledger.clear.ledger.account.AccountRepository;
import com.clearledger.clear.ledger.idempotency.IdempotencyKey;
import com.clearledger.clear.ledger.idempotency.IdempotencyKeyRepository;
import com.clearledger.clear.ledger.ledger.EntryDirection;
import com.clearledger.clear.ledger.ledger.LedgerEntry;
import com.clearledger.clear.ledger.ledger.LedgerEntryRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @InjectMocks
    private TransferService transferService;

    @Test
    void transfersMoneyCreatesLedgerEntriesAndUpdatesBalances() {
        Account from = new Account(
                1L, new BigDecimal("1000.00"), Instant.now());
        Account to = new Account(
                2L, new BigDecimal("500.00"), Instant.now());

        when(idempotencyKeyRepository.findByKeyValue("key-1"))
                .thenReturn(Optional.empty());
        when(accountRepository.findById(1L)).thenReturn(Optional.of(from));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(to));

        Transfer savedTransfer = org.mockito.Mockito.mock(Transfer.class);
        when(savedTransfer.getId()).thenReturn(99L);
        when(transferRepository.save(any(Transfer.class)))
                .thenReturn(savedTransfer);

        Transfer result = transferService.transfer(
                "key-1", 1L, 2L, new BigDecimal("100.00"));

        assertSame(savedTransfer, result);
        assertEquals(new BigDecimal("900.00"), from.getCachedBalance());
        assertEquals(new BigDecimal("600.00"), to.getCachedBalance());

        ArgumentCaptor<LedgerEntry> entryCaptor =
                ArgumentCaptor.forClass(LedgerEntry.class);

        verify(ledgerEntryRepository, times(2)).save(entryCaptor.capture());

        List<LedgerEntry> entries = entryCaptor.getAllValues();

        assertEquals(1L, entries.get(0).getAccountId());
        assertEquals(99L, entries.get(0).getTransferId());
        assertEquals(EntryDirection.DEBIT, entries.get(0).getDirection());

        assertEquals(2L, entries.get(1).getAccountId());
        assertEquals(99L, entries.get(1).getTransferId());
        assertEquals(EntryDirection.CREDIT, entries.get(1).getDirection());

        verify(accountRepository).save(from);
        verify(accountRepository).save(to);
        verify(idempotencyKeyRepository).save(any(IdempotencyKey.class));
    }

    @Test
    void rejectsTransferWhenFundsAreInsufficientWithoutSavingAnything() {
        Account from = new Account(
                1L, new BigDecimal("50.00"), Instant.now());
        Account to = new Account(
                2L, new BigDecimal("500.00"), Instant.now());

        when(idempotencyKeyRepository.findByKeyValue("key-2"))
                .thenReturn(Optional.empty());
        when(accountRepository.findById(1L)).thenReturn(Optional.of(from));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(to));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> transferService.transfer(
                        "key-2", 1L, 2L, new BigDecimal("100.00")));

        assertEquals("insufficient funds", exception.getMessage());
        assertEquals(new BigDecimal("50.00"), from.getCachedBalance());
        assertEquals(new BigDecimal("500.00"), to.getCachedBalance());

        verifyNoInteractions(transferRepository, ledgerEntryRepository);
        verify(accountRepository, never()).save(any(Account.class));
        verify(idempotencyKeyRepository, never()).save(any(IdempotencyKey.class));
    }

    @Test
    void returnsExistingTransferForRepeatedIdempotencyKeyWithoutMovingMoneyAgain() {
        IdempotencyKey existingKey = new IdempotencyKey(
                "key-3", 99L, Instant.now());

        Transfer existingTransfer = org.mockito.Mockito.mock(Transfer.class);

        when(idempotencyKeyRepository.findByKeyValue("key-3"))
                .thenReturn(Optional.of(existingKey));
        when(transferRepository.findById(99L))
                .thenReturn(Optional.of(existingTransfer));

        Transfer result = transferService.transfer(
                "key-3", 1L, 2L, new BigDecimal("100.00"));

        assertSame(existingTransfer, result);

        verify(transferRepository).findById(99L);
        verifyNoInteractions(accountRepository, ledgerEntryRepository);
        verify(transferRepository, never()).save(any(Transfer.class));
        verify(idempotencyKeyRepository, never()).save(any(IdempotencyKey.class));
    }
}