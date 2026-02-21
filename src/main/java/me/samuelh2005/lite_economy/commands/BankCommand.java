package me.samuelh2005.lite_economy.commands;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import me.samuelh2005.lite_economy.LiteEconomy;
import me.samuelh2005.lite_economy.TransactionService;
import me.samuelh2005.lite_economy.data.AccountOwner;
import me.samuelh2005.lite_economy.data.BankAccount;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class BankCommand {
    private BankCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("bank")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .executes(BankCommand::balancePlayer)
                .then(Commands.literal("balance")
                    .then(Commands.literal("player")
                        .executes(BankCommand::balancePlayer)
                        .then(Commands.argument("account", StringArgumentType.string())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(getOwnedAccountNames(getPlayer(context)), builder))
                            .executes(BankCommand::balancePlayerAccount)))
                    .then(Commands.literal("business")
                        .then(Commands.argument("business", StringArgumentType.string())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(getManageableBusinessNames(getPlayer(context)), builder))
                            .executes(BankCommand::balanceBusiness)
                            .then(Commands.argument("account", StringArgumentType.string())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(getBusinessAccountNames(getPlayer(context), StringArgumentType.getString(context, "business")), builder))
                                .executes(BankCommand::balanceBusinessAccount)))))
                .then(Commands.literal("accounts")
                    .then(Commands.literal("player").executes(BankCommand::accountsPlayer))
                    .then(Commands.literal("business")
                        .then(Commands.argument("business", StringArgumentType.string())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(getManageableBusinessNames(getPlayer(context)), builder))
                            .executes(BankCommand::accountsBusiness))))
                .then(Commands.literal("create")
                    .then(Commands.literal("player")
                        .then(Commands.argument("name", StringArgumentType.string())
                            .executes(BankCommand::createPlayer)))
                    .then(Commands.literal("business")
                        .then(Commands.argument("business", StringArgumentType.string())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(getManageableBusinessNames(getPlayer(context)), builder))
                            .then(Commands.argument("name", StringArgumentType.string())
                                .executes(BankCommand::createBusiness)))))
                .then(Commands.literal("deposit")
                    .then(Commands.literal("player")
                        .then(Commands.argument("account", StringArgumentType.string())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(getOwnedAccountNames(getPlayer(context)), builder))
                            .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01D))
                                .executes(BankCommand::depositPlayer))))
                    .then(Commands.literal("business")
                        .then(Commands.argument("business", StringArgumentType.string())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(getManageableBusinessNames(getPlayer(context)), builder))
                            .then(Commands.argument("account", StringArgumentType.string())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(getBusinessAccountNames(getPlayer(context), StringArgumentType.getString(context, "business")), builder))
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01D))
                                    .executes(BankCommand::depositBusiness))))))
                .then(Commands.literal("withdraw")
                    .then(Commands.literal("player")
                        .then(Commands.argument("account", StringArgumentType.string())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(getOwnedAccountNames(getPlayer(context)), builder))
                            .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01D))
                                .executes(BankCommand::withdrawPlayer))))
                    .then(Commands.literal("business")
                        .then(Commands.argument("business", StringArgumentType.string())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(getManageableBusinessNames(getPlayer(context)), builder))
                            .then(Commands.argument("account", StringArgumentType.string())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(getBusinessAccountNames(getPlayer(context), StringArgumentType.getString(context, "business")), builder))
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01D))
                                    .executes(BankCommand::withdrawBusiness))))))
                .then(Commands.literal("rename")
                    .then(Commands.literal("player")
                        .then(Commands.argument("account", StringArgumentType.string())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(getOwnedAccountNames(getPlayer(context)), builder))
                            .then(Commands.argument("name", StringArgumentType.string())
                                .executes(BankCommand::renamePlayer))))
                    .then(Commands.literal("business")
                        .then(Commands.argument("business", StringArgumentType.string())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(getManageableBusinessNames(getPlayer(context)), builder))
                            .then(Commands.argument("account", StringArgumentType.string())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(getBusinessAccountNames(getPlayer(context), StringArgumentType.getString(context, "business")), builder))
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
        Optional<me.samuelh2005.lite_economy.data.Business> business = resolveManageableBusiness(player, businessName);
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
        Optional<me.samuelh2005.lite_economy.data.Business> business = resolveManageableBusiness(player, businessName);
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

        context.getSource().sendSuccess(
            () -> Component.literal("Created account '" + account.get().getAccountName() + "' with id=" + account.get().getId()),
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

        Optional<me.samuelh2005.lite_economy.data.Business> business = resolveManageableBusiness(player, businessName);
        if (business.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Business not found or not manageable: " + businessName));
            return 0;
        }
        Optional<BankAccount> account = LiteEconomy.getDataStorage().createBankAccount(accountName, AccountOwner.forBusiness(business.get()));
        if (account.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Could not create bank account."));
            return 0;
        }
        context.getSource().sendSuccess(
            () -> Component.literal("Created business account '" + account.get().getAccountName() + "' with id=" + account.get().getId()),
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
        if (!TransactionService.deposit(account.get(), amount)) {
            context.getSource().sendFailure(Component.literal("Deposit failed."));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("Deposited $" + amount + " into '" + account.get().getAccountName() + "'."), true);
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
        if (!TransactionService.deposit(account.get(), amount)) {
            context.getSource().sendFailure(Component.literal("Deposit failed."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Deposited $" + amount + " into '" + account.get().getAccountName() + "'."), true);
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
        if (!TransactionService.withdraw(account.get(), amount)) {
            context.getSource().sendFailure(Component.literal("Withdrawal failed. Check your balance."));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("Withdrew $" + amount + " from '" + account.get().getAccountName() + "'."), true);
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
        if (!TransactionService.withdraw(account.get(), amount)) {
            context.getSource().sendFailure(Component.literal("Withdrawal failed. Check your balance."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Withdrew $" + amount + " from '" + account.get().getAccountName() + "'."), true);
        return 1;
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
                    (member.getRole() == me.samuelh2005.lite_economy.data.Business.BusinessMember.Role.OWNER ||
                        member.getRole() == me.samuelh2005.lite_economy.data.Business.BusinessMember.Role.MANAGER)))
            .findFirst();
    }

    private static Optional<BankAccount> resolveManagedBusinessAccount(ServerPlayer player, String businessName, String accountName) {
        Optional<me.samuelh2005.lite_economy.data.Business> business = resolveManageableBusiness(player, businessName);
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
                    (member.getRole() == me.samuelh2005.lite_economy.data.Business.BusinessMember.Role.OWNER ||
                        member.getRole() == me.samuelh2005.lite_economy.data.Business.BusinessMember.Role.MANAGER)))
            .map(me.samuelh2005.lite_economy.data.Business::getName)
            .toList();
    }

    private static List<String> getBusinessAccountNames(ServerPlayer player, String businessName) {
        Optional<me.samuelh2005.lite_economy.data.Business> business = resolveManageableBusiness(player, businessName);
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
