package com.vnap.mixin.client;

import com.vnap.client.RainbowNoseRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import traben.entity_model_features.models.parts.EMFModelPart;
import traben.entity_model_features.models.parts.EMFModelPartCustom;

@Mixin(value = EMFModelPart.class, remap = false)
public abstract class EMFModelPartMixin {
	@Shadow public Identifier textureOverride;
	@Unique private static final Identifier VNAP$RAINBOW_TEXTURE = Identifier.fromNamespaceAndPath("villager-news-addon-port", "textures/entity/rainbow_nose.png");
	@Unique private Identifier vnap$previousTexture;
	@Unique private boolean vnap$rainbowTextureActive;

	@Inject(method = "render", at = @At("HEAD"))
	private void vnap$beginRainbowTexture(PoseStack poseStack, VertexConsumer vertices, int light, int overlay, int color, CallbackInfo ci) {
		if (!vnap$isRainbowNose()) return;
		vnap$previousTexture = textureOverride;
		textureOverride = VNAP$RAINBOW_TEXTURE;
		vnap$rainbowTextureActive = true;
	}

	@Inject(method = "render", at = @At("RETURN"))
	private void vnap$endRainbowTexture(PoseStack poseStack, VertexConsumer vertices, int light, int overlay, int color, CallbackInfo ci) {
		if (!vnap$rainbowTextureActive) return;
		textureOverride = vnap$previousTexture;
		vnap$previousTexture = null;
		vnap$rainbowTextureActive = false;
	}

	@ModifyVariable(method = "compile", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int vnap$rainbowLight(int light) {
		return vnap$isRainbowNose() ? 0xF000F0 : light;
	}

	@ModifyVariable(method = "compile", at = @At("HEAD"), argsOnly = true, ordinal = 2)
	private int vnap$rainbowNose(int color) {
		if (!vnap$isRainbowNose()) return color;
		float phase = Mth.positiveModulo(RainbowNoseRenderState.cycleSeconds(), 6.0F);
		float x = 1.0F - Math.abs(phase % 2.0F - 1.0F);
		float red = phase < 1.0F ? 1.0F : phase < 2.0F ? x : phase < 4.0F ? 0.0F : phase < 5.0F ? x : 1.0F;
		float green = phase < 1.0F ? x : phase < 3.0F ? 1.0F : phase < 4.0F ? x : 0.0F;
		float blue = phase < 2.0F ? 0.0F : phase < 3.0F ? x : phase < 5.0F ? 1.0F : x;
		int alpha = color >>> 24;
		int tintedRed = Math.round((color >>> 16 & 255) * red);
		int tintedGreen = Math.round((color >>> 8 & 255) * green);
		int tintedBlue = Math.round((color & 255) * blue);
		return alpha << 24 | tintedRed << 16 | tintedGreen << 8 | tintedBlue;
	}

	@Unique
	private boolean vnap$isRainbowNose() {
		return (Object) this instanceof EMFModelPartCustom part
			&& part.id.contains("villager_news_base_fgk6")
			&& RainbowNoseRenderState.active();
	}
}
