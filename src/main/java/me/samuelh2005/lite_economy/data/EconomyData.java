package me.samuelh2005.lite_economy.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class EconomyData extends SavedData {
    private static EconomyData instance;

    public static final Codec<EconomyData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        BankAccount.CODEC.listOf().optionalFieldOf("bankAccounts", List.of()).forGetter(EconomyData::getBankAccounts),
        Business.CODEC.listOf().optionalFieldOf("businesses", List.of()).forGetter(EconomyData::getBusinesses),
        Transaction.CODEC.listOf().optionalFieldOf("transactions", List.of()).forGetter(EconomyData::getTransactions)
    ).apply(instance, EconomyData::new));

    public static final SavedDataType<EconomyData> TYPE = new SavedDataType<EconomyData>(
        "economy_data",
        EconomyData::new,
        EconomyData.CODEC,
        null
    );

    private List<BankAccount> bankAccounts;
    private List<Business> businesses;
    private List<Transaction> transactions;

    private EconomyData() {
        this.bankAccounts = new ArrayList<>();
        this.businesses = new ArrayList<>();
        this.transactions = new ArrayList<>();
    }

    private EconomyData(List<BankAccount> bankAccounts, List<Business> businesses, List<Transaction> transactions) {
        this.bankAccounts = new ArrayList<>(bankAccounts);
        this.businesses = new ArrayList<>(businesses);
        this.transactions = new ArrayList<>(transactions);
        sortTransactionsByTime();
    }

    public List<BankAccount> getBankAccounts() {
        return bankAccounts;
    }

    public List<Business> getBusinesses() {
        return businesses;
    }

    public List<Transaction> getTransactions() {
        return transactions;
    }

    public void sortTransactionsByTime() {
        transactions.sort(Comparator
            .comparingLong(Transaction::getCreatedAtEpochMs)
            .thenComparing(transaction -> transaction.getCompletedAtEpochMs().orElse(Long.MAX_VALUE))
            .thenComparing(Transaction::getId));
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
