package com.vnap.network;

import com.vnap.VillagerNewsAddonPort;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

public record DialogueAnimationPayload(UUID entityId, String groupId, int variantIndex, int durationTicks)
	implements CustomPacketPayload {
	public static final Type<DialogueAnimationPayload> TYPE = new Type<>(VillagerNewsAddonPort.id("dialogue_animation"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DialogueAnimationPayload> CODEC = new StreamCodec<>() {
		@Override
		public DialogueAnimationPayload decode(RegistryFriendlyByteBuf buffer) {
			return new DialogueAnimationPayload(
				buffer.readUUID(),
				buffer.readUtf(64),
				buffer.readVarInt(),
				buffer.readVarInt()
			);
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buffer, DialogueAnimationPayload payload) {
			buffer.writeUUID(payload.entityId());
			buffer.writeUtf(payload.groupId(), 64);
			buffer.writeVarInt(payload.variantIndex());
			buffer.writeVarInt(payload.durationTicks());
		}
	};

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
