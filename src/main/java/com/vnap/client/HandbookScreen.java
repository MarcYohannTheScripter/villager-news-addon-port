package com.vnap.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class HandbookScreen extends Screen {
	private static final HandbookData DATA = load();
	private static final int ROWS = 8;
	private Page page = Page.HOME;
	private Page returnPage = Page.TRIGGERS;
	private int pageIndex;
	private int entryIndex;
	private int categoryIndex;
	private int sectionIndex;
	private String search = "";
	private Entry detail;

	public HandbookScreen() {
		super(Component.literal("Villager News"));
	}

	@Override
	protected void init() {
		int contentWidth = Math.min(380, width - 32);
		int left = (width - contentWidth) / 2;
		addText(left, 16, contentWidth, Component.literal(titleForPage()).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), true);
		switch (page) {
			case HOME -> buildHome(left, contentWidth);
			case GUIDE -> buildGuide(left, contentWidth);
			case OVERVIEW -> buildEntryPage(left, contentWidth, DATA.overview, Page.GUIDE);
			case SPECIALS -> buildEntryPage(left, contentWidth, DATA.specialVillagers, Page.GUIDE);
			case COSMETICS -> buildEntryPage(left, contentWidth, DATA.cosmetics, Page.GUIDE);
			case GENERAL -> buildEntryPage(left, contentWidth, DATA.generalInformation, Page.TRIGGERS);
			case SETTINGS -> buildSettings(left, contentWidth);
			case SOCIALS -> buildEntryPage(left, contentWidth, DATA.socials, Page.HOME);
			case SUPPORT -> buildSupport(left, contentWidth);
			case TRIGGERS -> buildTriggers(left, contentWidth);
			case CATEGORY -> buildCategory(left, contentWidth);
			case SECTION -> buildSection(left, contentWidth);
			case DETAIL -> buildDetail(left, contentWidth);
		}
	}

	private void buildHome(int left, int contentWidth) {
		addText(left, 48, contentWidth, Component.literal(DATA.headline), true);
		int y = 126;
		addMenuButton(left, y, contentWidth, "Guide", Page.GUIDE);
		addMenuButton(left, y + 24, contentWidth, "Settings", Page.SETTINGS);
		addMenuButton(left, y + 48, contentWidth, "Socials", Page.SOCIALS);
		addMenuButton(left, y + 72, contentWidth, "Support", Page.SUPPORT);
		addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
			.bounds(left, height - 30, contentWidth, 20).build());
	}

	private void buildGuide(int left, int contentWidth) {
		addText(left, 44, contentWidth, Component.literal(DATA.guideIntro), true);
		int y = 112;
		addMenuButton(left, y, contentWidth, "Overview", Page.OVERVIEW);
		addMenuButton(left, y + 24, contentWidth, "Special Villagers", Page.SPECIALS);
		addMenuButton(left, y + 48, contentWidth, "Cosmetics", Page.COSMETICS);
		addMenuButton(left, y + 72, contentWidth, "Triggers & Reactions", Page.TRIGGERS);
		addBackButton(left, contentWidth, Page.HOME);
	}

	private void buildTriggers(int left, int contentWidth) {
		EditBox field = new EditBox(font, left, 44, contentWidth - 62, 20, Component.literal("Search Triggers"));
		field.setValue(search);
		field.setMaxLength(80);
		field.setHint(Component.literal("Search Triggers"));
		addRenderableWidget(field);
		addRenderableWidget(Button.builder(Component.literal("Go"), button -> {
			search = field.getValue().trim();
			pageIndex = 0;
			rebuildWidgets();
		}).bounds(left + contentWidth - 58, 44, 58, 20).build());
		if (!search.isBlank()) {
			buildSearchResults(left, contentWidth);
			return;
		}
		addText(left, 70, contentWidth, Component.literal("Browse triggers and reactions by category."), true);
		addRenderableWidget(Button.builder(Component.literal("General Information"), button -> navigate(Page.GENERAL))
			.bounds(left, 94, contentWidth, 20).build());
		List<Category> categories = DATA.categories;
		int start = pageIndex * ROWS;
		for (int index = start; index < Math.min(categories.size(), start + ROWS); index++) {
			int selected = index;
			addRenderableWidget(Button.builder(Component.literal(categories.get(index).title), button -> {
				categoryIndex = selected;
				pageIndex = 0;
				page = Page.CATEGORY;
				rebuildWidgets();
			}).bounds(left, 118 + (index - start) * 22, contentWidth, 20).build());
		}
		addPager(left, contentWidth, categories.size(), Page.GUIDE);
	}

	private void buildSearchResults(int left, int contentWidth) {
		String query = search.toLowerCase(Locale.ROOT);
		List<Entry> results = DATA.searchable.stream()
			.filter(entry -> clean(entry.title).toLowerCase(Locale.ROOT).contains(query)
				|| clean(entry.body).toLowerCase(Locale.ROOT).contains(query))
			.sorted(Comparator.comparing(Entry::title, String.CASE_INSENSITIVE_ORDER))
			.toList();
		addText(left, 70, contentWidth, Component.literal(results.size() + " matching triggers"), true);
		int start = pageIndex * ROWS;
		for (int index = start; index < Math.min(results.size(), start + ROWS); index++) {
			Entry entry = results.get(index);
			addRenderableWidget(Button.builder(Component.literal(clean(entry.title)), button -> openDetail(entry, Page.TRIGGERS))
				.bounds(left, 94 + (index - start) * 22, contentWidth, 20).build());
		}
		if (results.isEmpty()) addText(left, 110, contentWidth, Component.literal("No triggers match your search.").withStyle(ChatFormatting.RED), true);
		addPager(left, contentWidth, results.size(), Page.GUIDE);
	}

	private void buildCategory(int left, int contentWidth) {
		Category category = DATA.categories.get(categoryIndex);
		addText(left, 44, contentWidth, Component.literal("Choose a section below to browse its triggers."), true);
		int start = pageIndex * ROWS;
		for (int index = start; index < Math.min(category.sections.size(), start + ROWS); index++) {
			int selected = index;
			addRenderableWidget(Button.builder(Component.literal(category.sections.get(index).title), button -> {
				sectionIndex = selected;
				pageIndex = 0;
				page = Page.SECTION;
				rebuildWidgets();
			}).bounds(left, 72 + (index - start) * 22, contentWidth, 20).build());
		}
		addPager(left, contentWidth, category.sections.size(), Page.TRIGGERS);
	}

	private void buildSection(int left, int contentWidth) {
		Section section = DATA.categories.get(categoryIndex).sections.get(sectionIndex);
		List<Entry> groups = new ArrayList<>();
		for (String id : section.groups) {
			Entry entry = DATA.contexts.get(id);
			if (entry != null && !entry.title.isBlank()) groups.add(entry);
		}
		groups.addAll(section.entries);
		addText(left, 44, contentWidth, Component.literal("Choose a trigger to see how to activate it and what reaction it causes."), true);
		int start = pageIndex * ROWS;
		for (int index = start; index < Math.min(groups.size(), start + ROWS); index++) {
			Entry entry = groups.get(index);
			addRenderableWidget(Button.builder(Component.literal(clean(entry.title)), button -> openDetail(entry, Page.SECTION))
				.bounds(left, 76 + (index - start) * 22, contentWidth, 20).build());
		}
		if (groups.isEmpty()) addText(left, 100, contentWidth, Component.literal("This section is covered by the general guide entries."), true);
		addPager(left, contentWidth, groups.size(), Page.CATEGORY);
	}

	private void buildDetail(int left, int contentWidth) {
		if (detail != null) {
			addText(left, 52, contentWidth, Component.literal(clean(detail.body)), false);
		}
		addBackButton(left, contentWidth, returnPage);
	}

	private void buildEntryPage(int left, int contentWidth, List<Entry> entries, Page back) {
		Entry entry = entries.get(Math.max(0, Math.min(entryIndex, entries.size() - 1)));
		addText(left, 48, contentWidth, Component.literal(clean(entry.title)).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), true);
		addText(left, 74, contentWidth, Component.literal(clean(entry.body)), false);
		int half = (contentWidth - 6) / 2;
		Button previous = Button.builder(Component.literal("Previous"), button -> {
			entryIndex--;
			rebuildWidgets();
		}).bounds(left, height - 54, half, 20).build();
		previous.active = entryIndex > 0;
		addRenderableWidget(previous);
		Button next = Button.builder(Component.literal("Next"), button -> {
			entryIndex++;
			rebuildWidgets();
		}).bounds(left + half + 6, height - 54, half, 20).build();
		next.active = entryIndex + 1 < entries.size();
		addRenderableWidget(next);
		addBackButton(left, contentWidth, back);
	}

	private void buildSupport(int left, int contentWidth) {
		addText(left, 52, contentWidth, Component.literal(DATA.support), false);
		addBackButton(left, contentWidth, Page.HOME);
	}

	private void buildSettings(int left, int contentWidth) {
		addText(left, 42, contentWidth, Component.literal("Dialogue settings are saved by the current server."), true);
		int labelWidth = Math.min(166, contentWidth / 2);
		int buttonLeft = left + labelWidth;
		int buttonWidth = contentWidth - labelWidth;
		int y = 66;
		addText(left, y + 6, labelWidth - 6, Component.literal("Show Subtitles"), false);
		addRenderableWidget(Button.builder(Component.literal(toggleLabel(minecraft.options.showSubtitles().get())), button -> {
			boolean enabled = !minecraft.options.showSubtitles().get();
			minecraft.options.showSubtitles().set(enabled);
			minecraft.options.save();
			button.setMessage(Component.literal(toggleLabel(enabled)));
		}).bounds(buttonLeft, y, buttonWidth, 20).build());
		y += 26;
		addText(left, y + 6, labelWidth - 6, Component.literal("Villager Chattiness"), false);
		addRenderableWidget(Button.builder(Component.literal(chattinessLabel(VillagerNewsSettingsState.chattiness())), button -> {
			VillagerNewsSettingsState.setChattiness(VillagerNewsSettingsState.chattiness() + 1);
			button.setMessage(Component.literal(chattinessLabel(VillagerNewsSettingsState.chattiness())));
		}).bounds(buttonLeft, y, buttonWidth, 20).build());
		y += 26;
		addText(left, y + 6, labelWidth - 6, Component.literal("Rare Voicelines"), false);
		addRenderableWidget(Button.builder(Component.literal(rareLabel(VillagerNewsSettingsState.rareVoicelines())), button -> {
			VillagerNewsSettingsState.setRareVoicelines(VillagerNewsSettingsState.rareVoicelines() + 1);
			button.setMessage(Component.literal(rareLabel(VillagerNewsSettingsState.rareVoicelines())));
		}).bounds(buttonLeft, y, buttonWidth, 20).build());
		y += 26;
		addText(left, y + 6, labelWidth - 6, Component.literal("Spawn Special Villagers"), false);
		addRenderableWidget(Button.builder(Component.literal(toggleLabel(VillagerNewsSettingsState.spawnSpecialVillagers())), button -> {
			VillagerNewsSettingsState.setSpawnSpecialVillagers(!VillagerNewsSettingsState.spawnSpecialVillagers());
			button.setMessage(Component.literal(toggleLabel(VillagerNewsSettingsState.spawnSpecialVillagers())));
		}).bounds(buttonLeft, y, buttonWidth, 20).build());
		y += 26;
		addText(left, y + 6, labelWidth - 6, Component.literal("Villager Style"), false);
		Button style = Button.builder(Component.literal("Villager News"), button -> {
		}).bounds(buttonLeft, y, buttonWidth, 20).build();
		style.active = false;
		addRenderableWidget(style);
		addBackButton(left, contentWidth, Page.HOME);
	}

	private static String toggleLabel(boolean enabled) {
		return enabled ? "On" : "Off";
	}

	private static String chattinessLabel(int value) {
		return switch (value) {
			case 0 -> "Muted";
			case 1 -> "Shy";
			case 3 -> "Super Chatty";
			default -> "Chatty";
		};
	}

	private static String rareLabel(int value) {
		return switch (value) {
			case 0 -> "Never";
			case 2 -> "Often";
			default -> "Default";
		};
	}

	private void addPager(int left, int contentWidth, int count, Page back) {
		int pages = Math.max(1, (count + ROWS - 1) / ROWS);
		int third = (contentWidth - 12) / 3;
		Button previous = Button.builder(Component.literal("Previous"), button -> {
			pageIndex--;
			rebuildWidgets();
		}).bounds(left, height - 30, third, 20).build();
		previous.active = pageIndex > 0;
		addRenderableWidget(previous);
		addRenderableWidget(Button.builder(Component.literal("Back"), button -> navigate(back))
			.bounds(left + third + 6, height - 30, third, 20).build());
		Button next = Button.builder(Component.literal("Next"), button -> {
			pageIndex++;
			rebuildWidgets();
		}).bounds(left + (third + 6) * 2, height - 30, third, 20).build();
		next.active = pageIndex + 1 < pages;
		addRenderableWidget(next);
	}

	private void addMenuButton(int left, int y, int contentWidth, String label, Page destination) {
		addRenderableWidget(Button.builder(Component.literal(label), button -> navigate(destination))
			.bounds(left, y, contentWidth, 20).build());
	}

	private void addBackButton(int left, int contentWidth, Page destination) {
		addRenderableWidget(Button.builder(Component.literal("Back"), button -> navigate(destination))
			.bounds(left, height - 30, contentWidth, 20).build());
	}

	private void navigate(Page destination) {
		page = destination;
		pageIndex = 0;
		entryIndex = 0;
		if (destination != Page.TRIGGERS) search = "";
		rebuildWidgets();
	}

	private void openDetail(Entry entry, Page back) {
		detail = entry;
		returnPage = back;
		page = Page.DETAIL;
		rebuildWidgets();
	}

	private MultiLineTextWidget addText(int x, int y, int textWidth, Component text, boolean centered) {
		MultiLineTextWidget widget = new MultiLineTextWidget(x, y, text, font).setMaxWidth(textWidth).setCentered(centered);
		addRenderableWidget(widget);
		return widget;
	}

	private String titleForPage() {
		return switch (page) {
			case HOME -> "Villager News";
			case GUIDE -> "Guide";
			case OVERVIEW -> "Overview";
			case SPECIALS -> "Special Villagers";
			case COSMETICS -> "Cosmetics";
			case GENERAL -> "General Information";
			case SETTINGS -> "Settings";
			case SOCIALS -> "Socials";
			case SUPPORT -> "Support";
			case TRIGGERS -> "Triggers & Reactions";
			case CATEGORY -> DATA.categories.get(categoryIndex).title;
			case SECTION -> DATA.categories.get(categoryIndex).sections.get(sectionIndex).title;
			case DETAIL -> {
				yield detail == null ? "Trigger" : clean(detail.title);
			}
		};
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private static String clean(String value) {
		return value.replace("Â", "").replaceAll("§[0-9a-fk-or]", "");
	}

	private static HandbookData load() {
		String path = "/assets/villager-news-addon-port/handbook.json";
		try (InputStream stream = HandbookScreen.class.getResourceAsStream(path)) {
			if (stream == null) throw new IOException("Missing " + path);
			JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
			List<Category> categories = new ArrayList<>();
			Map<String, Entry> contexts = new LinkedHashMap<>();
			for (Map.Entry<String, JsonElement> context : root.getAsJsonObject("contexts").entrySet()) {
				JsonObject value = context.getValue().getAsJsonObject();
				String title = value.get("browseTitle").getAsString();
				if (title.isBlank()) title = value.get("title").getAsString();
				contexts.put(context.getKey(), new Entry(title, value.get("body").getAsString()));
			}
			List<Entry> searchable = new ArrayList<>();
			for (JsonElement categoryElement : root.getAsJsonArray("categories")) {
				JsonObject category = categoryElement.getAsJsonObject();
				List<Section> sections = new ArrayList<>();
				for (JsonElement sectionElement : category.getAsJsonArray("sections")) {
					JsonObject section = sectionElement.getAsJsonObject();
					List<String> groups = new ArrayList<>();
					for (JsonElement group : section.getAsJsonArray("groups")) groups.add(group.getAsString());
					List<Entry> sectionEntries = entries(section.getAsJsonArray("entries"));
					for (String group : groups) {
						Entry entry = contexts.get(group);
						if (entry != null && !entry.title.isBlank()) searchable.add(entry);
					}
					searchable.addAll(sectionEntries);
					sections.add(new Section(section.get("title").getAsString(), List.copyOf(groups), sectionEntries));
				}
				categories.add(new Category(category.get("title").getAsString(), List.copyOf(sections)));
			}
			return new HandbookData(
				root.get("headline").getAsString(),
				root.get("guideIntro").getAsString(),
				entries(root.getAsJsonArray("overview")),
				entries(root.getAsJsonArray("specialVillagers")),
				entries(root.getAsJsonArray("cosmetics")),
				entries(root.getAsJsonArray("generalInformation")),
				entries(root.getAsJsonArray("socials")),
				entries(root.getAsJsonArray("settings")),
				root.get("support").getAsString(),
				List.copyOf(categories),
				Map.copyOf(contexts),
				List.copyOf(searchable)
			);
		} catch (IOException | RuntimeException exception) {
			throw new IllegalStateException("Could not load the Villager News handbook", exception);
		}
	}

	private static List<Entry> entries(JsonArray array) {
		List<Entry> result = new ArrayList<>();
		for (JsonElement element : array) {
			JsonObject entry = element.getAsJsonObject();
			result.add(new Entry(entry.get("title").getAsString(), entry.get("body").getAsString()));
		}
		return List.copyOf(result);
	}

	private enum Page {
		HOME, GUIDE, OVERVIEW, SPECIALS, COSMETICS, GENERAL, SETTINGS, SOCIALS, SUPPORT, TRIGGERS, CATEGORY, SECTION, DETAIL
	}

	private record Entry(String title, String body) {
	}

	private record Section(String title, List<String> groups, List<Entry> entries) {
	}

	private record Category(String title, List<Section> sections) {
	}

	private record HandbookData(String headline, String guideIntro, List<Entry> overview,
		List<Entry> specialVillagers, List<Entry> cosmetics, List<Entry> generalInformation,
		List<Entry> socials, List<Entry> settings, String support, List<Category> categories,
		Map<String, Entry> contexts, List<Entry> searchable) {
	}
}
