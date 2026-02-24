package me.samuelh2005.lite_economy.data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import me.samuelh2005.lite_economy.commands.arguments.UUIDNameable;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.entity.player.Player;

public class Business implements UUIDNameable {
    private final UUID id;
    private String name;
    private List<BusinessMember> members;

    public static final Codec<Business> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(Business::getId),
        Codec.STRING.fieldOf("name").forGetter(Business::getName),
        BusinessMember.CODEC.listOf().fieldOf("members").forGetter(Business::getMembers)
    ).apply(instance, Business::new));

    public Business(UUID id, String name, List<BusinessMember> members) {
        this.id = id;
        this.name = name;
        this.members = new ArrayList<>(members);
    }

    public UUID getId() {
        return id;
    }

    public List<BusinessMember> getMembers() {
        return members;
    }

    @Override
    public String getDisplayName() {
        return name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns true if the given player is an OWNER or MANAGER of this business,
     * and therefore has permission to perform management actions (withdraw, rename, add/remove members, etc.).
     */
    public boolean isManageableBy(UUID playerId) {
        return members.stream().anyMatch(member ->
            member.getPlayerId().equals(playerId) &&
            (member.getRole() == BusinessMember.Role.OWNER || member.getRole() == BusinessMember.Role.MANAGER));
    }

    /** Returns true if this business has at least one member with the OWNER role. */
    public boolean hasOwner() {
        return members.stream().anyMatch(member -> member.getRole() == BusinessMember.Role.OWNER);
    }

    public static class BusinessMember {
        public static enum Role {
            OWNER("owner"),
            MANAGER("manager"),
            EMPLOYEE("employee");

            private final String value;

            private Role(String value) {
                this.value = value;
            }

            public static final Codec<Role> CODEC = Codec.STRING.xmap(str -> {
                for (Role role : Role.values()) {
                    if (role.value.equals(str.toLowerCase())) {
                        return role;
                    }
                }
                throw new IllegalArgumentException("Invalid BusinessMember.Role: " + str);
            }, Role::name);
        }

        private final UUID playerId;
        private Role role;

        public static final Codec<BusinessMember> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("playerId").forGetter(BusinessMember::getPlayerId),
            Role.CODEC.fieldOf("role").forGetter(BusinessMember::getRole)
        ).apply(instance, BusinessMember::new));

        public static BusinessMember forPlayer(Player player, Role role) {
            return new BusinessMember(player.getUUID(), role);
        }

        public static BusinessMember forPlayer(UUID playerId, Role role) {
            return new BusinessMember(playerId, role);
        }

        private BusinessMember(UUID playerId, Role role) {
            this.playerId = playerId;
            this.role = role;
        }

        public UUID getPlayerId() {
            return playerId;
        }

        public Role getRole() {
            return role;
        }

        public void setRole(Role role) {
            this.role = role;
        }
    }
}
