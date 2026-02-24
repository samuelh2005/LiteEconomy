package me.samuelh2005.lite_economy.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import me.samuelh2005.lite_economy.LiteEconomy;
import me.samuelh2005.lite_economy.commands.arguments.NamedUUIDArgumentType;
import me.samuelh2005.lite_economy.data.AccountOwner;
import me.samuelh2005.lite_economy.data.Business;
import me.samuelh2005.lite_economy.data.Business.BusinessMember;
import me.samuelh2005.lite_economy.data.Business.BusinessMember.Role;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class BusinessCommand {
    private static final String ERR_BUSINESS_NOT_FOUND_MANAGEABLE = "Business not found or you do not have permission.";

    private BusinessCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("business")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .then(Commands.literal("list").executes(BusinessCommand::list))
                .then(Commands.literal("create")
                    .then(Commands.argument("name", StringArgumentType.string())
                        .executes(BusinessCommand::create)))
                .then(Commands.literal("info")
                    .then(Commands.argument("business", NamedUUIDArgumentType.namedUUID(ctx -> LiteEconomy.getInstance().getDataStorage().getBusinesses(getPlayer(ctx))))
                        .executes(BusinessCommand::info)))
                .then(Commands.literal("rename")
                    .then(Commands.argument("business", NamedUUIDArgumentType.namedUUID(ctx -> LiteEconomy.getInstance().getDataStorage().getManageableBusinesses(getPlayer(ctx))))
                        .then(Commands.argument("new_name", StringArgumentType.string())
                            .executes(BusinessCommand::rename))))
                .then(Commands.literal("member")
                    .then(Commands.literal("add")
                        .then(Commands.argument("business", NamedUUIDArgumentType.namedUUID(ctx -> LiteEconomy.getInstance().getDataStorage().getManageableBusinesses(getPlayer(ctx))))
                            .then(Commands.argument("player", EntityArgument.player())
                                .executes(BusinessCommand::addMember))))
                    .then(Commands.literal("remove")
                        .then(Commands.argument("business", NamedUUIDArgumentType.namedUUID(ctx -> LiteEconomy.getInstance().getDataStorage().getManageableBusinesses(getPlayer(ctx))))
                            .then(Commands.argument("player", EntityArgument.player())
                                .executes(BusinessCommand::removeMember))))
                    .then(Commands.literal("role")
                        .then(Commands.literal("set")
                            .then(Commands.argument("business", NamedUUIDArgumentType.namedUUID(ctx -> LiteEconomy.getInstance().getDataStorage().getManageableBusinesses(getPlayer(ctx))))
                                .then(Commands.argument("player", EntityArgument.player())
                                    .then(Commands.literal("owner").executes(context -> setRole(context, Role.OWNER)))
                                    .then(Commands.literal("manager").executes(context -> setRole(context, Role.MANAGER)))
                                    .then(Commands.literal("employee").executes(context -> setRole(context, Role.EMPLOYEE))))))))
        );
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        List<Business> businesses = LiteEconomy.getInstance().getDataStorage().getBusinesses(player);
        if (businesses.isEmpty()) {
            context.getSource().sendFailure(Component.literal("You are not in any businesses."));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("Your businesses:"), false);
        for (Business business : businesses) {
            Role role = business.getMembers().stream()
                .filter(member -> member.getPlayerId().equals(player.getUUID()))
                .map(BusinessMember::getRole)
                .findFirst()
                .orElse(Role.EMPLOYEE);
            context.getSource().sendSuccess(
                () -> Component.literal("- " + business.getName() + " | members=" + business.getMembers().size() + " | role=" + role.name().toLowerCase()),
                false
            );
        }
        return businesses.size();
    }

    private static int create(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        String name = StringArgumentType.getString(context, "name").trim();
        if (name.isBlank()) {
            context.getSource().sendFailure(Component.literal("Business name cannot be blank."));
            return 0;
        }

        Optional<Business> created = LiteEconomy.getInstance().getDataStorage().createBusiness(name, AccountOwner.forPlayer(player));
        if (created.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Could not create business."));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("Created business '" + created.get().getName() + "'."), true);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        UUID businessId = NamedUUIDArgumentType.getUUID(context, "business");
        Optional<Business> business = LiteEconomy.getInstance().getDataStorage().getBusinessById(businessId);
        if (business.isEmpty() || business.get().getMembers().stream().noneMatch(m -> m.getPlayerId().equals(player.getUUID()))) {
            context.getSource().sendFailure(Component.literal("Business not found or you are not a member."));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("Business: " + business.get().getName()), false);
        for (BusinessMember member : business.get().getMembers()) {
            String playerName = Optional.ofNullable(LiteEconomy.getInstance().getServer().getPlayerList().getPlayer(member.getPlayerId()))
                .map(found -> found.getName().getString())
                .orElse(member.getPlayerId().toString());
            context.getSource().sendSuccess(() -> Component.literal("- " + playerName + " : " + member.getRole().name().toLowerCase()), false);
        }
        return 1;
    }

    private static int rename(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        UUID businessId = NamedUUIDArgumentType.getUUID(context, "business");
        String newName = StringArgumentType.getString(context, "new_name").trim();
        if (newName.isBlank()) {
            context.getSource().sendFailure(Component.literal("New business name cannot be blank."));
            return 0;
        }

        Optional<Business> business = LiteEconomy.getInstance().getDataStorage().getBusinessById(businessId);
        if (business.isEmpty() || !business.get().isManageableBy(player.getUUID())) {
            context.getSource().sendFailure(Component.literal("Business not found or you do not have permission to rename it."));
            return 0;
        }

        business.get().setName(newName);
        LiteEconomy.getInstance().getDataStorage().save(business.get());
        context.getSource().sendSuccess(() -> Component.literal("Business renamed to '" + newName + "'."), true);
        return 1;
    }

    /**
     * Validates that the business exists and the player can manage it. Returns empty if validation fails
     * and sends failure message.
     */
    private static Optional<Business> validateManageableBusiness(CommandContext<CommandSourceStack> context, ServerPlayer actor, UUID businessId) {
        Optional<Business> business = LiteEconomy.getInstance().getDataStorage().getBusinessById(businessId);
        if (business.isEmpty() || !business.get().isManageableBy(actor.getUUID())) {
            context.getSource().sendFailure(Component.literal(ERR_BUSINESS_NOT_FOUND_MANAGEABLE));
            return Optional.empty();
        }
        return business;
    }

    private static int addMember(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer actor = getPlayer(context);
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        UUID businessId = NamedUUIDArgumentType.getUUID(context, "business");

        Optional<Business> business = validateManageableBusiness(context, actor, businessId);
        if (business.isEmpty()) {
            return 0;
        }
        boolean exists = business.get().getMembers().stream().anyMatch(member -> member.getPlayerId().equals(target.getUUID()));
        if (exists) {
            context.getSource().sendFailure(Component.literal("Player is already in that business."));
            return 0;
        }

        List<BusinessMember> updatedMembers = new ArrayList<>(business.get().getMembers());
        updatedMembers.add(BusinessMember.forPlayer(target, Role.EMPLOYEE));
        replaceMembers(business.get(), updatedMembers);
        LiteEconomy.getInstance().getDataStorage().save(business.get());
        context.getSource().sendSuccess(() -> Component.literal("Added " + target.getName().getString() + " to " + business.get().getName() + "."), true);
        return 1;
    }

    private static int removeMember(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer actor = getPlayer(context);
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        UUID businessId = NamedUUIDArgumentType.getUUID(context, "business");

        Optional<Business> business = validateManageableBusiness(context, actor, businessId);
        if (business.isEmpty()) {
            return 0;
        }

        List<BusinessMember> updatedMembers = new ArrayList<>(business.get().getMembers());
        boolean removed = updatedMembers.removeIf(member -> member.getPlayerId().equals(target.getUUID()));
        if (!removed) {
            context.getSource().sendFailure(Component.literal("That player is not a member of this business."));
            return 0;
        }
        if (updatedMembers.stream().noneMatch(member -> member.getRole() == Role.OWNER)) {
            context.getSource().sendFailure(Component.literal("Cannot remove the only owner."));
            return 0;
        }

        replaceMembers(business.get(), updatedMembers);
        LiteEconomy.getInstance().getDataStorage().save(business.get());
        context.getSource().sendSuccess(() -> Component.literal("Removed " + target.getName().getString() + " from " + business.get().getName() + "."), true);
        return 1;
    }

    private static int setRole(CommandContext<CommandSourceStack> context, Role role) throws CommandSyntaxException {
        ServerPlayer actor = getPlayer(context);
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        UUID businessId = NamedUUIDArgumentType.getUUID(context, "business");

        Optional<Business> business = validateManageableBusiness(context, actor, businessId);
        if (business.isEmpty()) {
            return 0;
        }

        Optional<BusinessMember> member = business.get().getMembers().stream()
            .filter(existing -> existing.getPlayerId().equals(target.getUUID()))
            .findFirst();
        if (member.isEmpty()) {
            context.getSource().sendFailure(Component.literal("That player is not in this business."));
            return 0;
        }

        member.get().setRole(role);
        if (!business.get().hasOwner()) {
            context.getSource().sendFailure(Component.literal("Business must always have at least one owner."));
            return 0;
        }
        LiteEconomy.getInstance().getDataStorage().save(business.get());
        context.getSource().sendSuccess(
            () -> Component.literal("Set role for " + target.getName().getString() + " to " + role.name().toLowerCase() + "."),
            true
        );
        return 1;
    }

    private static void replaceMembers(Business business, List<BusinessMember> members) {
        business.getMembers().clear();
        business.getMembers().addAll(members);
    }

    private static ServerPlayer getPlayer(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            throw new IllegalStateException("Player-only command executed by non-player source.");
        }
        return player;
    }
}
