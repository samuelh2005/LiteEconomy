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
import me.samuelh2005.lite_economy.data.AccountOwner;
import me.samuelh2005.lite_economy.data.Business;
import me.samuelh2005.lite_economy.data.Business.BusinessMember;
import me.samuelh2005.lite_economy.data.Business.BusinessMember.Role;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class BusinessCommand {
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
                    .then(Commands.argument("name", StringArgumentType.string())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(CommandSuggestionUtil.quoteAll(getMemberBusinessNames(getPlayer(context))), builder))
                        .executes(BusinessCommand::info)))
                .then(Commands.literal("rename")
                    .then(Commands.argument("business", StringArgumentType.string())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(CommandSuggestionUtil.quoteAll(getMemberBusinessNames(getPlayer(context))), builder))
                        .then(Commands.argument("new_name", StringArgumentType.string())
                            .executes(BusinessCommand::rename))))
                .then(Commands.literal("member")
                    .then(Commands.literal("add")
                        .then(Commands.argument("business", StringArgumentType.string())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(CommandSuggestionUtil.quoteAll(getMemberBusinessNames(getPlayer(context))), builder))
                            .then(Commands.argument("player", EntityArgument.player())
                                .executes(BusinessCommand::addMember))))
                    .then(Commands.literal("remove")
                        .then(Commands.argument("business", StringArgumentType.string())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(CommandSuggestionUtil.quoteAll(getMemberBusinessNames(getPlayer(context))), builder))
                            .then(Commands.argument("player", EntityArgument.player())
                                .executes(BusinessCommand::removeMember))))
                    .then(Commands.literal("role")
                        .then(Commands.literal("set")
                            .then(Commands.argument("business", StringArgumentType.string())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(CommandSuggestionUtil.quoteAll(getMemberBusinessNames(getPlayer(context))), builder))
                                .then(Commands.argument("player", EntityArgument.player())
                                    .then(Commands.literal("owner").executes(context -> setRole(context, Role.OWNER)))
                                    .then(Commands.literal("manager").executes(context -> setRole(context, Role.MANAGER)))
                                    .then(Commands.literal("employee").executes(context -> setRole(context, Role.EMPLOYEE))))))))
        );
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        List<Business> businesses = LiteEconomy.getDataStorage().getBusinesses(player);
        if (businesses.isEmpty()) {
            context.getSource().sendFailure(Component.literal("You are not in any businesses."));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("Your businesses:"), false);
        for (Business business : businesses) {
            Role role = getMemberRole(business, player.getUUID()).orElse(Role.EMPLOYEE);
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

        Optional<Business> created = LiteEconomy.getDataStorage().createBusiness(name, AccountOwner.forPlayer(player));
        if (created.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Could not create business."));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("Created business '" + created.get().getName() + "'."), true);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        String name = StringArgumentType.getString(context, "name");

        Optional<Business> business = resolveMemberBusiness(player, name);
        if (business.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Business not found or you are not a member: " + name));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("Business: " + business.get().getName()), false);
        for (BusinessMember member : business.get().getMembers()) {
            String playerName = Optional.ofNullable(LiteEconomy.getServer().getPlayerList().getPlayer(member.getPlayerId()))
                .map(found -> found.getName().getString())
                .orElse(member.getPlayerId().toString());
            context.getSource().sendSuccess(() -> Component.literal("- " + playerName + " : " + member.getRole().name().toLowerCase()), false);
        }
        return 1;
    }

    private static int rename(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        String name = StringArgumentType.getString(context, "business");
        String newName = StringArgumentType.getString(context, "new_name").trim();
        if (newName.isBlank()) {
            context.getSource().sendFailure(Component.literal("New business name cannot be blank."));
            return 0;
        }

        Optional<Business> business = resolveMemberBusiness(player, name);
        if (business.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Business not found: " + name));
            return 0;
        }
        if (!isOwnerOrManager(business.get(), player.getUUID())) {
            context.getSource().sendFailure(Component.literal("You do not have permission to rename this business."));
            return 0;
        }

        business.get().setName(newName);
        LiteEconomy.getDataStorage().save(business.get());
        context.getSource().sendSuccess(() -> Component.literal("Business renamed to '" + newName + "'."), true);
        return 1;
    }

    private static int addMember(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer actor = getPlayer(context);
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        String name = StringArgumentType.getString(context, "business");

        Optional<Business> business = resolveMemberBusiness(actor, name);
        if (business.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Business not found: " + name));
            return 0;
        }
        if (!isOwnerOrManager(business.get(), actor.getUUID())) {
            context.getSource().sendFailure(Component.literal("You do not have permission to add members."));
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
        LiteEconomy.getDataStorage().save(business.get());
        context.getSource().sendSuccess(() -> Component.literal("Added " + target.getName().getString() + " to " + business.get().getName() + "."), true);
        return 1;
    }

    private static int removeMember(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer actor = getPlayer(context);
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        String name = StringArgumentType.getString(context, "business");

        Optional<Business> business = resolveMemberBusiness(actor, name);
        if (business.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Business not found: " + name));
            return 0;
        }
        if (!isOwnerOrManager(business.get(), actor.getUUID())) {
            context.getSource().sendFailure(Component.literal("You do not have permission to remove members."));
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
        LiteEconomy.getDataStorage().save(business.get());
        context.getSource().sendSuccess(() -> Component.literal("Removed " + target.getName().getString() + " from " + business.get().getName() + "."), true);
        return 1;
    }

    private static int setRole(CommandContext<CommandSourceStack> context, Role role) throws CommandSyntaxException {
        ServerPlayer actor = getPlayer(context);
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        String name = StringArgumentType.getString(context, "business");

        Optional<Business> business = resolveMemberBusiness(actor, name);
        if (business.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Business not found: " + name));
            return 0;
        }
        if (!isOwnerOrManager(business.get(), actor.getUUID())) {
            context.getSource().sendFailure(Component.literal("You do not have permission to set roles."));
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
        if (business.get().getMembers().stream().noneMatch(existing -> existing.getRole() == Role.OWNER)) {
            context.getSource().sendFailure(Component.literal("Business must always have at least one owner."));
            return 0;
        }
        LiteEconomy.getDataStorage().save(business.get());
        context.getSource().sendSuccess(
            () -> Component.literal("Set role for " + target.getName().getString() + " to " + role.name().toLowerCase() + "."),
            true
        );
        return 1;
    }

    private static Optional<Business> resolveMemberBusiness(ServerPlayer player, String businessName) {
        String normalized = businessName.trim();
        return LiteEconomy.getDataStorage().getBusinesses(player).stream()
            .filter(business -> business.getName().equalsIgnoreCase(normalized))
            .findFirst();
    }

    private static List<String> getMemberBusinessNames(ServerPlayer player) {
        return LiteEconomy.getDataStorage().getBusinesses(player).stream()
            .map(Business::getName)
            .toList();
    }

    private static Optional<Role> getMemberRole(Business business, UUID playerId) {
        return business.getMembers().stream()
            .filter(member -> member.getPlayerId().equals(playerId))
            .map(BusinessMember::getRole)
            .findFirst();
    }

    private static boolean isOwnerOrManager(Business business, UUID playerId) {
        return business.getMembers().stream()
            .anyMatch(member -> member.getPlayerId().equals(playerId) && (member.getRole() == Role.OWNER || member.getRole() == Role.MANAGER));
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
