package com.vnap.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vnap.VillagerNewsAddonPort;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class VillagerNewsSettings {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("villager-news-addon-port.json");
	private static int chattiness = 2;
	private static int rareVoicelines = 1;
	private static boolean spawnSpecialVillagers = true;

	private VillagerNewsSettings() {
	}

	public static synchronized void load() {
		if (!Files.exists(PATH)) {
			save();
			return;
		}
		try {
			JsonObject root = JsonParser.parseString(Files.readString(PATH, StandardCharsets.UTF_8)).getAsJsonObject();
			chattiness = clamp(root.has("chattiness") ? root.get("chattiness").getAsInt() : 2, 0, 3);
			rareVoicelines = clamp(root.has("rareVoicelines") ? root.get("rareVoicelines").getAsInt() : 1, 0, 2);
			spawnSpecialVillagers = !root.has("spawnSpecialVillagers") || root.get("spawnSpecialVillagers").getAsBoolean();
		} catch (IOException | RuntimeException exception) {
			VillagerNewsAddonPort.LOGGER.warn("Could not load Villager News settings; using defaults", exception);
			chattiness = 2;
			rareVoicelines = 1;
			spawnSpecialVillagers = true;
			save();
		}
	}

	public static synchronized void update(int newChattiness, int newRareVoicelines, boolean newSpawnSpecialVillagers) {
		chattiness = clamp(newChattiness, 0, 3);
		rareVoicelines = clamp(newRareVoicelines, 0, 2);
		spawnSpecialVillagers = newSpawnSpecialVillagers;
		save();
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

	public static boolean dialogueEnabled() {
		return chattiness > 0;
	}

	public static long scaleCooldown(long cooldown) {
		return switch (chattiness) {
			case 1 -> cooldown > Long.MAX_VALUE / 2L ? Long.MAX_VALUE : cooldown * 2L;
			case 3 -> Math.max(1L, Math.round(cooldown / 5.0));
			default -> cooldown;
		};
	}

	private static synchronized void save() {
		JsonObject root = new JsonObject();
		root.addProperty("chattiness", chattiness);
		root.addProperty("rareVoicelines", rareVoicelines);
		root.addProperty("spawnSpecialVillagers", spawnSpecialVillagers);
		try {
			Files.createDirectories(PATH.getParent());
			Files.writeString(PATH, GSON.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
		} catch (IOException exception) {
			VillagerNewsAddonPort.LOGGER.warn("Could not save Villager News settings", exception);
		}
	}

	private static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}
}
