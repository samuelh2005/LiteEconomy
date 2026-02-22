package me.samuelh2005.lite_economy.data.storage;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import me.samuelh2005.lite_economy.data.AccountOwner;
import me.samuelh2005.lite_economy.data.BankAccount;
import me.samuelh2005.lite_economy.data.Business;
import me.samuelh2005.lite_economy.data.Transaction;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.core.UUIDUtil;

public class LevelNBTStorage extends SavedData implements DataStorage {
public static final Codec<LevelNBTStorage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
    Codec.unboundedMap(UUIDUtil.STRING_CODEC, BankAccount.CODEC)
        .optionalFieldOf("bankAccounts", Map.of())
        .forGetter(LevelNBTStorage::getBankAccounts),

    Codec.unboundedMap(UUIDUtil.STRING_CODEC, Business.CODEC)
        .optionalFieldOf("businesses", Map.of())
        .forGetter(LevelNBTStorage::getBusinesses),

    Codec.unboundedMap(UUIDUtil.STRING_CODEC, Transaction.CODEC)
        .optionalFieldOf("transactions", Map.of())
        .forGetter(LevelNBTStorage::getTransactions)

).apply(instance, LevelNBTStorage::new));

    public static final SavedDataType<LevelNBTStorage> TYPE = new SavedDataType<LevelNBTStorage>(
        "economy_data",
        LevelNBTStorage::new,
        LevelNBTStorage.CODEC,
        null
    );

    private final Map<UUID, BankAccount> bankAccounts;
    private final Map<UUID, Business> businesses;
    private final Map<UUID, Transaction> transactions;

    private LevelNBTStorage() {
        this.bankAccounts = new HashMap<>();
        this.businesses = new HashMap<>();
        this.transactions = new HashMap<>();
    }

    private LevelNBTStorage(
        Map<UUID, BankAccount> bankAccounts,
        Map<UUID, Business> businesses,
        Map<UUID, Transaction> transactions
    ) {
        this.bankAccounts = new HashMap<>(bankAccounts);
        this.businesses = new HashMap<>(businesses);
        this.transactions = new HashMap<>(transactions);
    }

    public Map<UUID, BankAccount> getBankAccounts() {
        return bankAccounts;
    }

    @Override
    public List<BankAccount> getBankAccountsByOwner(AccountOwner owner) {
        return bankAccounts.values().stream()
            .filter(account -> {
                AccountOwner accountOwner = account.getOwner();
                return accountOwner.getType() == owner.getType() && accountOwner.getId().equals(owner.getId());
            })
            .toList();
    }

    @Override
    public Optional<BankAccount> getBankAccountById(UUID id) {
        return Optional.ofNullable(bankAccounts.get(id));
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

    public Map<UUID, Business> getBusinesses() {
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
        return Optional.ofNullable(businesses.get(id));
    }

    public Map<UUID, Transaction> getTransactions() {
        return transactions;
    }

    @Override
    public Optional<Transaction> getTransactionById(UUID id) {
        return Optional.ofNullable(transactions.get(id));
    }

    @Override
    public void save(BankAccount bankAccount) {
        Objects.requireNonNull(bankAccount, "bankAccount");
        setDirty();
    }

    @Override
    public void save(Business business) {
        Objects.requireNonNull(business, "business");
        setDirty();
    }

    @Override
    public void save(Transaction transaction) {
        Objects.requireNonNull(transaction, "transaction");
        setDirty();
    }
}
