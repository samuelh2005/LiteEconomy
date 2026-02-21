package me.samuelh2005.lite_economy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

import me.samuelh2005.lite_economy.data.AccountOwner;
import me.samuelh2005.lite_economy.data.BankAccount;
import me.samuelh2005.lite_economy.data.Business;
import me.samuelh2005.lite_economy.data.EconomyData;
import me.samuelh2005.lite_economy.data.Transaction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;

public class EconomyService {
    private static final Map<UUID, Transaction> PENDING_TRANSACTIONS = new ConcurrentHashMap<>();

    // == Bank Account Methods ==

    public static Optional<BankAccount> createBankAccount(String name, Business business) {
        AccountOwner accountOwner = AccountOwner.forBusiness(business);
        return createBankAccount(name, accountOwner);
    }

    public static Optional<BankAccount> createBankAccount(String name, Player owner) {
        AccountOwner accountOwner = AccountOwner.forPlayer(owner);
        return createBankAccount(name, accountOwner);
    }

    public static Optional<BankAccount> createBankAccount(String name, AccountOwner owner) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }

        BankAccount account = new BankAccount(
            UUID.randomUUID(),
            name,
            BigDecimal.valueOf(0),
            owner
        );

        EconomyData data = EconomyData.get();
        data.getBankAccounts().add(account);
        data.setDirty();
        return Optional.of(account);
    }

    public static List<BankAccount> getBankAccounts(Business business) {
        AccountOwner accountOwner = AccountOwner.forBusiness(business);
        return getBankAccounts(accountOwner);
    }

    public static List<BankAccount> getBankAccounts(Player owner) {
        AccountOwner accountOwner = AccountOwner.forPlayer(owner);
        return getBankAccounts(accountOwner);
    }

    public static List<BankAccount> getBankAccounts(AccountOwner owner) {
        return EconomyData.get().getBankAccounts().stream()
            .filter(account -> {
                AccountOwner accountOwner = account.getOwner();
                boolean typeMatches = accountOwner.getType().equals(owner.getType());
                boolean uuidMatches = accountOwner.getId().equals(owner.getId());
                return typeMatches && uuidMatches;
            })
            .toList();
    }


    public static Optional<BankAccount> getBankAccountById(UUID accountId) {
        return EconomyData.get().getBankAccounts().stream()
            .filter(account -> account.getId().equals(accountId))
            .findFirst();
    }

    // == Business Methods ==

    public static Optional<Business> createBusiness(String name, Player owner) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }

        Business business = new Business(
            UUID.randomUUID(),
            name,
            List.of(Business.BusinessMember.forPlayer(owner, Business.BusinessMember.Role.OWNER))
        );

        EconomyData data = EconomyData.get();
        data.getBusinesses().add(business);
        data.setDirty();
        return Optional.of(business);
    }
    
    public static List<Business> getBusinesses(Player player) {
        return EconomyData.get().getBusinesses().stream()
            .filter(business -> {
                List<Business.BusinessMember> members = business.getMembers();
                return members.stream().anyMatch(member -> {
                    boolean uuidMatches = member.getPlayerId().equals(player.getUUID());
                    return uuidMatches;
                });
            })
            .toList();
    }

    public static List<Business> getBusinesses() {
        return EconomyData.get().getBusinesses();
    }

    private static Optional<Business> getBusinessById(UUID businessId) {
        return EconomyData.get().getBusinesses().stream()
            .filter(business -> business.getId().equals(businessId))
            .findFirst();
    }

    // == Transaction Methods ==

    public static Transaction createTransaction(Player actor, BankAccount from, BankAccount to, BigDecimal amount) {
        return new Transaction(actor, from, to, amount);
    }

    public static CompletionStage<Boolean> submitTransaction(Transaction transaction) {
        EconomyData data = EconomyData.get();
        Optional<Transaction> existing = getStoredTransactionById(transaction.getId());
        if (existing.isEmpty()) {
            data.getTransactions().add(transaction);
            data.sortTransactionsByTime();
            data.setDirty();
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
        EconomyData.get().setDirty();
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
        EconomyData.get().setDirty();
        return true;
    }

    public static CompletionStage<Boolean> completeTransaction(Transaction transaction) {
        Transaction pending = PENDING_TRANSACTIONS.remove(transaction.getId());
        if (pending == null) {
            Optional<Transaction> stored = getStoredTransactionById(transaction.getId());
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
        EconomyData data = EconomyData.get();
        data.sortTransactionsByTime();
        data.setDirty();
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

        Optional<BankAccount> fromOpt = getBankAccountById(fromId);
        Optional<BankAccount> toOpt = getBankAccountById(toId);
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

        EconomyData.get().setDirty();
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
            Optional<Business> business = getBusinessById(owner.getId());
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

    private static Optional<Transaction> getStoredTransactionById(UUID transactionId) {
        return EconomyData.get().getTransactions().stream()
            .filter(transaction -> transaction.getId().equals(transactionId))
            .findFirst();
    }

    public static void loadPendingTransactionsFromStorage() {
        PENDING_TRANSACTIONS.clear();
        EconomyData data = EconomyData.get();
        data.sortTransactionsByTime();
        data.getTransactions().stream()
            .filter(transaction -> transaction.getStatus() == Transaction.Status.PENDING)
            .forEach(transaction -> PENDING_TRANSACTIONS.put(transaction.getId(), transaction));
    }
}
