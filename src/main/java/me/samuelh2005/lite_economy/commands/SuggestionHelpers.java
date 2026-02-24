package me.samuelh2005.lite_economy.commands;

import java.util.List;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import me.samuelh2005.lite_economy.LiteEconomy;
import me.samuelh2005.lite_economy.data.BankAccount;
import me.samuelh2005.lite_economy.data.Business;
import net.minecraft.commands.CommandSourceStack;

/**
 * Utility class for providing UUID suggestions that are compatible with vanilla clients.
 */
public final class SuggestionHelpers {

    private SuggestionHelpers() {
    }

    /**
     * Suggestion provider for bank accounts.
     */
    public static final SuggestionProvider<CommandSourceStack> BANK_ACCOUNTS = (context, builder) -> {
        List<BankAccount> accounts = LiteEconomy.getInstance().getDataStorage()
            .getBankAccounts(context.getSource().getPlayerOrException());
        for (BankAccount account : accounts) {
            builder.suggest(account.getId().toString(), new LiteralMessage(account.getAccountName()));
        }
        return builder.buildFuture();
    };

    /**
     * Suggestion provider for businesses the player is a member of.
     */
    public static final SuggestionProvider<CommandSourceStack> BUSINESSES = (context, builder) -> {
        List<Business> businesses = LiteEconomy.getInstance().getDataStorage()
            .getBusinesses(context.getSource().getPlayerOrException());
        for (Business business : businesses) {
            builder.suggest(business.getId().toString(), new LiteralMessage(business.getName()));
        }
        return builder.buildFuture();
    };

    /**
     * Suggestion provider for businesses the player can manage.
     */
    public static final SuggestionProvider<CommandSourceStack> MANAGABLE_BUSINESSES = (context, builder) -> {
        List<Business> businesses = LiteEconomy.getInstance().getDataStorage()
            .getManageableBusinesses(context.getSource().getPlayerOrException());
        for (Business business : businesses) {
            builder.suggest(business.getId().toString(), new LiteralMessage(business.getName()));
        }
        return builder.buildFuture();
    };

    /**
     * Suggestion provider for all bank accounts (used for 'to' accounts in pay command).
     */
    public static final SuggestionProvider<CommandSourceStack> ALL_BANK_ACCOUNTS = (context, builder) -> {
        var accounts = LiteEconomy.getInstance().getDataStorage().getBankAccounts();
        for (BankAccount account : accounts.values()) {
            builder.suggest(account.getId().toString(), new LiteralMessage(account.getAccountName()));
        }
        return builder.buildFuture();
    };

    /**
     * Suggestion provider for withdrawable accounts.
     */
    public static final SuggestionProvider<CommandSourceStack> WITHDRAWABLE_ACCOUNTS = (context, builder) -> {
        List<BankAccount> accounts = LiteEconomy.getInstance().getDataStorage()
            .getWithdrawableAccounts(context.getSource().getPlayerOrException());
        for (BankAccount account : accounts) {
            builder.suggest(account.getId().toString(), new LiteralMessage(account.getAccountName()));
        }
        return builder.buildFuture();
    };
}
