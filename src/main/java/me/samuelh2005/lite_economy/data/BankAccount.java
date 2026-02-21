package me.samuelh2005.lite_economy.data;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

public class BankAccount {
    private final UUID id;
    private final AccountOwner owner;

    private String accountName;
    private double balance;

    public static final Codec<BankAccount> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(BankAccount::getId),
        Codec.STRING.fieldOf("accountName").forGetter(BankAccount::getAccountName),
        Codec.DOUBLE.fieldOf("balance").forGetter(BankAccount::getBalance),
        AccountOwner.CODEC.fieldOf("owner").forGetter(BankAccount::getOwner)
    ).apply(instance, BankAccount::new));

    public BankAccount(UUID id, String accountName, double balance, AccountOwner owner) {
        this.id = id;
        this.owner = owner;
        this.accountName = accountName;
        this.balance = balance;
    }

    public UUID getId() {
        return id;
    }

    public AccountOwner getOwner() {
        return owner;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }
}
