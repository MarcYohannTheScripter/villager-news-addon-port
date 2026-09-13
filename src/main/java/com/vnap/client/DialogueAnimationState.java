package com.vnap.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vnap.network.DialogueAnimationPayload;
import com.vnap.entity.VillagerNewsData;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import traben.entity_model_features.EMFAnimationApi;
import traben.entity_model_features.utils.EMFEntity;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

final class DialogueAnimationState {
	private static final String DATA_PATH = "/assets/villager-news-addon-port/dialogue_animations.json";
	private static final String[] TARGETS = {
		"root", "waist", "body", "head", "head_inner", "arms",
		"left_leg_root", "left_leg", "right_leg_root", "right_leg", "brow", "eye_group", "lower_face",
		"pupil_left", "pupil_right", "eye_left", "eye_right", "nose"
	};
	private static final String[] COMPONENTS = {"rx", "ry", "rz", "tx", "ty", "tz", "sx", "sy", "sz"};
	private static final Map<String, List<VariantTimeline>> TIMELINES = new HashMap<>();
	private static final List<Gesture> GESTURES = new ArrayList<>();
	private static final List<Gesture> IDLES = new ArrayList<>();
	private static final Map<UUID, ActiveDialogue> ACTIVE = new ConcurrentHashMap<>();
	private static final Map<UUID, IdleState> IDLE_STATES = new ConcurrentHashMap<>();
	private static final float BLEND_SECONDS = 0.3F;
	private static final float MOUTH_BLEND_SECONDS = 0.15F;
	private static Gesture locomotion = new Gesture(0.0F, Map.of());
	private static float framesPerSecond = 24.0F;

	private DialogueAnimationState() {
	}

	static void load() throws IOException {
		try (InputStream stream = DialogueAnimationState.class.getResourceAsStream(DATA_PATH)) {
			if (stream == null) throw new IOException("Missing " + DATA_PATH);
			JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
			framesPerSecond = root.get("framesPerSecond").getAsFloat();
			for (JsonElement gestureElement : root.getAsJsonArray("gestures")) {
				GESTURES.add(readGesture(gestureElement.getAsJsonObject()));
			}
			locomotion = readGesture(root.getAsJsonObject("locomotion"));
			for (JsonElement idleElement : root.getAsJsonArray("idles")) IDLES.add(readGesture(idleElement.getAsJsonObject()));
			for (Map.Entry<String, JsonElement> group : root.getAsJsonObject("groups").entrySet()) {
				List<VariantTimeline> variants = new ArrayList<>();
				for (JsonElement variantElement : group.getValue().getAsJsonArray()) {
					JsonObject variant = variantElement.getAsJsonObject();
					List<MouthFrame> mouth = new ArrayList<>();
					for (JsonElement frameElement : variant.getAsJsonArray("mouth")) {
						JsonArray frame = frameElement.getAsJsonArray();
						mouth.add(new MouthFrame(frame.get(0).getAsFloat(), frame.get(1).getAsFloat(), frame.get(2).getAsFloat(), frame.get(3).getAsFloat()));
					}
					List<GestureFrame> gestures = new ArrayList<>();
					for (JsonElement frameElement : variant.getAsJsonArray("gestures")) {
						JsonArray frame = frameElement.getAsJsonArray();
						gestures.add(new GestureFrame(frame.get(0).getAsFloat(), frame.get(1).getAsInt()));
					}
					variants.add(new VariantTimeline(List.copyOf(mouth), List.copyOf(gestures)));
				}
				TIMELINES.put(group.getKey(), List.copyOf(variants));
			}
		}
	}

	private static Gesture readGesture(JsonObject value) {
		Map<String, float[]> tracks = new HashMap<>();
		for (Map.Entry<String, JsonElement> track : value.getAsJsonObject("tracks").entrySet()) {
			JsonArray samples = track.getValue().getAsJsonArray();
			float[] values = new float[samples.size()];
			for (int index = 0; index < values.length; index++) values[index] = samples.get(index).getAsFloat();
			tracks.put(track.getKey(), values);
		}
		return new Gesture(value.get("duration").getAsFloat(), Map.copyOf(tracks));
	}

