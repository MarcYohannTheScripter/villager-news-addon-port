package com.vnap.network;

import com.vnap.config.VillagerNewsSettings;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.level.ServerPlayer;

public final class VillagerNewsSettingsNetwork {
	private VillagerNewsSettingsNetwork() {
	}

	public static void register() {
		ServerPlayNetworking.registerGlobalReceiver(VillagerNewsSettingsPayload.TYPE, (payload, context) -> {
			if (!canEdit(context.player())) {
				send(context.player());
				return;
			}
			VillagerNewsSettings.update(payload.chattiness(), payload.rareVoicelines(), payload.spawnSpecialVillagers());
			send(context.player());
		});
		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> send(listener.getPlayer()));
	}

	public static void send(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, VillagerNewsSettingsPayload.TYPE)) {
			ServerPlayNetworking.send(player, new VillagerNewsSettingsPayload(
				VillagerNewsSettings.chattiness(),
				VillagerNewsSettings.rareVoicelines(),
				VillagerNewsSettings.spawnSpecialVillagers(),
				canEdit(player)
			));
		}
	}

	private static boolean canEdit(ServerPlayer player) {
		return player.level().getServer().isSingleplayerOwner(player.nameAndId())
			|| player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}
}
