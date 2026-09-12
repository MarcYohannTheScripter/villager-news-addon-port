package com.vnap.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class DialogueAnimationNetwork {
	private static final double TRACKING_RANGE_SQUARED = 96.0 * 96.0;

	private DialogueAnimationNetwork() {
	}

	public static void send(ServerLevel level, LivingEntity speaker, String groupId, int variantIndex, int durationTicks) {
		DialogueAnimationPayload payload = new DialogueAnimationPayload(speaker.getUUID(), groupId, variantIndex, durationTicks);
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(speaker) <= TRACKING_RANGE_SQUARED
				&& ServerPlayNetworking.canSend(player, DialogueAnimationPayload.TYPE)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}
}
