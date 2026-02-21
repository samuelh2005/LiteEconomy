package me.samuelh2005.lite_economy.data.storage;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import me.samuelh2005.lite_economy.data.AccountOwner;
import me.samuelh2005.lite_economy.data.BankAccount;
import me.samuelh2005.lite_economy.data.Business;
import me.samuelh2005.lite_economy.data.Transaction;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class LevelNBTStorage extends SavedData implements DataStorage {
    public static final Codec<LevelNBTStorage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        BankAccount.CODEC.listOf().optionalFieldOf("bankAccounts", List.of()).forGetter(LevelNBTStorage::getBankAccounts),
        Business.CODEC.listOf().optionalFieldOf("businesses", List.of()).forGetter(LevelNBTStorage::getBusinesses),
        Transaction.CODEC.listOf().optionalFieldOf("transactions", List.of()).forGetter(LevelNBTStorage::getTransactions)
    ).apply(instance, LevelNBTStorage::new));

    public static final SavedDataType<LevelNBTStorage> TYPE = new SavedDataType<LevelNBTStorage>(
        "economy_data",
        LevelNBTStorage::new,
        LevelNBTStorage.CODEC,
        null
    );

    private List<BankAccount> bankAccounts;
    private List<Business> businesses;
    private List<Transaction> transactions;

    private LevelNBTStorage() {
        this.bankAccounts = new ArrayList<>();
        this.businesses = new ArrayList<>();
        this.transactions = new ArrayList<>();
    }

    private LevelNBTStorage(List<BankAccount> bankAccounts, List<Business> businesses, List<Transaction> transactions) {
        this.bankAccounts = new ArrayList<>(bankAccounts);
        this.businesses = new ArrayList<>(businesses);
        this.transactions = new ArrayList<>(transactions);
        sortTransactionsByTime();
    }

    public List<BankAccount> getBankAccounts() {
        return bankAccounts;
    }

    @Override
    public List<BankAccount> getBankAccountsByOwner(AccountOwner owner) {
        return bankAccounts.stream()
            .filter(account -> {
                AccountOwner accountOwner = account.getOwner();
                return accountOwner.getType() == owner.getType() && accountOwner.getId().equals(owner.getId());
            })
            .toList();
    }

    @Override
    public Optional<BankAccount> getBankAccountById(UUID id) {
        return findById(bankAccounts, BankAccount::getId, id);
    }

    @Override
    public Optional<BankAccount> createBankAccount(String accountName, AccountOwner owner, double initialBalance) {
        if (accountName == null || accountName.isBlank() || owner == null || initialBalance < 0) {
            return Optional.empty();
        }
        BankAccount account = new BankAccount(
            UUID.randomUUID(),
            accountName,
            BigDecimal.valueOf(initialBalance),
            owner
        );
        save(account);
        return Optional.of(account);
    }

    public List<Business> getBusinesses() {
        return businesses;
    }

    @Override
    public Optional<Business> createBusiness(String name, AccountOwner owner) {
        if (name == null || name.isBlank() || owner == null || owner.getType() != AccountOwner.Type.PLAYER) {
            return Optional.empty();
        }
        Business business = new Business(
            UUID.randomUUID(),
            name,
            List.of(Business.BusinessMember.forPlayer(owner.getId(), Business.BusinessMember.Role.OWNER))
        );
        save(business);
        return Optional.of(business);
    }

    @Override
    public Optional<Business> getBusinessById(UUID id) {
        return findById(businesses, Business::getId, id);
    }

    public List<Transaction> getTransactions() {
        return transactions;
    }

    @Override
    public Optional<Transaction> getTransactionById(UUID id) {
        return findById(transactions, Transaction::getId, id);
    }

    @Override
    public void save(BankAccount bankAccount) {
        Objects.requireNonNull(bankAccount, "bankAccount");
        upsertById(bankAccounts, BankAccount::getId, bankAccount);
        setDirty();
    }

    @Override
    public void save(Business business) {
        Objects.requireNonNull(business, "business");
        upsertById(businesses, Business::getId, business);
        setDirty();
    }

    @Override
    public void save(Transaction transaction) {
        Objects.requireNonNull(transaction, "transaction");
        upsertById(transactions, Transaction::getId, transaction);
        sortTransactionsByTime();
        setDirty();
    }

    public void sortTransactionsByTime() {
        transactions.sort(Comparator
            .comparingLong(Transaction::getCreatedAtEpochMs)
            .thenComparing(transaction -> transaction.getCompletedAtEpochMs().orElse(Long.MAX_VALUE))
            .thenComparing(Transaction::getId));
    }

    private static <T> Optional<T> findById(List<T> items, Function<T, UUID> idExtractor, UUID id) {
        return items.stream()
            .filter(item -> idExtractor.apply(item).equals(id))
            .findFirst();
    }

    private static <T> void upsertById(List<T> items, Function<T, UUID> idExtractor, T value) {
        UUID id = idExtractor.apply(value);
        for (int i = 0; i < items.size(); i++) {
            if (idExtractor.apply(items.get(i)).equals(id)) {
                items.set(i, value);
                return;
            }
        }
        items.add(value);
    }
}
