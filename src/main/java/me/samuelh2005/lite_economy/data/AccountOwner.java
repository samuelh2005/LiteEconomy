package me.samuelh2005.lite_economy.data;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.world.entity.player.Player;

public class AccountOwner {
    public static enum Type {
        PLAYER("player"),
        BUSINESS("business");

        private final String value;

        private Type(String value) {
            this.value = value;
        }

        public static final Codec<Type> CODEC = Codec.STRING.xmap(str -> {
            for (Type type : Type.values()) {
                if (type.value.equals(str)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Invalid AccountOwner.Type: " + str);
        }, Type::name);
    }

    private final Type type;
    private final UUID id;
    private String name;

    public static final Codec<AccountOwner> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Type.CODEC.fieldOf("type").forGetter(AccountOwner::getType),
        UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(AccountOwner::getId),
        Codec.STRING.fieldOf("name").forGetter(AccountOwner::getName)
    ).apply(instance, AccountOwner::new));

    public static AccountOwner forPlayer(Player player) {
        return new AccountOwner(Type.PLAYER, player.getUUID(), player.getPlainTextName());
    }

    private AccountOwner(Type type, UUID id, String name) {
        this.type = type;
        this.id = id;
        this.name = name;
    }

    public Type getType() {
        return type;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
