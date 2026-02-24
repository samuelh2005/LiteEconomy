package me.samuelh2005.lite_economy.data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import me.samuelh2005.lite_economy.commands.arguments.UUIDNameable;
import net.minecraft.core.UUIDUtil;

public class BankAccount implements UUIDNameable {
    private final UUID id;
    private final AccountOwner owner;

    private String accountName;
    private BigDecimal balance;

    private static final Codec<BigDecimal> BIG_DECIMAL_CODEC = Codec.STRING.xmap(str -> {
        try {
            return new BigDecimal(str);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid BigDecimal: " + str, e);
        }
    }, BigDecimal::toString);

    public static final Codec<BankAccount> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(BankAccount::getId),
        Codec.STRING.fieldOf("accountName").forGetter(BankAccount::getAccountName),
        BIG_DECIMAL_CODEC.fieldOf("balance").forGetter(BankAccount::getBalance),
        AccountOwner.CODEC.fieldOf("owner").forGetter(BankAccount::getOwner)
    ).apply(instance, BankAccount::new));

    public BankAccount(UUID id, String accountName, BigDecimal balance, AccountOwner owner) {
        this.id = id;
        this.owner = owner;
        this.accountName = accountName;
        this.balance = balance.setScale(2, RoundingMode.HALF_UP);
    }

    public UUID getId() {
        return id;
    }

    public AccountOwner getOwner() {
        return owner;
    }

    @Override
    public String getDisplayName() {
        return accountName;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance.setScale(2, RoundingMode.HALF_UP);
    }
}
