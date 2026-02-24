package me.samuelh2005.lite_economy.data;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.world.entity.player.Player;

public class Transaction {
    public enum Status {
        PENDING("pending"),
        COMPLETED_SUCCESS("completed_success"),
        COMPLETED_FAILED("completed_failed");

        private final String value;

        Status(String value) {
            this.value = value;
        }

        public static final Codec<Status> CODEC = Codec.STRING.xmap(str -> {
            for (Status status : Status.values()) {
                if (status.value.equals(str)) {
                    return status;
                }
            }
            throw new IllegalArgumentException("Invalid transaction status: " + str);
        }, status -> status.value);
    }

    private final UUID id;
    private final UUID actor;
    private final Optional<UUID> from;
    private final Optional<UUID> to;
    private final BigDecimal amount;
    private final long createdAtEpochMs;
    private Long completedAtEpochMs;
    private Status status;
    private final CompletableFuture<Boolean> completionFuture;
    private final AtomicBoolean completionStarted;

    public Transaction(Player actor, BankAccount from, BankAccount to, BigDecimal amount) {
        this(actor, Optional.of(Objects.requireNonNull(from, "from")), Optional.of(Objects.requireNonNull(to, "to")), amount);
    }

    public Transaction(Player actor, Optional<BankAccount> from, Optional<BankAccount> to, BigDecimal amount) {
        this.id = UUID.randomUUID();
        this.actor = Objects.requireNonNull(actor, "actor").getUUID();
        this.from = Objects.requireNonNull(from, "from").map(BankAccount::getId);
        this.to = Objects.requireNonNull(to, "to").map(BankAccount::getId);
        this.amount = Objects.requireNonNull(amount, "amount");
        this.createdAtEpochMs = System.currentTimeMillis();
        this.completedAtEpochMs = null;
        this.status = Status.PENDING;
        this.completionFuture = new CompletableFuture<>();
        this.completionStarted = new AtomicBoolean(false);
    }

    public Transaction(UUID id, UUID actor, Optional<UUID> from, Optional<UUID> to, BigDecimal amount, long createdAtEpochMs, Optional<Long> completedAtEpochMs, Status status) {
        this.id = Objects.requireNonNull(id, "id");
        this.actor = Objects.requireNonNull(actor, "actor");
        this.from = Objects.requireNonNull(from, "from");
        this.to = Objects.requireNonNull(to, "to");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.createdAtEpochMs = createdAtEpochMs;
        this.completedAtEpochMs = completedAtEpochMs.orElse(null);
        this.status = Objects.requireNonNull(status, "status");
        this.completionFuture = new CompletableFuture<>();
        this.completionStarted = new AtomicBoolean(status != Status.PENDING);
        if (status == Status.COMPLETED_SUCCESS) {
            this.completionFuture.complete(true);
        } else if (status == Status.COMPLETED_FAILED) {
            this.completionFuture.complete(false);
        }
    }

    public static final Codec<Transaction> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(Transaction::getId),
        UUIDUtil.STRING_CODEC.fieldOf("actor").forGetter(Transaction::getActorId),
        UUIDUtil.STRING_CODEC.optionalFieldOf("from").forGetter(Transaction::getFromId),
        UUIDUtil.STRING_CODEC.optionalFieldOf("to").forGetter(Transaction::getToId),
        Codec.STRING.xmap(BigDecimal::new, BigDecimal::toString).fieldOf("amount").forGetter(Transaction::getAmount),
        Codec.LONG.optionalFieldOf("createdAtEpochMs", 0L).forGetter(Transaction::getCreatedAtEpochMs),
        Codec.LONG.optionalFieldOf("completedAtEpochMs").forGetter(Transaction::getCompletedAtEpochMs),
        Status.CODEC.optionalFieldOf("status", Status.PENDING).forGetter(Transaction::getStatus)
    ).apply(instance, Transaction::new));

    public UUID getActorId() {
        return actor;
    }

    public UUID getId() {
        return id;
    }

    public Optional<UUID> getFromId() {
        return from;
    }

    public Optional<UUID> getToId() {
        return to;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public long getCreatedAtEpochMs() {
        return createdAtEpochMs;
    }

    public Optional<Long> getCompletedAtEpochMs() {
        return Optional.ofNullable(completedAtEpochMs);
    }

    public Status getStatus() {
        return status;
    }

    public CompletionStage<Boolean> getCompletionFuture() {
        return completionFuture;
    }

    public boolean beginCompletion() {
        return completionStarted.compareAndSet(false, true);
    }

    public CompletionStage<Boolean> completeWith(boolean result) {
        this.completedAtEpochMs = System.currentTimeMillis();
        this.status = result ? Status.COMPLETED_SUCCESS : Status.COMPLETED_FAILED;
        completionFuture.complete(result);
        return completionFuture;
    }

    /**
     * Returns true if this transaction involves the given account (either as source or destination).
     */
    public boolean involvesAccount(UUID accountId) {
        return getFromId().filter(accountId::equals).isPresent()
            || getToId().filter(accountId::equals).isPresent();
    }

    /**
     * Returns true if this transaction deposits money INTO the given account (account is the destination).
     */
    public boolean isIncoming(UUID accountId) {
        return getToId().filter(accountId::equals).isPresent();
    }

    /**
     * Returns true if this transaction withdraws money FROM the given account (account is the source).
     */
    public boolean isOutgoing(UUID accountId) {
        return getFromId().filter(accountId::equals).isPresent();
    }
}
