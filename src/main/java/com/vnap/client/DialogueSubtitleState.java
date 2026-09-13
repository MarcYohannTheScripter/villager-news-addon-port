package com.vnap.client;

import com.vnap.dialogue.DialogueCatalog;
import com.vnap.network.DialogueAnimationPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DialogueSubtitleState {
	private static final double RANGE_SQUARED = 16.0 * 16.0;
	private static final Map<UUID, ActiveSubtitle> ACTIVE = new HashMap<>();
	private static ActiveSubtitle displayed;

	private DialogueSubtitleState() {
	}

	public static void start(DialogueAnimationPayload payload) {
		if (payload.groupId().isEmpty() || payload.durationTicks() <= 0) {
			ActiveSubtitle removed = ACTIVE.remove(payload.entityId());
			if (removed == displayed) clear(Minecraft.getInstance());
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
			ACTIVE.clear();
			clear(minecraft);
			return;
		}
		if (!minecraft.options.showSubtitles().get()) {
			clear(minecraft);
			return;
		}
		long now = System.nanoTime();
		ActiveSubtitle nearest = null;
		int nearestFrame = -1;
		double nearestDistance = Double.MAX_VALUE;
		Iterator<Map.Entry<UUID, ActiveSubtitle>> iterator = ACTIVE.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, ActiveSubtitle> entry = iterator.next();
			ActiveSubtitle active = entry.getValue();
			if (now >= active.endNanos()) {
				iterator.remove();
				continue;
			}
			Entity entity = minecraft.level.getEntity(entry.getKey());
			if (entity == null || !entity.isAlive()) continue;
			double distance = minecraft.player.distanceToSqr(entity);
			if (distance > RANGE_SQUARED || distance >= nearestDistance) continue;
			int frame = active.frame(now);
			if (frame < 0) continue;
			nearest = active;
			nearestFrame = frame;
			nearestDistance = distance;
		}
		if (nearest == null) {
			clear(minecraft);
			return;
		}
		displayed = nearest;
		minecraft.gui.hud.setOverlayMessage(Component.translatable(nearest.subtitles().get(nearestFrame).key()), false);
	}

	private static void clear(Minecraft minecraft) {
		if (displayed == null) return;
		displayed = null;
		minecraft.gui.hud.setOverlayMessage(Component.empty(), false);
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
