package me.samuelh2005.lite_economy.commands;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import me.samuelh2005.lite_economy.LiteEconomy;

public final class EconomyCommands {
    private EconomyCommands() {
    }

    public static void register(LiteEconomy main) {
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, environment) -> {
            BankCommand.register(dispatcher);
            BusinessCommand.register(dispatcher);
            PayCommand.register(dispatcher);
        });
    }
}
