package com.clearledger.clear.ledger.transfer;

import com.clearledger.clear.ledger.account.Account;
import com.clearledger.clear.ledger.account.AccountRepository;
import com.clearledger.clear.ledger.ledger.EntryDirection;
import com.clearledger.clear.ledger.ledger.LedgerEntry;
import com.clearledger.clear.ledger.ledger.LedgerEntryRepository;
import com.clearledger.clear.ledger.idempotency.IdempotencyKeyRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.clearledger.clear.ledger.idempotency.IdempotencyKey;
import java.util.Optional;

@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;

    public TransferService(AccountRepository accountRepository,
                           TransferRepository transferRepository,
                           LedgerEntryRepository ledgerEntryRepository,
                           IdempotencyKeyRepository idempotencyKeyRepository) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
    }

    @Transactional
    public Transfer transfer(String idempotencyKey, Long fromAccountId, Long toAccountId, BigDecimal amount) {
        Optional<IdempotencyKey> existing = idempotencyKeyRepository.findByKeyValue(idempotencyKey);
        if (existing.isPresent()) {
            return transferRepository.findById(existing.get().getTransferId())
                .orElseThrow(() -> new IllegalStateException("transfer missing for idempotency key"));
        }
        if (fromAccountId.equals(toAccountId)) {
            throw new IllegalArgumentException("from and to accounts must differ");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }

        Account from = accountRepository.findById(fromAccountId)
                .orElseThrow(() -> new IllegalArgumentException("from account not found"));
        Account to = accountRepository.findById(toAccountId)
                .orElseThrow(() -> new IllegalArgumentException("to account not found"));

        if (from.getCachedBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("insufficient funds");
        }

        Instant now = Instant.now();
        Transfer transfer = transferRepository.save(
                new Transfer(fromAccountId, toAccountId, amount, now));

        ledgerEntryRepository.save(new LedgerEntry(
                fromAccountId, transfer.getId(), amount, EntryDirection.DEBIT, now));
        ledgerEntryRepository.save(new LedgerEntry(
                toAccountId, transfer.getId(), amount, EntryDirection.CREDIT, now));

        from.setCachedBalance(from.getCachedBalance().subtract(amount));
        to.setCachedBalance(to.getCachedBalance().add(amount));
        accountRepository.save(from);
        accountRepository.save(to);

        idempotencyKeyRepository.save(new IdempotencyKey(idempotencyKey, transfer.getId(), now));
        return transfer;
    }
}