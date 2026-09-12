package com.vnap.network;

import com.vnap.VillagerNewsAddonPort;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerNewsSettingsPayload(int chattiness, int rareVoicelines, boolean spawnSpecialVillagers)
	implements CustomPacketPayload {
	public static final Type<VillagerNewsSettingsPayload> TYPE = new Type<>(VillagerNewsAddonPort.id("settings"));
	public static final StreamCodec<RegistryFriendlyByteBuf, VillagerNewsSettingsPayload> CODEC = new StreamCodec<>() {
		@Override
		public VillagerNewsSettingsPayload decode(RegistryFriendlyByteBuf buffer) {
			return new VillagerNewsSettingsPayload(buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean());
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buffer, VillagerNewsSettingsPayload payload) {
			buffer.writeVarInt(payload.chattiness());
			buffer.writeVarInt(payload.rareVoicelines());
			buffer.writeBoolean(payload.spawnSpecialVillagers());
		}
	};

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
