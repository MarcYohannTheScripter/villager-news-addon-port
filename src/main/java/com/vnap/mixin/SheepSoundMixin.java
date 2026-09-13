package com.vnap.mixin;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.animal.sheep.Sheep;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Sheep.class)
public abstract class SheepSoundMixin {
	@Inject(method = "getAmbientSound", at = @At("HEAD"), cancellable = true)
	private void vnap$removeWoolyAmbientSound(CallbackInfoReturnable<SoundEvent> cir) {
		if (vnap$isWooly()) cir.setReturnValue(SoundEvents.EMPTY);
	}

	@Inject(method = "getHurtSound", at = @At("HEAD"), cancellable = true)
	private void vnap$removeWoolyHurtSound(DamageSource source, CallbackInfoReturnable<SoundEvent> cir) {
		if (vnap$isWooly()) cir.setReturnValue(SoundEvents.EMPTY);
	}

	@Inject(method = "getDeathSound", at = @At("HEAD"), cancellable = true)
	private void vnap$removeWoolyDeathSound(CallbackInfoReturnable<SoundEvent> cir) {
		if (vnap$isWooly()) cir.setReturnValue(SoundEvents.EMPTY);
	}

	private boolean vnap$isWooly() {
		String name = ((Sheep) (Object) this).getName().getString();
		return name.equalsIgnoreCase("Wooly") || name.equalsIgnoreCase("Wooly The Sheep");
	}
}
