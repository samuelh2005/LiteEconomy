package me.samuelh2005.lite_economy.data;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.world.entity.player.Player;

public class Transaction {
    private final UUID id;
    private final Player actor;
    private final BankAccount from;
    private final BankAccount to;
    private final BigDecimal amount;
    private final CompletableFuture<Boolean> completionFuture;
    private final AtomicBoolean completionStarted;

    public Transaction(Player actor, BankAccount from, BankAccount to, BigDecimal amount) {
        this.id = UUID.randomUUID();
        this.actor = Objects.requireNonNull(actor, "actor");
        this.from = Objects.requireNonNull(from, "from");
        this.to = Objects.requireNonNull(to, "to");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.completionFuture = new CompletableFuture<>();
        this.completionStarted = new AtomicBoolean(false);
    }

    public Player getActor() {
        return actor;
    }

    public UUID getId() {
        return id;
    }

    public BankAccount getFrom() {
        return from;
    }

    public BankAccount getTo() {
        return to;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public CompletionStage<Boolean> getCompletionFuture() {
        return completionFuture;
    }

    public boolean beginCompletion() {
        return completionStarted.compareAndSet(false, true);
    }

    public CompletionStage<Boolean> completeWith(boolean result) {
        completionFuture.complete(result);
        return completionFuture;
    }
}
