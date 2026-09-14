package com.vnap.client;

import com.vnap.VillagerNewsAddonPort;
import com.vnap.dialogue.DialogueCatalog;
import com.vnap.network.DialogueAnimationPayload;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DialogueSubtitleState {
	private static final double RANGE = 16.0;
	private static final double RANGE_SQUARED = RANGE * RANGE;
	private static final int MAX_LINES = 4;
	private static final Map<UUID, ActiveSubtitle> ACTIVE = new HashMap<>();

	private DialogueSubtitleState() {
	}

	public static void register() {
		HudElementRegistry.attachElementAfter(
			VanillaHudElements.OVERLAY_MESSAGE,
			VillagerNewsAddonPort.id("dialogue_subtitles"),
			DialogueSubtitleState::render
		);
	}

	public static void start(DialogueAnimationPayload payload) {
		if (payload.groupId().isEmpty() || payload.durationTicks() <= 0) {
			ACTIVE.remove(payload.entityId());
			return;
		}
		DialogueCatalog.DialogueGroup group = DialogueCatalog.byId(payload.groupId());
		if (group == null) return;
		DialogueCatalog.DialogueVariant variant = group.variants().stream()
			.filter(candidate -> candidate.index() == payload.variantIndex()).findFirst().orElse(null);
		if (variant == null || variant.subtitles().isEmpty()) return;
		long startNanos = System.nanoTime();
		ACTIVE.put(payload.entityId(), new ActiveSubtitle(
			startNanos, startNanos + payload.durationTicks() * 50_000_000L, variant.subtitles()
		));
	}

	public static void tick(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.player == null) {
			clear();
			return;
		}
		long now = System.nanoTime();
		Iterator<Map.Entry<UUID, ActiveSubtitle>> iterator = ACTIVE.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, ActiveSubtitle> entry = iterator.next();
			Entity entity = minecraft.level.getEntity(entry.getKey());
			if (now >= entry.getValue().endNanos() || entity != null && !entity.isAlive()) iterator.remove();
		}
	}

	public static void clear() {
		ACTIVE.clear();
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null || !VillagerNewsClientSettings.showSubtitles()) return;
		long now = System.nanoTime();
		List<VisibleSubtitle> visible = new ArrayList<>();
		for (Map.Entry<UUID, ActiveSubtitle> entry : ACTIVE.entrySet()) {
			ActiveSubtitle active = entry.getValue();
			if (now >= active.endNanos()) continue;
			Entity entity = minecraft.level.getEntity(entry.getKey());
			if (entity == null || !entity.isAlive()) continue;
			double distanceSquared = minecraft.player.distanceToSqr(entity);
			if (distanceSquared > RANGE_SQUARED) continue;
			int frame = active.frame(now);
			if (frame < 0) continue;
			Component transcript = Component.translatable(active.subtitles().get(frame).key());
			visible.add(new VisibleSubtitle(distanceSquared, subtitleLine(entity, transcript)));
		}
		visible.sort(Comparator.comparingDouble(VisibleSubtitle::distanceSquared));
		float y = graphics.guiHeight() - 59.0F;
		for (int index = 0; index < Math.min(MAX_LINES, visible.size()); index++) {
			VisibleSubtitle subtitle = visible.get(index);
			float scale = subtitleScale(index, subtitle.distanceSquared());
			drawCentered(graphics, minecraft, subtitle.text(), y, scale);
			y -= minecraft.font.lineHeight + 3.0F;
		}
	}

	private static Component subtitleLine(Entity entity, Component transcript) {
		Component name = entity.getName();
		if (entity instanceof Villager villager && !villager.hasCustomName()) {
			name = villager.getVillagerData().profession().value().name();
		}
		MutableComponent line = Component.empty();
		line.append(name.copy().withStyle(ChatFormatting.YELLOW));
		line.append(Component.literal(": ").withStyle(ChatFormatting.YELLOW));
		line.append(transcript.copy().withStyle(ChatFormatting.WHITE));
		return line;
	}

	private static float subtitleScale(int index, double distanceSquared) {
		if (index == 0) return 1.0F;
		double distance = Math.sqrt(distanceSquared);
		return (float) Math.max(0.65, Math.min(0.9, 0.95 - distance / RANGE * 0.3));
	}

	private static void drawCentered(GuiGraphicsExtractor graphics, Minecraft minecraft, Component text, float y, float scale) {
		int width = minecraft.font.width(text);
		graphics.pose().pushMatrix();
		graphics.pose().translate(graphics.guiWidth() / 2.0F, y);
		graphics.pose().scale(scale, scale);
		graphics.text(minecraft.font, text, -width / 2, 0, 0xFFFFFFFF, true);
		graphics.pose().popMatrix();
	}

	private record VisibleSubtitle(double distanceSquared, Component text) {
	}

	private record ActiveSubtitle(
		long startNanos,
		long endNanos,
		List<DialogueCatalog.SubtitleFrame> subtitles
	) {
		int frame(long now) {
			double elapsed = (now - startNanos) / 1_000_000_000.0;
			int frame = -1;
			for (int index = 0; index < subtitles.size(); index++) {
				if (subtitles.get(index).time() > elapsed) break;
				frame = index;
			}
			return frame;
		}
	}
}
