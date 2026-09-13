package com.vnap.mixin;

import com.vnap.dialogue.ContextualDialogueController;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class VillagerSleepMixin {
	@Inject(method = "startSleeping", at = @At("HEAD"), cancellable = true)
	private void vnap$delaySleepUntilBedtimeLineFinishes(BlockPos bedPos, CallbackInfo ci) {
		if ((Object) this instanceof Villager villager && ContextualDialogueController.delayVillagerSleep(villager, bedPos)) ci.cancel();
	}
}
