package me.samuelh2005.lite_economy.commands;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import me.samuelh2005.lite_economy.LiteEconomy;
import me.samuelh2005.lite_economy.TransactionService;
import me.samuelh2005.lite_economy.commands.arguments.NamedUUIDArgumentType;
import me.samuelh2005.lite_economy.data.AccountOwner;
import me.samuelh2005.lite_economy.data.BankAccount;
import me.samuelh2005.lite_economy.data.Business;
import me.samuelh2005.lite_economy.data.Transaction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class BankCommand {
    private static final int DEFAULT_TRANSACTIONS_PAGE = 1;
    private static final int DEFAULT_TRANSACTIONS_LIMIT = 5;
    private static final DateTimeFormatter TRANSACTION_TIME_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault());
    private static final String ERR_ACCOUNT_NOT_FOUND_OWNED = "Bank account not found or not owned by you.";

    private BankCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("bank")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .executes(BankCommand::balancePlayer)
                .then(Commands.literal("balance")
                    .then(Commands.literal("self")
                        .executes(BankCommand::balancePlayer)
                        .then(Commands.argument("account", NamedUUIDArgumentType.namedUUID(ctx -> LiteEconomy.getDataStorage().getBankAccounts(getPlayer(ctx))))
                            .executes(BankCommand::balancePlayerAccount)))
                    .then(Commands.literal("business")
                        .then(Commands.argument("business", NamedUUIDArgumentType.namedUUID(ctx -> LiteEconomy.getDataStorage().getManageableBusinesses(getPlayer(ctx))))
                            .executes(BankCommand::balanceBusiness)
                            .then(Commands.argument("account", NamedUUIDArgumentType.namedUUID(ctx -> getBusinessAccountsFromContext(ctx, "business")))
                                .executes(BankCommand::balanceBusinessAccount)))))
                .then(Commands.literal("accounts")
                    .then(Commands.literal("self").executes(BankCommand::accountsPlayer))
                    .then(Commands.literal("business")
                        .then(Commands.argument("business", NamedUUIDArgumentType.namedUUID(ctx -> LiteEconomy.getDataStorage().getManageableBusinesses(getPlayer(ctx))))
                            .executes(BankCommand::accountsBusiness))))
                .then(Commands.literal("transactions")
                    .then(Commands.literal("self")
                        .then(Commands.argument("account", NamedUUIDArgumentType.namedUUID(ctx -> LiteEconomy.getDataStorage().getBankAccounts(getPlayer(ctx))))
                            .executes(BankCommand::transactionsSelf)
                            .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(BankCommand::transactionsSelf)
                                .then(Commands.argument("limit", IntegerArgumentType.integer(1))
                                    .executes(BankCommand::transactionsSelf)))))
                    .then(Commands.literal("business")
                        .then(Commands.argument("business", NamedUUIDArgumentType.namedUUID(ctx -> LiteEconomy.getDataStorage().getManageableBusinesses(getPlayer(ctx))))
                            .then(Commands.argument("account", NamedUUIDArgumentType.namedUUID(ctx -> getBusinessAccountsFromContext(ctx, "business")))
                                .executes(BankCommand::transactionsBusiness)
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                    .executes(BankCommand::transactionsBusiness)
                                    .then(Commands.argument("limit", IntegerArgumentType.integer(1))
                                        .executes(BankCommand::transactionsBusiness)))))))
                .then(Commands.literal("create")
                    .then(Commands.literal("self")
                        .then(Commands.argument("name", StringArgumentType.string())
                            .executes(BankCommand::createPlayer)))
                    .then(Commands.literal("business")
                        .then(Commands.argument("business", NamedUUIDArgumentType.namedUUID(ctx -> LiteEconomy.getDataStorage().getManageableBusinesses(getPlayer(ctx))))
                            .then(Commands.argument("name", StringArgumentType.string())
                                .executes(BankCommand::createBusiness)))))
                .then(Commands.literal("deposit")
                    .then(Commands.literal("self")
                        .then(Commands.argument("account", NamedUUIDArgumentType.namedUUID(ctx -> LiteEconomy.getDataStorage().getBankAccounts(getPlayer(ctx))))
                            .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01D))
                                .executes(BankCommand::depositPlayer))))
                    .then(Commands.literal("business")
                        .then(Commands.argument("business", NamedUUIDArgumentType.namedUUID(ctx -> LiteEconomy.getDataStorage().getManageableBusinesses(getPlayer(ctx))))
                            .then(Commands.argument("account", NamedUUIDArgumentType.namedUUID(ctx -> getBusinessAccountsFromContext(ctx, "business")))
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01D))
                                    .executes(BankCommand::depositBusiness))))))
                .then(Commands.literal("withdraw")
                    .then(Commands.literal("self")
                        .then(Commands.argument("account", NamedUUIDArgumentType.namedUUID(ctx -> LiteEconomy.getDataStorage().getBankAccounts(getPlayer(ctx))))
                            .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01D))
                                .executes(BankCommand::withdrawPlayer))))
                    .then(Commands.literal("business")
                        .then(Commands.argument("business", NamedUUIDArgumentType.namedUUID(ctx -> LiteEconomy.getDataStorage().getManageableBusinesses(getPlayer(ctx))))
                            .then(Commands.argument("account", NamedUUIDArgumentType.namedUUID(ctx -> getBusinessAccountsFromContext(ctx, "business")))
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01D))
                                    .executes(BankCommand::withdrawBusiness))))))
                .then(Commands.literal("rename")
                    .then(Commands.literal("self")
                        .then(Commands.argument("account", NamedUUIDArgumentType.namedUUID(ctx -> LiteEconomy.getDataStorage().getBankAccounts(getPlayer(ctx))))
                            .then(Commands.argument("name", StringArgumentType.string())
                                .executes(BankCommand::renamePlayer))))
                    .then(Commands.literal("business")
                        .then(Commands.argument("business", NamedUUIDArgumentType.namedUUID(ctx -> LiteEconomy.getDataStorage().getManageableBusinesses(getPlayer(ctx))))
                            .then(Commands.argument("account", NamedUUIDArgumentType.namedUUID(ctx -> getBusinessAccountsFromContext(ctx, "business")))
                                .then(Commands.argument("name", StringArgumentType.string())
                                    .executes(BankCommand::renameBusiness))))))
        );
    }

    private static List<BankAccount> getBusinessAccountsFromContext(CommandContext<CommandSourceStack> ctx, String businessArgName) {
        try {
            UUID businessId = NamedUUIDArgumentType.getUUID(ctx, businessArgName);
            return LiteEconomy.getDataStorage().getBusinessById(businessId)
                .map(business -> LiteEconomy.getDataStorage().getBankAccounts(business))
                .orElse(List.of());
        } catch (IllegalArgumentException ignored) {
            return List.of();
        }
    }

    /**
     * Resolves a business account from the "business" and "account" arguments, verifying that the
     * player manages the business and the account belongs to it. Returns empty if any check fails,
     * and sends the appropriate failure message to the source.
     * 
     * @return Optional containing the resolved BankAccount if successful
     */
    private static Optional<BankAccount> resolveBusinessAccount(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        return resolveBusinessAccountWithName(context, player).map(r -> r.account);
    }

    /**
     * Resolves a business account and returns both the account and business name.
     * Returns empty if any check fails, and sends the appropriate failure message to the source.
     */
    private static Optional<BusinessAccountResult> resolveBusinessAccountWithName(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        UUID businessId = NamedUUIDArgumentType.getUUID(context, "business");
        UUID accountId = NamedUUIDArgumentType.getUUID(context, "account");
        Optional<Business> business = LiteEconomy.getDataStorage().getBusinessById(businessId);
        if (business.isEmpty() || !business.get().isManageableBy(player.getUUID())) {
            context.getSource().sendFailure(Component.literal("Business not found or not manageable."));
            return Optional.empty();
        }
        Optional<BankAccount> account = LiteEconomy.getDataStorage().getBankAccountById(accountId);
        if (account.isEmpty() || !account.get().getOwner().getId().equals(businessId)) {
            context.getSource().sendFailure(Component.literal("Bank account not found or does not belong to that business."));
            return Optional.empty();
        }
        return Optional.of(new BusinessAccountResult(business.get(), account.get()));
    }

    /** Simple record to hold a business and its account together */
    private static record BusinessAccountResult(Business business, BankAccount account) {}

    private static int balancePlayer(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        List<BankAccount> accounts = LiteEconomy.getDataStorage().getBankAccounts(player);
        BigDecimal total = accounts.stream()
            .map(BankAccount::getBalance)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        context.getSource().sendSuccess(() -> Component.literal("Player balance across " + accounts.size() + " account(s): $" + total), false);
        return 1;
    }

    private static int balanceBusiness(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        UUID businessId = NamedUUIDArgumentType.getUUID(context, "business");
        Optional<Business> business = LiteEconomy.getDataStorage().getBusinessById(businessId);
        if (business.isEmpty() || !business.get().isManageableBy(player.getUUID())) {
            context.getSource().sendFailure(Component.literal("Business not found or not manageable."));
            return 0;
        }
        List<BankAccount> accounts = LiteEconomy.getDataStorage().getBankAccounts(business.get());
        BigDecimal total = accounts.stream()
            .map(BankAccount::getBalance)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        context.getSource().sendSuccess(() -> Component.literal("Business balance across " + accounts.size() + " account(s): $" + total), false);
        return 1;
    }

    /**
     * Validates that the account exists and is owned by the player. Returns empty if validation fails
     * and sends failure message.
     */
    private static Optional<BankAccount> validatePlayerAccount(CommandContext<CommandSourceStack> context, ServerPlayer player, UUID accountId) {
        Optional<BankAccount> account = LiteEconomy.getDataStorage().getBankAccountById(accountId);
        if (account.isEmpty() || !account.get().getOwner().getId().equals(player.getUUID())) {
            context.getSource().sendFailure(Component.literal(ERR_ACCOUNT_NOT_FOUND_OWNED));
            return Optional.empty();
        }
        return account;
    }

    private static int balancePlayerAccount(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        UUID accountId = NamedUUIDArgumentType.getUUID(context, "account");
        Optional<BankAccount> account = validatePlayerAccount(context, player, accountId);
        if (account.isEmpty()) {
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Player account '" + account.get().getAccountName() + "' balance: $" + account.get().getBalance()), false);
        return 1;
    }

    private static int balanceBusinessAccount(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        Optional<BankAccount> account = resolveBusinessAccount(context, player);
        if (account.isEmpty()) {
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Business account '" + account.get().getAccountName() + "' balance: $" + account.get().getBalance()), false);
        return 1;
    }

    private static int accountsPlayer(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        List<BankAccount> accounts = LiteEconomy.getDataStorage().getBankAccounts(player);
        if (accounts.isEmpty()) {
            context.getSource().sendFailure(Component.literal("You do not have any bank accounts yet."));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("Your player bank accounts:"), false);
        for (BankAccount account : accounts) {
            context.getSource().sendSuccess(
                () -> Component.literal("- " + account.getAccountName() + " | $" + account.getBalance() + " | id=" + account.getId()),
                false
            );
        }
        return accounts.size();
    }

    private static int accountsBusiness(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        UUID businessId = NamedUUIDArgumentType.getUUID(context, "business");
        Optional<Business> business = LiteEconomy.getDataStorage().getBusinessById(businessId);
        if (business.isEmpty() || !business.get().isManageableBy(player.getUUID())) {
            context.getSource().sendFailure(Component.literal("Business not found or not manageable."));
            return 0;
        }
        List<BankAccount> accounts = LiteEconomy.getDataStorage().getBankAccounts(business.get());
        if (accounts.isEmpty()) {
            context.getSource().sendFailure(Component.literal("No bank accounts for that business."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Business bank accounts for " + business.get().getName() + ":"), false);
        for (BankAccount account : accounts) {
            context.getSource().sendSuccess(
                () -> Component.literal("- " + account.getAccountName() + " | $" + account.getBalance() + " | id=" + account.getId()),
                false
            );
        }
        return accounts.size();
    }

    private static int transactionsSelf(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        UUID accountId = NamedUUIDArgumentType.getUUID(context, "account");
        Optional<BankAccount> account = validatePlayerAccount(context, player, accountId);
        if (account.isEmpty()) {
            return 0;
        }

        int page = getOptionalInt(context, "page", DEFAULT_TRANSACTIONS_PAGE);
        int limit = getOptionalInt(context, "limit", DEFAULT_TRANSACTIONS_LIMIT);
        return sendTransactionPage(context.getSource(), account.get(), page, limit, "self");
    }

    private static int transactionsBusiness(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        Optional<BusinessAccountResult> result = resolveBusinessAccountWithName(context, player);
        if (result.isEmpty()) {
            return 0;
        }

        int page = getOptionalInt(context, "page", DEFAULT_TRANSACTIONS_PAGE);
        int limit = getOptionalInt(context, "limit", DEFAULT_TRANSACTIONS_LIMIT);
        String businessName = result.get().business().getName();
        return sendTransactionPage(context.getSource(), result.get().account(), page, limit, "business=" + businessName);
    }

    private static int sendTransactionPage(CommandSourceStack source, BankAccount account, int page, int limit, String scopeLabel) {
        List<Transaction> transactions = LiteEconomy.getDataStorage().getTransactions().values().stream()
            .filter(transaction -> belongsToAccount(transaction, account.getId()))
            .sorted(Comparator.comparingLong(Transaction::getCreatedAtEpochMs).reversed().thenComparing(Transaction::getId))
            .toList();

        if (transactions.isEmpty()) {
            source.sendFailure(Component.literal("No transactions found for account '" + account.getAccountName() + "'."));
            return 0;
        }

        int totalPages = (transactions.size() + limit - 1) / limit;
        if (page > totalPages) {
            source.sendFailure(Component.literal("Page out of range. Requested " + page + ", max page is " + totalPages + "."));
            return 0;
        }

        int start = (page - 1) * limit;
        int end = Math.min(start + limit, transactions.size());
        List<Transaction> selected = transactions.subList(start, end);

        source.sendSuccess(
            () -> Component.literal(
                "Transactions for '" + account.getAccountName() + "' (" + scopeLabel + ") "
                    + "- page " + page + "/" + totalPages
                    + " - showing " + selected.size() + " of " + transactions.size()
            ),
            false
        );
        for (Transaction transaction : selected) {
            source.sendSuccess(() -> Component.literal(formatTransactionLine(account, transaction)), false);
        }
        return selected.size();
    }

    private static boolean belongsToAccount(Transaction transaction, UUID accountId) {
        return transaction.getFromId().filter(accountId::equals).isPresent()
            || transaction.getToId().filter(accountId::equals).isPresent();
    }

    private static String formatTransactionLine(BankAccount account, Transaction transaction) {
        UUID accountId = account.getId();
        boolean incoming = transaction.getToId().filter(accountId::equals).isPresent();
        Optional<UUID> counterpartyId = incoming ? transaction.getFromId() : transaction.getToId();
        String direction = incoming ? "IN" : "OUT";
        String counterparty = counterpartyId
            .map(BankCommand::formatAccountReference)
            .orElse("(external)");

        return "- [" + direction + "] $" + transaction.getAmount()
            + " counterparty=" + counterparty
            + " status=" + transaction.getStatus()
            + " createdAt=" + formatTimestamp(transaction.getCreatedAtEpochMs())
            + " txId=" + transaction.getId();
    }

    private static String formatAccountReference(UUID accountId) {
        return LiteEconomy.getDataStorage().getBankAccountById(accountId)
            .map(account -> account.getAccountName() + " (" + account.getId() + ")")
            .orElse(accountId.toString());
    }

    private static int getOptionalInt(CommandContext<CommandSourceStack> context, String name, int fallback) {
        try {
            return IntegerArgumentType.getInteger(context, name);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static String formatTimestamp(long epochMs) {
        return TRANSACTION_TIME_FORMAT.format(Instant.ofEpochMilli(epochMs));
    }

    private static int createPlayer(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        String accountName = StringArgumentType.getString(context, "name").trim();
        if (accountName.isBlank()) {
            context.getSource().sendFailure(Component.literal("Account name cannot be blank."));
            return 0;
        }

        Optional<BankAccount> account = LiteEconomy.getDataStorage().createBankAccount(accountName, AccountOwner.forPlayer(player));
        if (account.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Could not create bank account."));
            return 0;
        }
        BankAccount createdAccount = account.get();
        context.getSource().sendSuccess(
            () -> Component.literal("Created account '" + createdAccount.getAccountName() + "' with id=" + createdAccount.getId()),
            true
        );
        return 1;
    }

    private static int createBusiness(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        UUID businessId = NamedUUIDArgumentType.getUUID(context, "business");
        String accountName = StringArgumentType.getString(context, "name").trim();
        if (accountName.isBlank()) {
            context.getSource().sendFailure(Component.literal("Account name cannot be blank."));
            return 0;
        }

        Optional<Business> business = LiteEconomy.getDataStorage().getBusinessById(businessId);
        if (business.isEmpty() || !business.get().isManageableBy(player.getUUID())) {
            context.getSource().sendFailure(Component.literal("Business not found or not manageable."));
            return 0;
        }
        Optional<BankAccount> account = LiteEconomy.getDataStorage().createBankAccount(accountName, AccountOwner.forBusiness(business.get()));
        if (account.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Could not create bank account."));
            return 0;
        }
        BankAccount createdAccount = account.get();
        context.getSource().sendSuccess(
            () -> Component.literal("Created business account '" + createdAccount.getAccountName() + "' with id=" + createdAccount.getId()),
            true
        );
        return 1;
    }

    private static int depositPlayer(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        UUID accountId = NamedUUIDArgumentType.getUUID(context, "account");
        BigDecimal amount = BigDecimal.valueOf(DoubleArgumentType.getDouble(context, "amount"));

        Optional<BankAccount> account = validatePlayerAccount(context, player, accountId);
        if (account.isEmpty()) {
            return 0;
        }
        submitDeposit(context.getSource(), player, account.get(), amount);
        return 1;
    }

    private static int depositBusiness(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        BigDecimal amount = BigDecimal.valueOf(DoubleArgumentType.getDouble(context, "amount"));

        Optional<BankAccount> account = resolveBusinessAccount(context, player);
        if (account.isEmpty()) {
            return 0;
        }
        submitDeposit(context.getSource(), player, account.get(), amount);
        return 1;
    }

    private static int withdrawPlayer(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        UUID accountId = NamedUUIDArgumentType.getUUID(context, "account");
        BigDecimal amount = BigDecimal.valueOf(DoubleArgumentType.getDouble(context, "amount"));

        Optional<BankAccount> account = validatePlayerAccount(context, player, accountId);
        if (account.isEmpty()) {
            return 0;
        }
        submitWithdrawal(context.getSource(), player, account.get(), amount);
        return 1;
    }

    private static int withdrawBusiness(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        BigDecimal amount = BigDecimal.valueOf(DoubleArgumentType.getDouble(context, "amount"));

        Optional<BankAccount> account = resolveBusinessAccount(context, player);
        if (account.isEmpty()) {
            return 0;
        }
        submitWithdrawal(context.getSource(), player, account.get(), amount);
        return 1;
    }

    private static void submitDeposit(CommandSourceStack source, ServerPlayer actor, BankAccount account, BigDecimal amount) {
        String accountName = account.getAccountName();
        submitWithCallback(
            source,
            TransactionService.deposit(actor, account, amount),
            () -> Component.literal("Deposited $" + amount + " into '" + accountName + "'."),
            () -> Component.literal("Deposit failed.")
        );
        source.sendSuccess(() -> Component.literal("Deposit queued: $" + amount + " into '" + accountName + "'."), false);
    }

    private static void submitWithdrawal(CommandSourceStack source, ServerPlayer actor, BankAccount account, BigDecimal amount) {
        String accountName = account.getAccountName();
        submitWithCallback(
            source,
            TransactionService.withdraw(actor, account, amount),
            () -> Component.literal("Withdrew $" + amount + " from '" + accountName + "'."),
            () -> Component.literal("Withdrawal failed. Check your balance.")
        );
        source.sendSuccess(() -> Component.literal("Withdrawal queued: $" + amount + " from '" + accountName + "'."), false);
    }

    private static void submitWithCallback(
        CommandSourceStack source,
        java.util.concurrent.CompletionStage<Boolean> completion,
        Supplier<Component> successMessage,
        Supplier<Component> failureMessage
    ) {
        completion.thenAccept(success -> source.getServer().execute(() -> {
            if (success) {
                source.sendSuccess(successMessage, true);
            } else {
                source.sendFailure(failureMessage.get());
            }
        })).exceptionally(error -> {
            source.getServer().execute(() -> source.sendFailure(Component.literal("Transaction failed unexpectedly.")));
            return null;
        });
    }

    private static int renamePlayer(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        UUID accountId = NamedUUIDArgumentType.getUUID(context, "account");
        String newName = StringArgumentType.getString(context, "name").trim();
        if (newName.isBlank()) {
            context.getSource().sendFailure(Component.literal("New account name cannot be blank."));
            return 0;
        }

        Optional<BankAccount> account = validatePlayerAccount(context, player, accountId);
        if (account.isEmpty()) {
            return 0;
        }

        account.get().setAccountName(newName);
        LiteEconomy.getDataStorage().save(account.get());
        context.getSource().sendSuccess(() -> Component.literal("Renamed account to '" + newName + "'."), true);
        return 1;
    }

    private static int renameBusiness(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        String newName = StringArgumentType.getString(context, "name").trim();
        if (newName.isBlank()) {
            context.getSource().sendFailure(Component.literal("New account name cannot be blank."));
            return 0;
        }

        Optional<BankAccount> account = resolveBusinessAccount(context, player);
        if (account.isEmpty()) {
            return 0;
        }
        account.get().setAccountName(newName);
        LiteEconomy.getDataStorage().save(account.get());
        context.getSource().sendSuccess(() -> Component.literal("Renamed account to '" + newName + "'."), true);
        return 1;
    }

    private static ServerPlayer getPlayer(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            throw new IllegalStateException("Player-only command executed by non-player source.");
        }
        return player;
    }
}
