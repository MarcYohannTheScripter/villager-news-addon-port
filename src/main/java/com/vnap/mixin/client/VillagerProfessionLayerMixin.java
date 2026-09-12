package com.vnap.mixin.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.layers.VillagerProfessionLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Java creates a separate vanilla no-hat villager model for some clothing
 * passes. That model does not contain the EMF replacement hierarchy, so its
 * torso, arms, and legs intersect the imported Villager News model. Reusing
 * the renderer's main model keeps every texture pass on the same geometry.
 */
@Mixin(VillagerProfessionLayer.class)
abstract class VillagerProfessionLayerMixin {
	@SuppressWarnings("rawtypes")
	@Redirect(
		method = "submit",
		at = @At(
			value = "FIELD",
			target = "Lnet/minecraft/client/renderer/entity/layers/VillagerProfessionLayer;noHatModel:Lnet/minecraft/client/model/EntityModel;"
		)
	)
	private EntityModel vnap$alignAdultClothingWithEmfModel(VillagerProfessionLayer layer) {
		return layer.getParentModel();
	}
}
