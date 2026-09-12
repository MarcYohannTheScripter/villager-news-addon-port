package com.vnap.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vnap.network.DialogueAnimationPayload;
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
	private static final Map<UUID, ActiveDialogue> ACTIVE = new ConcurrentHashMap<>();
	private static final float BLEND_SECONDS = 0.3F;
	private static final float MOUTH_BLEND_SECONDS = 0.15F;
	private static float framesPerSecond = 24.0F;

	private DialogueAnimationState() {
	}

	static void load() throws IOException {
		try (InputStream stream = DialogueAnimationState.class.getResourceAsStream(DATA_PATH)) {
			if (stream == null) throw new IOException("Missing " + DATA_PATH);
			JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
			framesPerSecond = root.get("framesPerSecond").getAsFloat();
			for (JsonElement gestureElement : root.getAsJsonArray("gestures")) {
				JsonObject value = gestureElement.getAsJsonObject();
				Map<String, float[]> tracks = new HashMap<>();
				for (Map.Entry<String, JsonElement> track : value.getAsJsonObject("tracks").entrySet()) {
					JsonArray samples = track.getValue().getAsJsonArray();
					float[] values = new float[samples.size()];
					for (int index = 0; index < values.length; index++) values[index] = samples.get(index).getAsFloat();
					tracks.put(track.getKey(), values);
				}
				GESTURES.add(new Gesture(value.get("duration").getAsFloat(), Map.copyOf(tracks)));
			}
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

	static List<String> animationVariables() {
		List<String> variables = new ArrayList<>(TARGETS.length * COMPONENTS.length);
		for (String target : TARGETS) {
			for (String component : COMPONENTS) variables.add("vnap_" + target + "_" + component);
		}
		return variables;
	}

	static void start(DialogueAnimationPayload payload) {
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

	static float transform(String variableName) {
		ActiveDialogue active = active();
		boolean scale = variableName.endsWith("_sx") || variableName.endsWith("_sy") || variableName.endsWith("_sz");
		float fallback = scale ? 1.0F : 0.0F;
		if (active == null) return fallback;
		return active.timeline().transformAt(
			active.elapsedSeconds(), variableName.substring("vnap_".length()), fallback
		);
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
		// Villager skin, biome, profession, and badge textures are separate render
		// passes. Freeze their suppliers to one time sample for this entity frame;
		// otherwise fast poses put each texture layer on a slightly different bone
		// transform and expose the skin between the clothes.
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
