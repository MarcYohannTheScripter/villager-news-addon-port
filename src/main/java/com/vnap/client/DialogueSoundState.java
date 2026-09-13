package com.vnap.client;

import com.vnap.dialogue.DialogueCatalog;
import com.vnap.network.DialogueAnimationPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class DialogueSoundState {
	private static final Map<UUID, ActiveSound> ACTIVE = new HashMap<>();

	private DialogueSoundState() {
	}

	public static void start(DialogueAnimationPayload payload) {
		Minecraft minecraft = Minecraft.getInstance();
		stop(minecraft, payload.entityId());
		if (payload.groupId().isEmpty() || minecraft.level == null) return;
		DialogueCatalog.DialogueGroup group = DialogueCatalog.byId(payload.groupId());
		if (group == null) return;
		DialogueCatalog.DialogueVariant variant = group.variants().stream()
			.filter(candidate -> candidate.index() == payload.variantIndex()).findFirst().orElse(null);
		Entity entity = minecraft.level.getEntity(payload.entityId());
		if (variant == null || entity == null) return;
		boolean followsEntity = entity.isAlive();
		SoundInstance sound = followsEntity
			? new EntityBoundSoundInstance(variant.sound(), SoundSource.NEUTRAL, 1.0F, 1.0F, entity, entity.getRandom().nextLong())
			: new SimpleSoundInstance(variant.sound(), SoundSource.NEUTRAL, 1.0F, 1.0F, RandomSource.create(), entity.getX(), entity.getY(), entity.getZ());
		minecraft.getSoundManager().play(sound);
		ACTIVE.put(payload.entityId(), new ActiveSound(sound, followsEntity, System.nanoTime() + payload.durationTicks() * 50_000_000L));
	}

	public static void tick(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.player == null) {
			for (UUID id : ACTIVE.keySet().toArray(UUID[]::new)) stop(minecraft, id);
			return;
		}
		long now = System.nanoTime();
		Iterator<Map.Entry<UUID, ActiveSound>> iterator = ACTIVE.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, ActiveSound> entry = iterator.next();
			Entity entity = minecraft.level.getEntity(entry.getKey());
			if (now < entry.getValue().endNanos()
				&& (!entry.getValue().followsEntity() || entity != null && entity.isAlive())) continue;
			minecraft.getSoundManager().stop(entry.getValue().instance());
			iterator.remove();
		}
	}

	private static void stop(Minecraft minecraft, UUID id) {
		ActiveSound active = ACTIVE.remove(id);
		if (active != null) minecraft.getSoundManager().stop(active.instance());
	}

	private record ActiveSound(SoundInstance instance, boolean followsEntity, long endNanos) {
	}
}
