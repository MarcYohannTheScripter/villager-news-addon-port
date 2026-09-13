package com.vnap.mixin.client;

import com.vnap.client.VillagerNewsRenderState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(VillagerRenderState.class)
public abstract class VillagerRenderStateMixin implements VillagerNewsRenderState {
	@Unique
	private int vnap$signMessage = -1;
	@Unique
	private int vnap$signType = -1;

	@Override
	public int vnap$signMessage() {
		return vnap$signMessage;
	}

	@Override
	public void vnap$setSignMessage(int value) {
		vnap$signMessage = value;
	}

	@Override
	public int vnap$signType() {
		return vnap$signType;
	}

	@Override
	public void vnap$setSignType(int value) {
		vnap$signType = value;
	}
}
