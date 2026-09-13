package com.vnap.client;

import com.vnap.config.VillagerNewsSettings;
import com.vnap.network.VillagerNewsSettingsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public final class VillagerNewsSettingsState {
	private static int chattiness = 2;
	private static int rareVoicelines = 1;
	private static boolean spawnSpecialVillagers = true;
	private static boolean canEdit;
	private static boolean localSettings;

	private VillagerNewsSettingsState() {
	}

	public static void apply(VillagerNewsSettingsPayload payload) {
		chattiness = Math.max(0, Math.min(3, payload.chattiness()));
		rareVoicelines = Math.max(0, Math.min(2, payload.rareVoicelines()));
		spawnSpecialVillagers = payload.spawnSpecialVillagers();
		canEdit = payload.canEdit();
		localSettings = false;
	}

	public static void prepareConfigScreen() {
		if (Minecraft.getInstance().getConnection() != null) return;
		chattiness = VillagerNewsSettings.chattiness();
		rareVoicelines = VillagerNewsSettings.rareVoicelines();
		spawnSpecialVillagers = VillagerNewsSettings.spawnSpecialVillagers();
		canEdit = true;
		localSettings = true;
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

	public static boolean canEdit() {
		return canEdit;
	}

	public static boolean localSettings() {
		return localSettings;
	}

	public static void setChattiness(int value) {
		if (!canEdit) return;
		chattiness = Math.floorMod(value, 4);
		send();
	}

	public static void setRareVoicelines(int value) {
		if (!canEdit) return;
		rareVoicelines = Math.floorMod(value, 3);
		send();
	}

	public static void setSpawnSpecialVillagers(boolean value) {
		if (!canEdit) return;
		spawnSpecialVillagers = value;
		send();
	}

	private static void send() {
		if (localSettings) {
			VillagerNewsSettings.update(chattiness, rareVoicelines, spawnSpecialVillagers);
			return;
		}
		if (Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(VillagerNewsSettingsPayload.TYPE)) {
			ClientPlayNetworking.send(new VillagerNewsSettingsPayload(chattiness, rareVoicelines, spawnSpecialVillagers, false));
		}
	}

	public static void reset() {
		canEdit = false;
		localSettings = false;
	}
}
