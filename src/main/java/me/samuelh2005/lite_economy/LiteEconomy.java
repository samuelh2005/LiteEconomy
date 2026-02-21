package me.samuelh2005.lite_economy;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.samuelh2005.lite_economy.data.storage.DataStorage;
import me.samuelh2005.lite_economy.data.storage.LevelNBTStorage;

public class LiteEconomy implements ModInitializer {
	public static final String MOD_ID = "lite_economy";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static LevelNBTStorage levelNBTStorage;
	private static MinecraftServer server;

	@Override
	public void onInitialize() {
        ServerWorldEvents.LOAD.register((MinecraftServer server, ServerLevel world) -> {
			if (world.dimension() != world.getServer().overworld().dimension()) return;
			LiteEconomy.server = server;
			levelNBTStorage = world.getDataStorage().computeIfAbsent(LevelNBTStorage.TYPE);
			TransactionService.loadPendingTransactionsFromStorage();
			LOGGER.info("Initialized EconomyData for world: " + world.dimension().location());
        });
	}

	public static MinecraftServer getServer() {
		if (server == null) {
			throw new IllegalStateException("Minecraft server has not been initialized yet!");
		}
		return server;
	}

	public static DataStorage getDataStorage() {
		if (levelNBTStorage == null) {
			throw new IllegalStateException("Economy data storage has not been initialized yet!");
		}
		return levelNBTStorage;
	}
}
