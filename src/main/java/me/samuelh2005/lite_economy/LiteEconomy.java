package me.samuelh2005.lite_economy;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.samuelh2005.lite_economy.commands.EconomyCommands;
import me.samuelh2005.lite_economy.data.AccountOwner;
import me.samuelh2005.lite_economy.data.BankAccount;
import me.samuelh2005.lite_economy.data.Transaction;
import me.samuelh2005.lite_economy.data.storage.DataStorage;
import me.samuelh2005.lite_economy.data.storage.LevelNBTStorage;
import me.samuelh2005.lite_economy.services.TransactionService;

public class LiteEconomy implements ModInitializer {
	public static final String MOD_ID = "lite_economy";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static LiteEconomy INSTANCE;

	private LevelNBTStorage levelNBTStorage;
	private MinecraftServer server;
	private final TransactionService transactionService;

	public LiteEconomy() {
		this.transactionService = new TransactionService(this, this::handleTransactionCompletion);
		INSTANCE = this;
	}

	@Override
	public void onInitialize() {
		EconomyCommands.register(this);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> transactionService.stopProcessor());
        ServerWorldEvents.LOAD.register((MinecraftServer server, ServerLevel world) -> {
			if (world.dimension() != world.getServer().overworld().dimension()) return;
			this.server = server;
			levelNBTStorage = world.getDataStorage().computeIfAbsent(LevelNBTStorage.TYPE);
			transactionService.startProcessor();
			transactionService.loadPendingTransactionsFromStorage();
			LOGGER.info("Initialized EconomyData for world: " + world.dimension().location());
        });
	}

	public MinecraftServer getServer() {
		if (server == null) {
			throw new IllegalStateException("Minecraft server has not been initialized yet!");
		}
		return server;
	}

	public DataStorage getDataStorage() {
		if (levelNBTStorage == null) {
			throw new IllegalStateException("Economy data storage has not been initialized yet!");
		}
		return levelNBTStorage;
	}

	public TransactionService getTransactionService() {
		return transactionService;
	}

	public static LiteEconomy getInstance() {
		return INSTANCE;
	}

	private void handleTransactionCompletion(Transaction transaction, boolean success) {
		Optional<UUID> fromOpt = transaction.getFromId();
		Optional<UUID> toOpt = transaction.getToId();
		if (fromOpt.isEmpty() && toOpt.isEmpty()) {
			return;
		}
		DataStorage dataStorage = getDataStorage();
		if (fromOpt.isPresent()) {
			Optional<BankAccount> fromAccountOpt = dataStorage.getBankAccountById(fromOpt.get());
			if (fromAccountOpt.isPresent()) {
				BankAccount fromAccount = fromAccountOpt.get();
				AccountOwner fromOwner = fromAccount.getOwner();
				AccountOwner.Type fromType = fromOwner.getType();
				if (fromType == AccountOwner.Type.PLAYER) {
					UUID playerId = fromOwner.getId();
					ServerPlayer player = getServer().getPlayerList().getPlayer(playerId);
					if (player != null) {
						if (success) {
							player.sendSystemMessage(Component.literal("Transaction successful: $" + transaction.getAmount() + " withdrawn from '" + fromAccount.getAccountName() + "'."));
						} else {
							player.sendSystemMessage(Component.literal("Transaction failed: Could not withdraw $" + transaction.getAmount() + " from '" + fromAccount.getAccountName() + "'."));
						}
					};
				}
			}
		}
		if (toOpt.isPresent()) {
			Optional<BankAccount> toAccountOpt = dataStorage.getBankAccountById(toOpt.get());
			if (toAccountOpt.isPresent()) {
				BankAccount toAccount = toAccountOpt.get();
				AccountOwner toOwner = toAccount.getOwner();
				AccountOwner.Type toType = toOwner.getType();
				if (toType == AccountOwner.Type.PLAYER) {
					UUID playerId = toOwner.getId();
					ServerPlayer player = getServer().getPlayerList().getPlayer(playerId);
					if (player != null) {
						if (success) {
							player.sendSystemMessage(Component.literal("Transaction successful: $" + transaction.getAmount() + " deposited into '" + toAccount.getAccountName() + "'."));
						} else {
							player.sendSystemMessage(Component.literal("Transaction failed: Could not deposit $" + transaction.getAmount() + " into '" + toAccount.getAccountName() + "'."));
						}
					};
				}
			}
		}
	}
}
