package com.vnap.client;

import com.vnap.network.VillagerNewsSettingsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public final class VillagerNewsSettingsState {
	private static int chattiness = 2;
	private static int rareVoicelines = 1;
	private static boolean spawnSpecialVillagers = true;

	private VillagerNewsSettingsState() {
	}

	public static void apply(VillagerNewsSettingsPayload payload) {
		chattiness = Math.max(0, Math.min(3, payload.chattiness()));
		rareVoicelines = Math.max(0, Math.min(2, payload.rareVoicelines()));
		spawnSpecialVillagers = payload.spawnSpecialVillagers();
	}

	public static int chattiness() {
		return chattiness;
	}

	public static int rareVoicelines() {
		return rareVoicelines;
	}

	public static boolean spawnSpecialVillagers() {
		return spawnSpecialVillagers;
	}

	public static void setChattiness(int value) {
		chattiness = Math.floorMod(value, 4);
		send();
	}

	public static void setRareVoicelines(int value) {
		rareVoicelines = Math.floorMod(value, 3);
		send();
	}

	public static void setSpawnSpecialVillagers(boolean value) {
		spawnSpecialVillagers = value;
		send();
	}

	private static void send() {
		if (Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(VillagerNewsSettingsPayload.TYPE)) {
			ClientPlayNetworking.send(new VillagerNewsSettingsPayload(chattiness, rareVoicelines, spawnSpecialVillagers));
		}
	}
}
