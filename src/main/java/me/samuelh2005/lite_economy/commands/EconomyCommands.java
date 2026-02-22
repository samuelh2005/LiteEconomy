package me.samuelh2005.lite_economy.commands;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public final class EconomyCommands {
    private EconomyCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, environment) -> {
            BankCommand.register(dispatcher);
            BusinessCommand.register(dispatcher);
            PayCommand.register(dispatcher);
        });
    }
}
