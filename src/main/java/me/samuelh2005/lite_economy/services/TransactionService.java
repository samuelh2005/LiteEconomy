package me.samuelh2005.lite_economy.services;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import me.samuelh2005.lite_economy.data.AccountOwner;
import me.samuelh2005.lite_economy.data.BankAccount;
import me.samuelh2005.lite_economy.data.Business;
import me.samuelh2005.lite_economy.data.Transaction;
import me.samuelh2005.lite_economy.data.storage.DataStorage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import me.samuelh2005.lite_economy.LiteEconomy;

public class TransactionService {
    private final LiteEconomy main;
    private final Map<UUID, Transaction> PENDING_TRANSACTIONS = new ConcurrentHashMap<>();
    private final AtomicBoolean PROCESSOR_RUNNING = new AtomicBoolean(false);
    private volatile Thread processorThread;

    public TransactionService(LiteEconomy main) {
        this.main = main;
    }
    public Transaction createTransaction(ServerPlayer actor, BankAccount from, BankAccount to, BigDecimal amount) {
        return new Transaction(actor, from, to, amount);
    }

    public CompletionStage<Boolean> submitTransaction(Transaction transaction) {
        DataStorage dataStorage = main.getDataStorage();
        Optional<Transaction> existing = dataStorage.getTransactionById(transaction.getId());
        if (existing.isEmpty()) {
            dataStorage.save(transaction);
        }
        final Transaction selectedTransaction = existing.orElse(transaction);
        if (selectedTransaction.getStatus() != Transaction.Status.PENDING) {
            return selectedTransaction.getCompletionFuture();
        }

        Transaction pending = PENDING_TRANSACTIONS.putIfAbsent(selectedTransaction.getId(), selectedTransaction);
        if (pending == null) {
            pending = selectedTransaction;
        }
        return pending.getCompletionFuture();
    }

    public CompletionStage<Boolean> deposit(ServerPlayer actor, BankAccount account, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return CompletableFuture.completedFuture(false);
        }

        return submitTransaction(new Transaction(actor, Optional.empty(), Optional.of(account), amount));
    }

    public CompletionStage<Boolean> withdraw(ServerPlayer actor, BankAccount account, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return CompletableFuture.completedFuture(false);
        }

        return submitTransaction(new Transaction(actor, Optional.of(account), Optional.empty(), amount));
    }

    public synchronized void startProcessor() {
        if (PROCESSOR_RUNNING.get()) {
            return;
        }
        PROCESSOR_RUNNING.set(true);
        processorThread = Thread.ofPlatform()
            .name("LiteEconomy-TransactionProcessor")
            .daemon(true)
            .start(this::runProcessorLoop);
    }

    public synchronized void stopProcessor() {
        PROCESSOR_RUNNING.set(false);
        Thread thread = processorThread;
        if (thread == null) {
            return;
        }
        thread.interrupt();
        try {
            thread.join(TimeUnit.SECONDS.toMillis(3));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            processorThread = null;
        }
    }

    private void runProcessorLoop() {
        while (PROCESSOR_RUNNING.get()) {
            if (!processNextTransaction()) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    if (!PROCESSOR_RUNNING.get()) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    private boolean processNextTransaction() {
        Optional<Transaction> next = PENDING_TRANSACTIONS.values().stream()
            .filter(transaction -> transaction.getStatus() == Transaction.Status.PENDING)
            .min(java.util.Comparator
                .comparingLong(Transaction::getCreatedAtEpochMs)
                .thenComparing(Transaction::getId));

        if (next.isEmpty()) {
            return false;
        }

        processTransaction(next.get());
        return true;
    }

    private void processTransaction(Transaction pending) {
        if (pending == null || !pending.beginCompletion()) {
            return;
        }

        MinecraftServer server = main.getServer();
        CompletableFuture<Boolean> processingFuture = new CompletableFuture<>();
        server.execute(() -> {
            try {
                boolean success = applyTransaction(pending);
                pending.completeWith(success);
                main.getDataStorage().save(pending);
                PENDING_TRANSACTIONS.remove(pending.getId());
                processingFuture.complete(success);
            } catch (RuntimeException e) {
                processingFuture.completeExceptionally(e);
            }
        });

        try {
            processingFuture.join();
        } catch (RuntimeException e) {
            LiteEconomy.LOGGER.error("Failed to process transaction {}", pending.getId(), e);
        }
    }

    private boolean applyTransaction(Transaction transaction) {
        UUID actorId = transaction.getActorId();
        Optional<UUID> fromIdOpt = transaction.getFromId();
        Optional<UUID> toIdOpt = transaction.getToId();

        if (fromIdOpt.isEmpty() && toIdOpt.isEmpty()) {
            return false;
        }

        DataStorage dataStorage = main.getDataStorage();
        BigDecimal amount = transaction.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        if (fromIdOpt.isPresent() && toIdOpt.isPresent()) {
            Optional<BankAccount> fromOpt = dataStorage.getBankAccountById(fromIdOpt.get());
            Optional<BankAccount> toOpt = dataStorage.getBankAccountById(toIdOpt.get());
            if (fromOpt.isEmpty() || toOpt.isEmpty()) {
                return false;
            }
            BankAccount from = fromOpt.get();
            BankAccount to = toOpt.get();

            if (!canTransfer(actorId, from, amount)) {
                return false;
            }

            from.setBalance(from.getBalance().subtract(amount));
            to.setBalance(to.getBalance().add(amount));
            dataStorage.save(from);
            dataStorage.save(to);
            return true;
        }

        if (fromIdOpt.isPresent()) {
            Optional<BankAccount> fromOpt = dataStorage.getBankAccountById(fromIdOpt.get());
            if (fromOpt.isEmpty()) {
                return false;
            }
            BankAccount from = fromOpt.get();
            if (!canTransfer(actorId, from, amount)) {
                return false;
            }
            from.setBalance(from.getBalance().subtract(amount));
            dataStorage.save(from);
            return true;
        }

        Optional<BankAccount> toOpt = dataStorage.getBankAccountById(toIdOpt.orElseThrow());
        if (toOpt.isEmpty()) {
            return false;
        }
        BankAccount to = toOpt.get();
        to.setBalance(to.getBalance().add(amount));
        dataStorage.save(to);
        return true;
    }

    private boolean canTransfer(UUID actorId, BankAccount from, BigDecimal amount) {
        if (from.getBalance().compareTo(amount) < 0) {
            return false;
        }
        return canWithdraw(actorId, from);
    }

    private boolean canWithdraw(UUID actorId, BankAccount from) {
        AccountOwner owner = from.getOwner();
        if (owner.getType() == AccountOwner.Type.PLAYER) {
            return owner.getId().equals(actorId);
        }
        if (owner.getType() == AccountOwner.Type.BUSINESS) {
            Optional<Business> business = main.getDataStorage().getBusinessById(owner.getId());
            return business.isPresent() && business.get().isManageableBy(actorId);
        }
        return false;
    }

    public void loadPendingTransactionsFromStorage() {
        PENDING_TRANSACTIONS.clear();
        main.getDataStorage().getTransactions().values().stream()
            .filter(transaction -> transaction.getStatus() == Transaction.Status.PENDING)
            .forEach(transaction -> PENDING_TRANSACTIONS.put(transaction.getId(), transaction));
    }
}
