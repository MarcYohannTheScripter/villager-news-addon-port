package com.vnap.client;

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

public final class VillagerNewsClientSettings {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("villager-news-addon-port-client.json");
	private static boolean showSubtitles = true;

	private VillagerNewsClientSettings() {
	}

	public static synchronized void load() {
		if (!Files.exists(PATH)) {
			save();
			return;
		}
		try {
			JsonObject root = JsonParser.parseString(Files.readString(PATH, StandardCharsets.UTF_8)).getAsJsonObject();
			showSubtitles = !root.has("showSubtitles") || root.get("showSubtitles").getAsBoolean();
		} catch (IOException | RuntimeException exception) {
			VillagerNewsAddonPort.LOGGER.warn("Could not load Villager News client settings; using defaults", exception);
			showSubtitles = true;
			save();
		}
	}

	public static boolean showSubtitles() {
		return showSubtitles;
	}

	public static synchronized void setShowSubtitles(boolean enabled) {
		showSubtitles = enabled;
		save();
	}

	private static void save() {
		JsonObject root = new JsonObject();
		root.addProperty("showSubtitles", showSubtitles);
		try {
			Files.createDirectories(PATH.getParent());
			Files.writeString(PATH, GSON.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
		} catch (IOException exception) {
			VillagerNewsAddonPort.LOGGER.warn("Could not save Villager News client settings", exception);
		}
	}
}