	static List<String> animationVariables() {
		List<String> variables = new ArrayList<>(TARGETS.length * COMPONENTS.length);
		for (String target : TARGETS) {
			for (String component : COMPONENTS) variables.add("vnap_" + target + "_" + component);
		}
		return variables;
	}

	static void tick(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.player == null) {
			ACTIVE.clear();
			IDLE_STATES.clear();
			return;
		}
		long now = System.nanoTime();
		ACTIVE.entrySet().removeIf(entry -> now > entry.getValue().endNanos());
		IDLE_STATES.keySet().removeIf(id -> minecraft.level.getEntity(id) == null);
	}

	static void start(DialogueAnimationPayload payload) {
		if (payload.groupId().isEmpty()) {
			ACTIVE.remove(payload.entityId());
			return;
		}
		List<VariantTimeline> variants = TIMELINES.get(payload.groupId());
		if (variants == null || payload.variantIndex() < 0 || payload.variantIndex() >= variants.size()) return;
		long now = System.nanoTime();
		VariantTimeline timeline = variants.get(payload.variantIndex());
		float audioSeconds = Math.max(1, payload.durationTicks()) / 20.0F;
		float totalSeconds = Math.max(audioSeconds + MOUTH_BLEND_SECONDS, timeline.poseEndSeconds());
		ACTIVE.put(payload.entityId(), new ActiveDialogue(
			now,
			now + (long) (audioSeconds * 1_000_000_000L),
			now + (long) (totalSeconds * 1_000_000_000L),
			timeline
		));
	}

	static float speaking() {
		ActiveDialogue active = active();
		return active == null ? 0.0F : active.speechWeight();
	}

	static float mouthOpen() {
		MouthFrame frame = mouthFrame();
		return frame == null ? 0.0F : frame.open();
	}

	static float mouthWidth() {
		MouthFrame frame = mouthFrame();
		return frame == null ? 1.0F : frame.width();
	}

	static float mouthClosed() {
		MouthFrame frame = mouthFrame();
		return frame == null ? 1.0F : frame.closed();
	}

	static float hasNose() {
		EMFEntity entity = EMFAnimationApi.getCurrentEntity();
		return entity instanceof Villager villager && ((VillagerNewsData) villager).vnap$hasNose() ? 1.0F : 0.0F;
	}

	static float cosmetic(int cosmetic) {
		EMFEntity entity = EMFAnimationApi.getCurrentEntity();
		return entity instanceof Villager villager && ((VillagerNewsData) villager).vnap$cosmetic() == cosmetic ? 1.0F : 0.0F;
	}

	static float transform(String variableName) {
		ActiveDialogue active = active();
		boolean scale = variableName.endsWith("_sx") || variableName.endsWith("_sy") || variableName.endsWith("_sz");
		float fallback = scale ? 1.0F : 0.0F;
		String trackName = variableName.substring("vnap_".length());
		float base = baseTransform(trackName, fallback, active != null);
		float dialogue = active == null ? fallback : active.timeline().transformAt(active.elapsedSeconds(), trackName, fallback);
		return scale ? base * dialogue : base + dialogue;
	}

	private static float baseTransform(String trackName, float fallback, boolean dialogueActive) {
		EMFEntity emfEntity = EMFAnimationApi.getCurrentEntity();
		if (!(emfEntity instanceof LivingEntity entity)
				|| !(entity instanceof Villager) && !(entity instanceof WanderingTrader)) return fallback;
		UUID id = entity.getUUID();
		float age = emfEntity.emf$age();
		float partialTick = age - (float) Math.floor(age);
		float speed = entity.walkAnimation.speed(partialTick);
		IdleState idle = IDLE_STATES.computeIfAbsent(id, ignored -> new IdleState());
		boolean moving = speed > 0.01F && entity.getDeltaMovement().horizontalDistanceSqr() > 0.0001;
		if (!entity.isSleeping() && entity.onGround() && moving && locomotion.duration() > 0.0F) {
			idle.block();
			float phase = entity.walkAnimation.position(partialTick) * 0.6662F / ((float) Math.PI * 2.0F);
			float cycle = phase - (float) Math.floor(phase);
			float value = locomotion.valueAt(cycle * locomotion.duration(), trackName, fallback);
			float weight = Math.min(1.0F, speed * 0.9F);
			return fallback + (value - fallback) * weight;
		}
		if (dialogueActive || entity.isSleeping() || !entity.onGround() || IDLES.isEmpty()) {
			idle.block();
			return fallback;
		}
		idle.unblock(age);
		return idle.valueAt(age, trackName, fallback);
	}

	private static MouthFrame mouthFrame() {
		ActiveDialogue active = active();
		return active == null || active.speechWeight() <= 0.0F
			? null
			: active.timeline().mouthAt(active.elapsedSeconds());
	}

	private static ActiveDialogue active() {
		EMFEntity entity = EMFAnimationApi.getCurrentEntity();
		if (entity == null || entity.etf$getUuid() == null) return null;
		UUID id = entity.etf$getUuid();
		ActiveDialogue value = ACTIVE.get(id);
		if (value == null) return null;
		value.beginFrame(entity.emf$age(), System.nanoTime());
		if (value.frameNanos() > value.endNanos()) {
			ACTIVE.remove(id, value);
			return null;
		}
		return value;
	}

	private record MouthFrame(float time, float open, float width, float closed) {
	}

	private record GestureFrame(float time, int gestureIndex) {
	}

	private record VariantTimeline(List<MouthFrame> mouth, List<GestureFrame> gestures) {
		MouthFrame mouthAt(float time) {
			MouthFrame selected = mouth.isEmpty() ? null : mouth.getFirst();
			for (MouthFrame frame : mouth) {
				if (frame.time() > time) break;
				selected = frame;
			}
			return selected;
		}

		float transformAt(float time, String trackName, float fallback) {
			int selected = -1;
			for (int index = 0; index < gestures.size(); index++) {
				if (gestures.get(index).time() > time) break;
				selected = index;
			}
			if (selected < 0) return fallback;

			GestureFrame current = gestures.get(selected);
			float currentValue = stateValue(current, time, trackName, fallback);
			float transitionTime = time - current.time();
			if (transitionTime >= BLEND_SECONDS) return currentValue;

			float previousValue = selected == 0
				? fallback
				: stateValue(gestures.get(selected - 1), time, trackName, fallback);
			return lerp(previousValue, currentValue, blendCurve(transitionTime / BLEND_SECONDS));
		}

		float poseEndSeconds() {
			if (gestures.isEmpty()) return 0.0F;
			GestureFrame last = gestures.getLast();
			if (last.gestureIndex() < 0 || last.gestureIndex() >= GESTURES.size()) {
				return last.time() + BLEND_SECONDS;
			}
			return last.time() + GESTURES.get(last.gestureIndex()).duration() + BLEND_SECONDS;
		}

		private static float stateValue(GestureFrame frame, float time, String trackName, float fallback) {
			if (frame.gestureIndex() < 0 || frame.gestureIndex() >= GESTURES.size()) return fallback;
			Gesture gesture = GESTURES.get(frame.gestureIndex());
			float localTime = Math.max(0.0F, time - frame.time());
			float value = sample(gesture, trackName, Math.min(localTime, gesture.duration()), fallback);
			if (localTime <= gesture.duration()) return value;
			float out = blendCurve((localTime - gesture.duration()) / BLEND_SECONDS);
			return lerp(value, fallback, out);
		}

		private static float sample(Gesture gesture, String trackName, float localTime, float fallback) {
			float[] samples = gesture.tracks().get(trackName);
			if (samples == null || samples.length == 0) return fallback;
			float sample = Math.max(0.0F, localTime) * framesPerSecond;
			int lower = Math.min(samples.length - 1, (int) Math.floor(sample));
			int upper = Math.min(samples.length - 1, lower + 1);
			float progress = Math.min(1.0F, sample - lower);
			return lerp(samples[lower], samples[upper], progress);
		}

		private static float blendCurve(float progress) {
			float clamped = Math.max(0.0F, Math.min(1.0F, progress));
			float sine = (float) Math.sin(clamped * Math.PI * 0.5);
			return sine * sine;
		}

		private static float lerp(float from, float to, float progress) {
			return from + (to - from) * progress;
		}
	}

	private record Gesture(float duration, Map<String, float[]> tracks) {
		float valueAt(float time, String trackName, float fallback) {
			return VariantTimeline.sample(this, trackName, Math.max(0.0F, Math.min(time, duration)), fallback);
		}
	}

	private static final class IdleState {
		private boolean blocked = true;
		private int activeIndex = -1;
		private int lastIndex = -1;
		private int blendFromIndex = -1;
		private float startTick;

		void block() {
			if (blocked) return;
			blocked = true;
			if (activeIndex >= 0) lastIndex = activeIndex;
			activeIndex = -1;
			blendFromIndex = -1;
		}

		void unblock(float tick) {
			if (!blocked) return;
			blocked = false;
			startNext(tick, -1);
		}

		float valueAt(float tick, String trackName, float fallback) {
			if (activeIndex < 0) startNext(tick, -1);
			Gesture active = IDLES.get(activeIndex);
			float elapsed = (tick - startTick) / 20.0F;
			if (elapsed > active.duration()) {
				float overrunTicks = (elapsed - active.duration()) * 20.0F;
				startNext(tick - overrunTicks, activeIndex);
				active = IDLES.get(activeIndex);
				elapsed = (tick - startTick) / 20.0F;
			}
			float value = active.valueAt(elapsed, trackName, fallback);
			if (elapsed >= BLEND_SECONDS) return value;
			float previous = blendFromIndex < 0 ? fallback : IDLES.get(blendFromIndex)
				.valueAt(IDLES.get(blendFromIndex).duration(), trackName, fallback);
			return VariantTimeline.lerp(previous, value, VariantTimeline.blendCurve(elapsed / BLEND_SECONDS));
		}

		private void startNext(float tick, int blendFromIndex) {
			int next = ThreadLocalRandom.current().nextInt(IDLES.size());
			if (IDLES.size() > 1 && next == lastIndex) next = (next + 1) % IDLES.size();
			this.blendFromIndex = blendFromIndex;
			activeIndex = next;
			lastIndex = next;
			startTick = tick;
		}
	}

	private static final class ActiveDialogue {
		private final long startNanos;
		private final long audioEndNanos;
		private final long endNanos;
		private final VariantTimeline timeline;
		private int frameAgeBits = Integer.MIN_VALUE;
		private long frameNanos;

		private ActiveDialogue(long startNanos, long audioEndNanos, long endNanos, VariantTimeline timeline) {
			this.startNanos = startNanos;
			this.audioEndNanos = audioEndNanos;
			this.endNanos = endNanos;
			this.timeline = timeline;
			this.frameNanos = startNanos;
		}

		void beginFrame(float entityAge, long now) {
			int ageBits = Float.floatToIntBits(entityAge);
			if (ageBits == frameAgeBits) return;
			frameAgeBits = ageBits;
			frameNanos = now;
		}

		long frameNanos() {
			return frameNanos;
		}

		long endNanos() {
			return endNanos;
		}

		VariantTimeline timeline() {
			return timeline;
		}

		float elapsedSeconds() {
			return (frameNanos - startNanos) / 1_000_000_000.0F;
		}

		float speechWeight() {
			long now = frameNanos;
			float fadeIn = (now - startNanos) / (MOUTH_BLEND_SECONDS * 1_000_000_000.0F);
			float fadeOut = (endNanos - now) / (MOUTH_BLEND_SECONDS * 1_000_000_000.0F);
			if (now <= audioEndNanos) fadeOut = 1.0F;
			else fadeOut = (audioEndNanos + (long) (MOUTH_BLEND_SECONDS * 1_000_000_000L) - now)
				/ (MOUTH_BLEND_SECONDS * 1_000_000_000.0F);
			return VariantTimeline.blendCurve(Math.min(fadeIn, fadeOut));
		}
	}
}
