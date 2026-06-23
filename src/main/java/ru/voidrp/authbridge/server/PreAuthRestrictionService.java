package ru.voidrp.authbridge.server;

import com.mojang.brigadier.ParseResults;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import ru.voidrp.authbridge.bootstrap.ModBootstrap;

public final class PreAuthRestrictionService {

    private static final Set<String> ALLOWED_COMMANDS = Set.of("login");

    private static final double MAX_ALLOWED_DRIFT_SQR = 0.01D;

    private static final Component CHAT_DENIED = Component.literal(
            "Чат будет доступен после авторизации."
    );
    private static final Component COMMAND_DENIED = Component.literal(
            "Эта команда будет доступна после авторизации."
    );
    private static final Component INTERACTION_DENIED = Component.literal(
            "Сначала завершите авторизацию."
    );
    private static final Component LEGACY_HINT = Component.literal(
            "Для входа используйте /login <password>"
    );
    private static final Component LAUNCHER_HINT = Component.literal(
            "Ожидаем подтверждение входа через лаунчер VoidRP..."
    );

    private PreAuthRestrictionService() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (player.level().isClientSide()) {
            return;
        }

        var restriction = ModBootstrap.get().stateStore().findRestriction(player.getUUID());
        if (restriction.isEmpty()) {
            return;
        }

        enforceLockedState(player, restriction.get());
    }

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        if (!isRestricted(player)) {
            return;
        }

        event.setCanceled(true);
        deny(player, CHAT_DENIED);
    }

    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        ParseResults<CommandSourceStack> parseResults = event.getParseResults();

        if (!(parseResults.getContext().getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (!isRestricted(player)) {
            return;
        }

        String rootCommand = extractRootCommand(parseResults.getReader().getString());
        if (ALLOWED_COMMANDS.contains(rootCommand)) {
            return;
        }

        event.setCanceled(true);
        deny(player, COMMAND_DENIED);
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        ServerPlayer player = asRestrictedServerPlayer(event.getEntity());
        if (player == null) {
            return;
        }

        event.setCanceled(true);
        deny(player, INTERACTION_DENIED);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ServerPlayer player = asRestrictedServerPlayer(event.getEntity());
        if (player == null) {
            return;
        }

        event.setCanceled(true);
        deny(player, INTERACTION_DENIED);
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        ServerPlayer player = asRestrictedServerPlayer(event.getEntity());
        if (player == null) {
            return;
        }

        event.setCanceled(true);
        deny(player, INTERACTION_DENIED);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        ServerPlayer player = asRestrictedServerPlayer(event.getEntity());
        if (player == null) {
            return;
        }

        event.setCanceled(true);
        deny(player, INTERACTION_DENIED);
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        ServerPlayer player = asRestrictedServerPlayer(event.getEntity());
        if (player == null) {
            return;
        }

        event.setCanceled(true);
        deny(player, INTERACTION_DENIED);
    }

    @SubscribeEvent
    public static void onUseItemOnBlock(UseItemOnBlockEvent event) {
        if (!(event.getUseOnContext().getPlayer() instanceof ServerPlayer player)) {
            return;
        }

        if (player.level().isClientSide()) {
            return;
        }

        if (!isRestricted(player)) {
            return;
        }

        event.setCanceled(true);
        deny(player, INTERACTION_DENIED);
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        ServerPlayer player = asRestrictedServerPlayer(event.getEntity());
        if (player == null) {
            return;
        }

        event.setCanceled(true);
        deny(player, INTERACTION_DENIED);
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (player.level().isClientSide()) {
            return;
        }

        if (!isRestricted(player)) {
            return;
        }

        event.setCanceled(true);
        player.fallDistance = 0.0F;
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        ServerPlayer player = asRestrictedServerPlayer(event.getEntity());
        if (player == null) {
            return;
        }

        event.setCanceled(true);
        event.setNewSpeed(0.0F);
        deny(player, INTERACTION_DENIED);
    }

    @SubscribeEvent
    public static void onHarvestCheck(PlayerEvent.HarvestCheck event) {
        ServerPlayer player = asRestrictedServerPlayer(event.getEntity());
        if (player == null) {
            return;
        }

        event.setCanHarvest(false);
    }

    private static void enforceLockedState(
            ServerPlayer player,
            AuthenticationStateStore.PendingPlayerRecord restriction
    ) {
        player.stopUsingItem();
        player.setSprinting(false);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;

        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }

        double dx = player.getX() - restriction.anchorX();
        double dy = player.getY() - restriction.anchorY();
        double dz = player.getZ() - restriction.anchorZ();

        if ((dx * dx) + (dy * dy) + (dz * dz) > MAX_ALLOWED_DRIFT_SQR) {
            player.teleportTo(
                    restriction.anchorX(),
                    restriction.anchorY(),
                    restriction.anchorZ()
            );
        }

        if (player.tickCount % 40 == 0) {
            if (ModBootstrap.get().stateStore().isLegacyPending(player.getUUID())) {
                player.displayClientMessage(LEGACY_HINT, true);
            } else {
                player.displayClientMessage(LAUNCHER_HINT, true);
            }
        }
    }

    private static boolean isRestricted(ServerPlayer player) {
        return ModBootstrap.get().stateStore().isRestricted(player.getUUID());
    }

    private static ServerPlayer asRestrictedServerPlayer(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return null;
        }

        if (serverPlayer.level().isClientSide()) {
            return null;
        }

        return isRestricted(serverPlayer) ? serverPlayer : null;
    }

    private static void deny(ServerPlayer player, Component message) {
        if (player.tickCount % 10 == 0) {
            player.sendSystemMessage(message);
        }
    }

    private static String extractRootCommand(String rawCommand) {
        if (rawCommand == null) {
            return "";
        }

        String normalized = rawCommand.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        int spaceIndex = normalized.indexOf(' ');
        if (spaceIndex >= 0) {
            normalized = normalized.substring(0, spaceIndex);
        }

        return normalized.trim().toLowerCase();
    }
}
