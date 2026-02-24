package me.samuelh2005.lite_economy.data.storage;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import me.samuelh2005.lite_economy.data.AccountOwner;
import me.samuelh2005.lite_economy.data.BankAccount;
import me.samuelh2005.lite_economy.data.Business;
import me.samuelh2005.lite_economy.data.Transaction;
import net.minecraft.world.entity.player.Player;

public interface DataStorage {
    // == Bank Account Methods ==

    Optional<BankAccount> createBankAccount(String accountName, AccountOwner owner, double initialBalance);
    Optional<BankAccount> createBankAccount(String accountName, AccountOwner owner);
    Optional<BankAccount> createBankAccount(String name, Player owner);
    Optional<BankAccount> createBankAccount(String name, Business business);

    Map<UUID, BankAccount> getBankAccounts();
    List<BankAccount> getBankAccountsByOwner(AccountOwner owner);
    List<BankAccount> getBankAccounts(Player owner);
    List<BankAccount> getBankAccounts(Business business);
    Optional<BankAccount> getBankAccountById(UUID id);

    // == Business Methods ==

    Optional<Business> createBusiness(String name, AccountOwner owner);
    Optional<Business> createBusiness(String name, Player owner);

    Map<UUID, Business> getBusinesses();
    List<Business> getBusinesses(Player player);

    /** Returns all businesses where the player is an OWNER or MANAGER. */
    List<Business> getManageableBusinesses(Player player);

    /** Returns all bank accounts the player can withdraw from (own accounts + manageable business accounts). */
    List<BankAccount> getWithdrawableAccounts(Player player);

    Optional<Business> getBusinessById(UUID id);

    // == Transaction Methods ==

    Map<UUID, Transaction> getTransactions();
    Optional<Transaction> getTransactionById(UUID id);

    // == Save Methods ==

    void save(BankAccount bankAccount);
    void save(Business business);
    void save(Transaction transaction);
}
