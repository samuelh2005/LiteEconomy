package me.samuelh2005.lite_economy.commands;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;

import me.samuelh2005.lite_economy.LiteEconomy;
import me.samuelh2005.lite_economy.commands.arguments.NamedUUIDArgumentType;
import me.samuelh2005.lite_economy.data.BankAccount;
import me.samuelh2005.lite_economy.data.Transaction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class PayCommand {
    private PayCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pay")
            .requires(source -> source.getEntity() instanceof ServerPlayer)
            .then(Commands.argument("from_account", NamedUUIDArgumentType.namedUUID(ctx -> LiteEconomy.getInstance().getDataStorage().getWithdrawableAccounts(getPlayer(ctx))))
                .then(Commands.argument("to_account", NamedUUIDArgumentType.namedUUID(ctx -> LiteEconomy.getInstance().getDataStorage().getBankAccounts().values().stream().toList()))
                    .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01D))
                        .executes(PayCommand::transfer)))));
    }

    private static int transfer(CommandContext<CommandSourceStack> context) {
        ServerPlayer actor = getPlayer(context);
        UUID fromId = NamedUUIDArgumentType.getUUID(context, "from_account");
        UUID toId = NamedUUIDArgumentType.getUUID(context, "to_account");
        BigDecimal amount = BigDecimal.valueOf(DoubleArgumentType.getDouble(context, "amount"));

        Optional<BankAccount> from = LiteEconomy.getInstance().getDataStorage().getBankAccountById(fromId);
        if (from.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Source account not found."));
            return 0;
        }
        Optional<BankAccount> to = LiteEconomy.getInstance().getDataStorage().getBankAccountById(toId);
        if (to.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Destination account not found."));
            return 0;
        }
        if (fromId.equals(toId)) {
            context.getSource().sendFailure(Component.literal("Source and destination accounts must be different."));
            return 0;
        }

        CommandSourceStack source = context.getSource();
        String fromAccountName = from.get().getAccountName();
        String toAccountName = to.get().getAccountName();
        Transaction transaction = LiteEconomy.getInstance().getTransactionService().createTransaction(actor, from.get(), to.get(), amount);
        LiteEconomy.getInstance().getTransactionService().submitTransaction(transaction).thenAccept(success ->
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

    private static ServerPlayer getPlayer(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            throw new IllegalStateException("Player-only command executed by non-player source.");
        }
        return player;
    }
}
