package me.samuelh2005.lite_economy.data.storage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import me.samuelh2005.lite_economy.data.AccountOwner;
import me.samuelh2005.lite_economy.data.BankAccount;
import me.samuelh2005.lite_economy.data.Business;
import me.samuelh2005.lite_economy.data.Transaction;
import net.minecraft.world.entity.player.Player;

public interface DataStorage {
    // == Bank Accounts Methods ==

    Optional<BankAccount> createBankAccount(String accountName, AccountOwner owner, double initialBalance);

    default Optional<BankAccount> createBankAccount(String accountName, AccountOwner owner) {
        return createBankAccount(accountName, owner, 0);
    }

    default Optional<BankAccount> createBankAccount(String name, Player owner) {
        return createBankAccount(name, AccountOwner.forPlayer(owner));
    }

    default Optional<BankAccount> createBankAccount(String name, Business business) {
        return createBankAccount(name, AccountOwner.forBusiness(business));
    }

    List<BankAccount> getBankAccounts();
    List<BankAccount> getBankAccountsByOwner(AccountOwner owner);

    default List<BankAccount> getBankAccounts(Player owner) {
        return getBankAccountsByOwner(AccountOwner.forPlayer(owner));
    }

    default List<BankAccount> getBankAccounts(Business business) {
        return getBankAccountsByOwner(AccountOwner.forBusiness(business));
    }

    Optional<BankAccount> getBankAccountById(UUID id);

    // == Business Methods ==

    Optional<Business> createBusiness(String name, AccountOwner owner);

    default Optional<Business> createBusiness(String name, Player owner) {
        return createBusiness(name, AccountOwner.forPlayer(owner));
    }

    List<Business> getBusinesses();

    default List<Business> getBusinesses(Player player) {
        return getBusinesses().stream()
            .filter(business -> business.getMembers().stream()
                .anyMatch(member -> member.getPlayerId().equals(player.getUUID())))
            .toList();
    }

    Optional<Business> getBusinessById(UUID id);

    // == Transaction Methods ==

    List<Transaction> getTransactions();
    Optional<Transaction> getTransactionById(UUID id);

    // == Save Methods ==

    void save(BankAccount bankAccount);
    void save(Business business);
    void save(Transaction transaction);
}
