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
import me.samuelh2005.lite_economy.data.BankAccount;
import me.samuelh2005.lite_economy.data.Business;
import me.samuelh2005.lite_economy.data.Transaction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class TransferCommand {
    private TransferCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("transfer")
            .requires(source -> source.getEntity() instanceof ServerPlayer)
            .then(Commands.argument("from_account", StringArgumentType.string())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(CommandSuggestionUtil.quoteAll(getWithdrawableAccountNames(getPlayer(context))), builder))
                .then(Commands.argument("to_account", StringArgumentType.string())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(CommandSuggestionUtil.quoteAll(getAllAccountNames()), builder))
                    .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01D))
                        .executes(TransferCommand::transfer)))));
    }

    private static int transfer(CommandContext<CommandSourceStack> context) {
        ServerPlayer actor = getPlayer(context);
        String fromName = StringArgumentType.getString(context, "from_account");
        String toName = StringArgumentType.getString(context, "to_account");
        BigDecimal amount = BigDecimal.valueOf(DoubleArgumentType.getDouble(context, "amount"));

        Optional<BankAccount> from = resolveFromAccount(actor, fromName);
        if (from.isEmpty()) {
            context.getSource().sendFailure(Component.literal("You cannot transfer from account: " + fromName));
            return 0;
        }
        Optional<BankAccount> to = resolveUniqueAccount(toName);
        if (to.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Could not resolve destination account: " + toName));
            return 0;
        }
        if (from.get().getId().equals(to.get().getId())) {
            context.getSource().sendFailure(Component.literal("Source and destination accounts must be different."));
            return 0;
        }

        CommandSourceStack source = context.getSource();
        String fromAccountName = from.get().getAccountName();
        String toAccountName = to.get().getAccountName();
        Transaction transaction = TransactionService.createTransaction(actor, from.get(), to.get(), amount);
        TransactionService.submitTransaction(transaction).thenAccept(success ->
            source.getServer().execute(() -> {
                if (success) {
                    source.sendSuccess(
                        () -> Component.literal("Transferred $" + amount + " from '" + fromAccountName + "' to '" + toAccountName + "'."),
                        true
                    );
                } else {
                    source.sendFailure(Component.literal("Transfer failed. Check your permissions and balance."));
                }
            })
        ).exceptionally(error -> {
            source.getServer().execute(() -> source.sendFailure(Component.literal("Transfer failed unexpectedly.")));
            return null;
        });
        source.sendSuccess(
            () -> Component.literal("Transfer queued: $" + amount + " from '" + fromAccountName + "' to '" + toAccountName + "'."),
            false
        );
        return 1;
    }

    private static Optional<BankAccount> resolveFromAccount(ServerPlayer actor, String accountName) {
        String normalized = accountName.trim();

        Optional<BankAccount> personal = LiteEconomy.getDataStorage().getBankAccounts(actor).stream()
            .filter(account -> account.getAccountName().equalsIgnoreCase(normalized))
            .findFirst();
        if (personal.isPresent()) {
            return personal;
        }

        List<Business> businesses = LiteEconomy.getDataStorage().getBusinesses(actor);
        for (Business business : businesses) {
            boolean canWithdraw = business.getMembers().stream()
                .anyMatch(member ->
                    member.getPlayerId().equals(actor.getUUID()) &&
                    (member.getRole() == Business.BusinessMember.Role.OWNER || member.getRole() == Business.BusinessMember.Role.MANAGER));
            if (!canWithdraw) {
                continue;
            }
            Optional<BankAccount> account = LiteEconomy.getDataStorage().getBankAccounts(business).stream()
                .filter(found -> found.getAccountName().equalsIgnoreCase(normalized))
                .findFirst();
            if (account.isPresent()) {
                return account;
            }
        }

        return Optional.empty();
    }

    private static Optional<BankAccount> resolveUniqueAccount(String accountName) {
        String normalized = accountName.trim();
        List<BankAccount> matching = LiteEconomy.getDataStorage().getBankAccounts().values()
            .stream()
            .filter(account -> account.getAccountName().equalsIgnoreCase(normalized))
            .toList();
        if (matching.size() == 1) {
            return Optional.of(matching.get(0));
        }
        return Optional.empty();
    }

    private static List<String> getWithdrawableAccountNames(ServerPlayer actor) {
        List<String> names = new java.util.ArrayList<>();
        names.addAll(LiteEconomy.getDataStorage().getBankAccounts(actor).stream().map(BankAccount::getAccountName).toList());
        for (Business business : LiteEconomy.getDataStorage().getBusinesses(actor)) {
            boolean canWithdraw = business.getMembers().stream()
                .anyMatch(member ->
                    member.getPlayerId().equals(actor.getUUID()) &&
                    (member.getRole() == Business.BusinessMember.Role.OWNER || member.getRole() == Business.BusinessMember.Role.MANAGER));
            if (!canWithdraw) {
                continue;
            }
            names.addAll(LiteEconomy.getDataStorage().getBankAccounts(business).stream().map(BankAccount::getAccountName).toList());
        }
        return names.stream().distinct().toList();
    }

    private static List<String> getAllAccountNames() {
        return LiteEconomy.getDataStorage().getBankAccounts().values().stream()
            .map(BankAccount::getAccountName)
            .distinct()
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
