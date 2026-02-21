package me.samuelh2005.lite_economy;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

import me.samuelh2005.lite_economy.data.AccountOwner;
import me.samuelh2005.lite_economy.data.BankAccount;
import me.samuelh2005.lite_economy.data.Business;
import me.samuelh2005.lite_economy.data.Transaction;
import me.samuelh2005.lite_economy.data.storage.DataStorage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;

public class TransactionService {
    private static final Map<UUID, Transaction> PENDING_TRANSACTIONS = new ConcurrentHashMap<>();

    public static Transaction createTransaction(Player actor, BankAccount from, BankAccount to, BigDecimal amount) {
        return new Transaction(actor, from, to, amount);
    }

    public static CompletionStage<Boolean> submitTransaction(Transaction transaction) {
        DataStorage dataStorage = LiteEconomy.getDataStorage();
        Optional<Transaction> existing = dataStorage.getTransactionById(transaction.getId());
        if (existing.isEmpty()) {
            dataStorage.save(transaction);
        }
        final Transaction selectedTransaction = existing.orElse(transaction);

        Transaction pending = PENDING_TRANSACTIONS.computeIfAbsent(
            selectedTransaction.getId(),
            ignored -> selectedTransaction
        );
        return pending.getCompletionFuture();
    }

    public static boolean deposit(BankAccount account, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        
        account.setBalance(account.getBalance().add(amount));
        LiteEconomy.getDataStorage().save(account);
        return true;
    }

    public static boolean withdraw(BankAccount account, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        
        if (account.getBalance().compareTo(amount) < 0) {
            return false;
        }
        
        account.setBalance(account.getBalance().subtract(amount));
        LiteEconomy.getDataStorage().save(account);
        return true;
    }

    public static CompletionStage<Boolean> completeTransaction(Transaction transaction) {
        Transaction pending = PENDING_TRANSACTIONS.remove(transaction.getId());
        if (pending == null) {
            Optional<Transaction> stored = LiteEconomy.getDataStorage().getTransactionById(transaction.getId());
            if (stored.isPresent()) {
                return stored.get().getCompletionFuture();
            }
            return CompletableFuture.completedFuture(false);
        }
        if (!pending.beginCompletion()) {
            return pending.getCompletionFuture();
        }

        boolean success = applyTransaction(pending);
        CompletionStage<Boolean> completion = pending.completeWith(success);
        LiteEconomy.getDataStorage().save(pending);
        return completion;
    }

    private static boolean applyTransaction(Transaction transaction) {
        UUID actorId = transaction.getActorId();
        UUID fromId = transaction.getFromId();
        UUID toId = transaction.getToId();

        MinecraftServer server = LiteEconomy.getServer();
        Player actor = server.getPlayerList().getPlayer(actorId);
        if (actor == null) {
            return false;
        }

        DataStorage dataStorage = LiteEconomy.getDataStorage();
        Optional<BankAccount> fromOpt = dataStorage.getBankAccountById(fromId);
        Optional<BankAccount> toOpt = dataStorage.getBankAccountById(toId);
        if (fromOpt.isEmpty() || toOpt.isEmpty()) {
            return false;
        }
        BankAccount from = fromOpt.get();
        BankAccount to = toOpt.get();

        if (!canTransfer(actor, from, transaction.getAmount())) {
            return false;
        }

        BigDecimal amount = transaction.getAmount();

        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));
        dataStorage.save(from);
        dataStorage.save(to);
        return true;
    }

    private static boolean canTransfer(Player actor, BankAccount from, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        if (from.getBalance().compareTo(amount) < 0) {
            return false;
        }
        return canWithdraw(actor, from);
    }

    private static boolean canWithdraw(Player actor, BankAccount from) {
        AccountOwner owner = from.getOwner();
        if (owner.getType() == AccountOwner.Type.PLAYER) {
            return owner.getId().equals(actor.getUUID());
        }
        if (owner.getType() == AccountOwner.Type.BUSINESS) {
            Optional<Business> business = LiteEconomy.getDataStorage().getBusinessById(owner.getId());
            if (business.isEmpty()) {
                return false;
            }
            return business.get().getMembers().stream()
                .anyMatch(member ->
                    member.getPlayerId().equals(actor.getUUID()) &&
                    (member.getRole() == Business.BusinessMember.Role.OWNER || member.getRole() == Business.BusinessMember.Role.MANAGER));
        }
        return false;
    }

    public static void loadPendingTransactionsFromStorage() {
        PENDING_TRANSACTIONS.clear();
        LiteEconomy.getDataStorage().getTransactions().stream()
            .filter(transaction -> transaction.getStatus() == Transaction.Status.PENDING)
            .forEach(transaction -> PENDING_TRANSACTIONS.put(transaction.getId(), transaction));
    }
}
