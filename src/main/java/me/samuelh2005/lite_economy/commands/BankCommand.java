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
import me.samuelh2005.lite_economy.data.AccountOwner;
import me.samuelh2005.lite_economy.data.BankAccount;
import me.samuelh2005.lite_economy.data.Business;
import me.samuelh2005.lite_economy.data.Transaction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class BankCommand {
    private static final int DEFAULT_TRANSACTIONS_PAGE = 1;
    private static final int DEFAULT_TRANSACTIONS_LIMIT = 5;
    private static final DateTimeFormatter TRANSACTION_TIME_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault());

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
                        .then(Commands.argument("account", StringArgumentType.string())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(getOwnedAccountNames(getPlayer(context)), builder))
                            .executes(BankCommand::balancePlayerAccount)))
                    .then(Commands.literal("business")
                        .then(Commands.argument("business", StringArgumentType.string())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(CommandSuggestionUtil.quoteAll(getManageableBusinessNames(getPlayer(context))), builder))
                            .executes(BankCommand::balanceBusiness)
                            .then(Commands.argument("account", StringArgumentType.string())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(CommandSuggestionUtil.quoteAll(getBusinessAccountNames(getPlayer(context), StringArgumentType.getString(context, "business"))), builder))
                                .executes(BankCommand::balanceBusinessAccount)))))
                .then(Commands.literal("accounts")
                    .then(Commands.literal("self").executes(BankCommand::accountsPlayer))
                    .then(Commands.literal("business")
                        .then(Commands.argument("business", StringArgumentType.string())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(CommandSuggestionUtil.quoteAll(getManageableBusinessNames(getPlayer(context))), builder))
                            .executes(BankCommand::accountsBusiness))))
                .then(Commands.literal("transactions")
                    .then(Commands.literal("self")
                        .then(Commands.argument("account", StringArgumentType.string())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(CommandSuggestionUtil.quoteAll(getOwnedAccountNames(getPlayer(context))), builder))
                            .executes(BankCommand::transactionsSelf)
                            .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(BankCommand::transactionsSelf)
                                .then(Commands.argument("limit", IntegerArgumentType.integer(1))
                                    .executes(BankCommand::transactionsSelf)))))
                    .then(Commands.literal("business")
                        .then(Commands.argument("business", StringArgumentType.string())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(CommandSuggestionUtil.quoteAll(getManageableBusinessNames(getPlayer(context))), builder))
                            .then(Commands.argument("account", StringArgumentType.string())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(CommandSuggestionUtil.quoteAll(getBusinessAccountNames(getPlayer(context), StringArgumentType.getString(context, "business"))), builder))
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
                        .then(Commands.argument("business", StringArgumentType.string())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(CommandSuggestionUtil.quoteAll(getManageableBusinessNames(getPlayer(context))), builder))
                            .then(Commands.argument("name", StringArgumentType.string())
                                .executes(BankCommand::createBusiness)))))
                .then(Commands.literal("deposit")
                    .then(Commands.literal("self")
                        .then(Commands.argument("account", StringArgumentType.string())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(CommandSuggestionUtil.quoteAll(getOwnedAccountNames(getPlayer(context))), builder))
                            .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01D))
                                .executes(BankCommand::depositPlayer))))
                    .then(Commands.literal("business")
                        .then(Commands.argument("business", StringArgumentType.string())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(CommandSuggestionUtil.quoteAll(getManageableBusinessNames(getPlayer(context))), builder))
                            .then(Commands.argument("account", StringArgumentType.string())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(CommandSuggestionUtil.quoteAll(getBusinessAccountNames(getPlayer(context), StringArgumentType.getString(context, "business"))), builder))
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01D))
                                    .executes(BankCommand::depositBusiness))))))
                .then(Commands.literal("withdraw")
                    .then(Commands.literal("self")
                        .then(Commands.argument("account", StringArgumentType.string())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(CommandSuggestionUtil.quoteAll(getOwnedAccountNames(getPlayer(context))), builder))
                            .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01D))
                                .executes(BankCommand::withdrawPlayer))))
                    .then(Commands.literal("business")
                        .then(Commands.argument("business", StringArgumentType.string())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(CommandSuggestionUtil.quoteAll(getManageableBusinessNames(getPlayer(context))), builder))
                            .then(Commands.argument("account", StringArgumentType.string())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(CommandSuggestionUtil.quoteAll(getBusinessAccountNames(getPlayer(context), StringArgumentType.getString(context, "business"))), builder))
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01D))
                                    .executes(BankCommand::withdrawBusiness))))))
                .then(Commands.literal("rename")
                    .then(Commands.literal("self")
                        .then(Commands.argument("account", StringArgumentType.string())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(CommandSuggestionUtil.quoteAll(getOwnedAccountNames(getPlayer(context))), builder))
                            .then(Commands.argument("name", StringArgumentType.string())
                                .executes(BankCommand::renamePlayer))))
                    .then(Commands.literal("business")
                        .then(Commands.argument("business", StringArgumentType.string())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(CommandSuggestionUtil.quoteAll(getManageableBusinessNames(getPlayer(context))), builder))
                            .then(Commands.argument("account", StringArgumentType.string())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(CommandSuggestionUtil.quoteAll(getBusinessAccountNames(getPlayer(context), StringArgumentType.getString(context, "business"))), builder))
                                .then(Commands.argument("name", StringArgumentType.string())
                                    .executes(BankCommand::renameBusiness))))))
        );
    }

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
        String businessName = StringArgumentType.getString(context, "business");
        Optional<Business> business = resolveManageableBusiness(player, businessName);
        if (business.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Business not found or not manageable: " + businessName));
            return 0;
        }
        List<BankAccount> accounts = LiteEconomy.getDataStorage().getBankAccounts(business.get());
        BigDecimal total = accounts.stream()
            .map(BankAccount::getBalance)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        context.getSource().sendSuccess(() -> Component.literal("Business balance across " + accounts.size() + " account(s): $" + total), false);
        return 1;
    }

    private static int balancePlayerAccount(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        String accountName = StringArgumentType.getString(context, "account");
        Optional<BankAccount> account = resolveOwnedAccount(player, accountName);
        if (account.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Bank account not found: " + accountName));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Player account '" + account.get().getAccountName() + "' balance: $" + account.get().getBalance()), false);
        return 1;
    }

    private static int balanceBusinessAccount(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        String businessName = StringArgumentType.getString(context, "business");
        String accountName = StringArgumentType.getString(context, "account");
        Optional<BankAccount> account = resolveManagedBusinessAccount(player, businessName, accountName);
        if (account.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Business bank account not found: " + accountName));
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
        String businessName = StringArgumentType.getString(context, "business");
        Optional<Business> business = resolveManageableBusiness(player, businessName);
        if (business.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Business not found or not manageable: " + businessName));
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
        String accountName = StringArgumentType.getString(context, "account");
        Optional<BankAccount> account = resolveOwnedAccount(player, accountName);
        if (account.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Bank account not found: " + accountName));
            return 0;
        }

        int page = getOptionalInt(context, "page", DEFAULT_TRANSACTIONS_PAGE);
        int limit = getOptionalInt(context, "limit", DEFAULT_TRANSACTIONS_LIMIT);
        return sendTransactionPage(context.getSource(), account.get(), page, limit, "self");
    }

    private static int transactionsBusiness(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        String businessName = StringArgumentType.getString(context, "business");
        String accountName = StringArgumentType.getString(context, "account");
        Optional<BankAccount> account = resolveManagedBusinessAccount(player, businessName, accountName);
        if (account.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Business bank account not found: " + accountName));
            return 0;
        }

        int page = getOptionalInt(context, "page", DEFAULT_TRANSACTIONS_PAGE);
        int limit = getOptionalInt(context, "limit", DEFAULT_TRANSACTIONS_LIMIT);
        return sendTransactionPage(context.getSource(), account.get(), page, limit, "business=" + businessName);
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
        BankAccount createdAccount = account.orElseThrow();
        LiteEconomy.getDataStorage().save(createdAccount);

        context.getSource().sendSuccess(
            () -> Component.literal("Created account '" + createdAccount.getAccountName() + "' with id=" + createdAccount.getId()),
            true
        );
        return 1;
    }

    private static int createBusiness(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        String businessName = StringArgumentType.getString(context, "business");
        String accountName = StringArgumentType.getString(context, "name").trim();
        if (accountName.isBlank()) {
            context.getSource().sendFailure(Component.literal("Account name cannot be blank."));
            return 0;
        }

        Optional<Business> business = resolveManageableBusiness(player, businessName);
        if (business.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Business not found or not manageable: " + businessName));
            return 0;
        }
        Optional<BankAccount> account = LiteEconomy.getDataStorage().createBankAccount(accountName, AccountOwner.forBusiness(business.get()));
        if (account.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Could not create bank account."));
            return 0;
        }
        BankAccount createdAccount = account.orElseThrow();
        LiteEconomy.getDataStorage().save(createdAccount);
        context.getSource().sendSuccess(
            () -> Component.literal("Created business account '" + createdAccount.getAccountName() + "' with id=" + createdAccount.getId()),
            true
        );
        return 1;
    }

    private static int depositPlayer(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        String accountName = StringArgumentType.getString(context, "account");
        BigDecimal amount = BigDecimal.valueOf(DoubleArgumentType.getDouble(context, "amount"));

        Optional<BankAccount> account = resolveOwnedAccount(player, accountName);
        if (account.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Bank account not found: " + accountName));
            return 0;
        }
        CommandSourceStack source = context.getSource();
        String targetAccountName = account.get().getAccountName();
        submitWithCallback(
            source,
            TransactionService.deposit(player, account.get(), amount),
            () -> Component.literal("Deposited $" + amount + " into '" + targetAccountName + "'."),
            () -> Component.literal("Deposit failed.")
        );
        source.sendSuccess(() -> Component.literal("Deposit queued: $" + amount + " into '" + targetAccountName + "'."), false);
        return 1;
    }

    private static int depositBusiness(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        String businessName = StringArgumentType.getString(context, "business");
        String accountName = StringArgumentType.getString(context, "account");
        BigDecimal amount = BigDecimal.valueOf(DoubleArgumentType.getDouble(context, "amount"));

        Optional<BankAccount> account = resolveManagedBusinessAccount(player, businessName, accountName);
        if (account.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Business bank account not found: " + accountName));
            return 0;
        }
        CommandSourceStack source = context.getSource();
        String targetAccountName = account.get().getAccountName();
        submitWithCallback(
            source,
            TransactionService.deposit(player, account.get(), amount),
            () -> Component.literal("Deposited $" + amount + " into '" + targetAccountName + "'."),
            () -> Component.literal("Deposit failed.")
        );
        source.sendSuccess(() -> Component.literal("Deposit queued: $" + amount + " into '" + targetAccountName + "'."), false);
        return 1;
    }

    private static int withdrawPlayer(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        String accountName = StringArgumentType.getString(context, "account");
        BigDecimal amount = BigDecimal.valueOf(DoubleArgumentType.getDouble(context, "amount"));

        Optional<BankAccount> account = resolveOwnedAccount(player, accountName);
        if (account.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Bank account not found: " + accountName));
            return 0;
        }
        CommandSourceStack source = context.getSource();
        String targetAccountName = account.get().getAccountName();
        submitWithCallback(
            source,
            TransactionService.withdraw(player, account.get(), amount),
            () -> Component.literal("Withdrew $" + amount + " from '" + targetAccountName + "'."),
            () -> Component.literal("Withdrawal failed. Check your balance.")
        );
        source.sendSuccess(() -> Component.literal("Withdrawal queued: $" + amount + " from '" + targetAccountName + "'."), false);
        return 1;
    }

    private static int withdrawBusiness(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        String businessName = StringArgumentType.getString(context, "business");
        String accountName = StringArgumentType.getString(context, "account");
        BigDecimal amount = BigDecimal.valueOf(DoubleArgumentType.getDouble(context, "amount"));

        Optional<BankAccount> account = resolveManagedBusinessAccount(player, businessName, accountName);
        if (account.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Business bank account not found: " + accountName));
            return 0;
        }
        CommandSourceStack source = context.getSource();
        String targetAccountName = account.get().getAccountName();
        submitWithCallback(
            source,
            TransactionService.withdraw(player, account.get(), amount),
            () -> Component.literal("Withdrew $" + amount + " from '" + targetAccountName + "'."),
            () -> Component.literal("Withdrawal failed. Check your balance.")
        );
        source.sendSuccess(() -> Component.literal("Withdrawal queued: $" + amount + " from '" + targetAccountName + "'."), false);
        return 1;
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
        String accountName = StringArgumentType.getString(context, "account");
        String newName = StringArgumentType.getString(context, "name").trim();
        if (newName.isBlank()) {
            context.getSource().sendFailure(Component.literal("New account name cannot be blank."));
            return 0;
        }

        Optional<BankAccount> account = resolveOwnedAccount(player, accountName);
        if (account.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Bank account not found: " + accountName));
            return 0;
        }

        account.get().setAccountName(newName);
        LiteEconomy.getDataStorage().save(account.get());
        context.getSource().sendSuccess(() -> Component.literal("Renamed account to '" + newName + "'."), true);
        return 1;
    }

    private static int renameBusiness(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        String businessName = StringArgumentType.getString(context, "business");
        String accountName = StringArgumentType.getString(context, "account");
        String newName = StringArgumentType.getString(context, "name").trim();
        if (newName.isBlank()) {
            context.getSource().sendFailure(Component.literal("New account name cannot be blank."));
            return 0;
        }

        Optional<BankAccount> account = resolveManagedBusinessAccount(player, businessName, accountName);
        if (account.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Business bank account not found: " + accountName));
            return 0;
        }
        account.get().setAccountName(newName);
        LiteEconomy.getDataStorage().save(account.get());
        context.getSource().sendSuccess(() -> Component.literal("Renamed account to '" + newName + "'."), true);
        return 1;
    }

    private static Optional<BankAccount> resolveOwnedAccount(ServerPlayer player, String accountName) {
        String normalized = accountName.trim();
        return LiteEconomy.getDataStorage().getBankAccounts(player).stream()
            .filter(account -> account.getAccountName().equalsIgnoreCase(normalized))
            .findFirst();
    }

    private static Optional<me.samuelh2005.lite_economy.data.Business> resolveManageableBusiness(ServerPlayer player, String businessName) {
        String normalized = businessName.trim();
        return LiteEconomy.getDataStorage().getBusinesses(player).stream()
            .filter(business -> business.getName().equalsIgnoreCase(normalized))
            .filter(business -> business.getMembers().stream()
                .anyMatch(member ->
                    member.getPlayerId().equals(player.getUUID()) &&
                    (member.getRole() == Business.BusinessMember.Role.OWNER ||
                        member.getRole() == Business.BusinessMember.Role.MANAGER)))
            .findFirst();
    }

    private static Optional<BankAccount> resolveManagedBusinessAccount(ServerPlayer player, String businessName, String accountName) {
        Optional<Business> business = resolveManageableBusiness(player, businessName);
        if (business.isEmpty()) {
            return Optional.empty();
        }
        String normalized = accountName.trim();
        return LiteEconomy.getDataStorage().getBankAccounts(business.get()).stream()
            .filter(account -> account.getAccountName().equalsIgnoreCase(normalized))
            .findFirst();
    }

    private static List<String> getOwnedAccountNames(ServerPlayer player) {
        return LiteEconomy.getDataStorage().getBankAccounts(player).stream()
            .map(BankAccount::getAccountName)
            .toList();
    }

    private static List<String> getManageableBusinessNames(ServerPlayer player) {
        return LiteEconomy.getDataStorage().getBusinesses(player).stream()
            .filter(business -> business.getMembers().stream()
                .anyMatch(member ->
                    member.getPlayerId().equals(player.getUUID()) &&
                    (member.getRole() == Business.BusinessMember.Role.OWNER ||
                        member.getRole() == Business.BusinessMember.Role.MANAGER)))
            .map(Business::getName)
            .toList();
    }

    private static List<String> getBusinessAccountNames(ServerPlayer player, String businessName) {
        Optional<Business> business = resolveManageableBusiness(player, businessName);
        if (business.isEmpty()) {
            return List.of();
        }
        return LiteEconomy.getDataStorage().getBankAccounts(business.get()).stream()
            .map(BankAccount::getAccountName)
            .toList();
    }

    private static ServerPlayer getPlayer(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            throw new IllegalStateException("Player-only command executed by non-player source.");
        }
        return player;
    }
}
