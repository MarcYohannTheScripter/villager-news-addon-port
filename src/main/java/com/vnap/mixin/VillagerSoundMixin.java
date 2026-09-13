package com.vnap.mixin;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Villager.class)
public abstract class VillagerSoundMixin {
	@Inject(method = "getAmbientSound", at = @At("HEAD"), cancellable = true)
	private void vnap$removeUnreachableAmbientSound(CallbackInfoReturnable<SoundEvent> cir) {
		if (vnap$isUnreachable()) cir.setReturnValue(SoundEvents.EMPTY);
	}

	@Inject(method = "getHurtSound", at = @At("HEAD"), cancellable = true)
	private void vnap$removeUnreachableHurtSound(DamageSource source, CallbackInfoReturnable<SoundEvent> cir) {
		if (vnap$isUnreachable()) cir.setReturnValue(SoundEvents.EMPTY);
	}

	@Inject(method = "getDeathSound", at = @At("HEAD"), cancellable = true)
	private void vnap$removeVanillaDeathSound(CallbackInfoReturnable<SoundEvent> cir) {
		cir.setReturnValue(SoundEvents.EMPTY);
	}

	private boolean vnap$isUnreachable() {
		String name = ((Villager) (Object) this).getName().getString();
		return name.equalsIgnoreCase("Villager Unreachable") || name.equalsIgnoreCase("Can't Catch Me!");
	}
}
