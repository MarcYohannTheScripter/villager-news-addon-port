package com.vnap.mixin.client;

import com.vnap.client.VillagerNewsRenderState;
import com.vnap.entity.VillagerNewsData;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.WeakHashMap;

@Mixin(VillagerRenderer.class)
public abstract class VillagerRendererMixin {
	private static final Map<Villager, StableVillagerData> VNAP$STABLE_DATA = new WeakHashMap<>();

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/npc/villager/Villager;Lnet/minecraft/client/renderer/entity/state/VillagerRenderState;F)V", at = @At("TAIL"))
	private void vnap$extractRenderState(Villager villager, VillagerRenderState state, float partialTick, CallbackInfo ci) {
		StableVillagerData stableData = VNAP$STABLE_DATA.computeIfAbsent(villager,
			ignored -> new StableVillagerData(state.villagerData));
		state.villagerData = stableData.resolve(state.villagerData, villager.tickCount);
		VillagerNewsData data = (VillagerNewsData) villager;
		VillagerNewsRenderState renderState = (VillagerNewsRenderState) state;
		renderState.vnap$setSignMessage(data.vnap$signMessage());
		renderState.vnap$setSignType(data.vnap$signType());
	}

	private static final class StableVillagerData {
		private VillagerData displayed;
		private VillagerData pending;
		private int pendingSince;

		private StableVillagerData(VillagerData displayed) {
			this.displayed = displayed;
		}

		private VillagerData resolve(VillagerData current, int tick) {
			if (current.equals(displayed)) {
				pending = null;
				return displayed;
			}
			if (!current.equals(pending)) {
				pending = current;
				pendingSince = tick;
				return displayed;
			}
			if (tick - pendingSince >= 2) {
				displayed = current;
				pending = null;
			}
			return displayed;
		}
	}
}
