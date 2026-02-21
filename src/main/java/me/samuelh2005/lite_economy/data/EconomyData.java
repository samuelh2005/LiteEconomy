package me.samuelh2005.lite_economy.data;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class EconomyData extends SavedData {
    private static EconomyData instance;

    public static final Codec<EconomyData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        BankAccount.CODEC.listOf().fieldOf("bankAccounts").forGetter(EconomyData::getBankAccounts),
        Business.CODEC.listOf().fieldOf("businesses").forGetter(EconomyData::getBusinesses)
    ).apply(instance, EconomyData::new));

    public static final SavedDataType<EconomyData> TYPE = new SavedDataType<EconomyData>(
        "economy_data",
        EconomyData::new,
        EconomyData.CODEC,
        null
    );

    private List<BankAccount> bankAccounts;
    private List<Business> businesses;

    private EconomyData() {
        this.bankAccounts = new ArrayList<>();
        this.businesses = new ArrayList<>();
    }

    private EconomyData(List<BankAccount> bankAccounts, List<Business> businesses) {
        this.bankAccounts = new ArrayList<>(bankAccounts);
        this.businesses = new ArrayList<>(businesses);
    }

    public List<BankAccount> getBankAccounts() {
        return bankAccounts;
    }

    public List<Business> getBusinesses() {
        return businesses;
    }

    public static void init(MinecraftServer server) {
        instance = server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public static EconomyData get() {
        if (instance == null) {
            throw new IllegalStateException("EconomyData has not been initialized yet!");
        }
        return instance;
    }
}
