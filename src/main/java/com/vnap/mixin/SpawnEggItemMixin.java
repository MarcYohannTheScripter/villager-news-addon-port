package com.vnap.mixin;

import com.vnap.dialogue.ContextualDialogueController;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(SpawnEggItem.class)
public abstract class SpawnEggItemMixin {
	@Inject(method = "spawnOffspringFromSpawnEgg", at = @At("RETURN"))
	private static void vnap$spawnOffspringFromSpawnEgg(Player player, Mob parent, EntityType<? extends Mob> type,
			ServerLevel level, Vec3 position, ItemStack stack, CallbackInfoReturnable<Optional<Mob>> cir) {
		cir.getReturnValue().ifPresent(entity -> {
			if (entity instanceof Villager villager) {
				ContextualDialogueController.onBabySpawnedFromEgg(villager, player);
			}
		});
	}
}