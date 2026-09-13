package com.vnap.client;

import java.util.UUID;

public final class RainbowNoseRenderState {
	private static final ThreadLocal<State> CURRENT = ThreadLocal.withInitial(() -> new State(false, 0.0F, 0.0F));

	private RainbowNoseRenderState() {
	}

	static void update(boolean active, float age, UUID id) {
		float offset = id == null ? 0.0F : Math.floorMod(id.hashCode(), 20_000) / 1000.0F;
		CURRENT.set(new State(active, age / 20.0F, offset));
	}

	public static boolean active() {
		return CURRENT.get().active();
	}

	public static float cycleSeconds() {
		State state = CURRENT.get();
		return state.lifeSeconds() + state.offsetSeconds();
	}

	private record State(boolean active, float lifeSeconds, float offsetSeconds) {
	}
}
