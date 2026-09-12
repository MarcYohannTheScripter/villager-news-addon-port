package com.vnap.dialogue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vnap.VillagerNewsAddonPort;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class DialogueCatalog {
	private static final String CATALOG_PATH = "/assets/villager-news-addon-port/dialogues.json";
	private static final Map<String, DialogueGroup> GROUPS = new LinkedHashMap<>();
	private static final Map<String, List<DialogueGroup>> TITLES = new LinkedHashMap<>();

	private DialogueCatalog() {
	}

	public static void register() {
		try (InputStream stream = DialogueCatalog.class.getResourceAsStream(CATALOG_PATH)) {
			if (stream == null) {
				throw new IOException("Missing " + CATALOG_PATH);
			}
			JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
			int variantCount = 0;
			for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("groups").entrySet()) {
				String groupId = entry.getKey();
				JsonObject value = entry.getValue().getAsJsonObject();
				List<DialogueVariant> variants = new ArrayList<>();
				for (JsonElement variantElement : value.getAsJsonArray("variants")) {
					JsonObject variantValue = variantElement.getAsJsonObject();
					int index = variantValue.get("index").getAsInt();
					Identifier soundId = VillagerNewsAddonPort.id("dialogue." + groupId + "." + index);
					SoundEvent sound = Registry.register(
						BuiltInRegistries.SOUND_EVENT,
						soundId,
						SoundEvent.createVariableRangeEvent(soundId)
					);
					variants.add(new DialogueVariant(
						index,
						variantValue.get("duration").getAsDouble(),
						variantValue.get("weight").getAsInt(),
						variantValue.get("animation").getAsString(),
						sound
					));
					variantCount++;
				}
				DialogueGroup group = new DialogueGroup(
					groupId,
					value.get("title").getAsString(),
					value.get("body").getAsString(),
					value.get("speaker").getAsString(),
					value.get("maximumDuration").getAsDouble(),
					List.copyOf(variants)
				);
				GROUPS.put(groupId, group);
				if (!group.title().isBlank()) TITLES.computeIfAbsent(group.title(), ignored -> new ArrayList<>()).add(group);
			}
			VillagerNewsAddonPort.LOGGER.info("Registered {} contextual dialogue groups with {} synchronized variants", GROUPS.size(), variantCount);
		} catch (IOException | RuntimeException exception) {
			throw new IllegalStateException("Could not load Villager News dialogue catalog", exception);
		}
	}

	public static DialogueGroup byId(String id) {
		return GROUPS.get(id);
	}

	public static DialogueGroup byTitle(String title) {
		List<DialogueGroup> matches = TITLES.get(title);
		return matches == null || matches.isEmpty() ? null : matches.getFirst();
	}

	public static DialogueGroup byTitle(String title, String speaker) {
		List<DialogueGroup> matches = TITLES.get(title);
		if (matches == null) return null;
		return matches.stream().filter(group -> group.speaker().equals(speaker)).findFirst().orElse(null);
	}

	public static Map<String, DialogueGroup> groups() {
		return Collections.unmodifiableMap(GROUPS);
	}

	public record DialogueGroup(
		String id,
		String title,
		String body,
		String speaker,
		double maximumDuration,
		List<DialogueVariant> variants
	) {
		public long durationTicks() {
			return Math.max(20L, (long) Math.ceil(maximumDuration * 20.0));
		}

		public DialogueVariant chooseVariant() {
			return chooseVariant(1);
		}

		public DialogueVariant chooseVariant(int rareVoicelines) {
			if (variants.isEmpty()) return null;
			int minimum = variants.stream().mapToInt(DialogueVariant::weight).min().orElse(1);
			int maximum = variants.stream().mapToInt(DialogueVariant::weight).max().orElse(1);
			int[] weights = new int[variants.size()];
			int totalWeight = 0;
			for (int index = 0; index < variants.size(); index++) {
				int weight = variants.get(index).weight();
				if (rareVoicelines == 0 && weight < maximum * 0.8) weight = 0;
				else if (rareVoicelines == 2) weight = maximum + minimum - weight;
				weights[index] = Math.max(0, weight);
				totalWeight += weights[index];
			}
			if (totalWeight <= 0) return variants.getFirst();
			int choice = ThreadLocalRandom.current().nextInt(Math.max(1, totalWeight));
			for (int index = 0; index < variants.size(); index++) {
				choice -= weights[index];
				if (choice < 0) return variants.get(index);
			}
			return variants.getLast();
		}
	}

	public record DialogueVariant(
		int index,
		double duration,
		int weight,
		String animation,
		SoundEvent sound
	) {
		public long durationTicks() {
			return Math.max(20L, (long) Math.ceil(duration * 20.0));
		}
	}
}
