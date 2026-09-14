package com.vnap.client;

import com.vnap.VillagerNewsAddonPort;
import com.vnap.item.VillagerNewsItems;
import com.vnap.network.DialogueAnimationPayload;
import com.vnap.network.VillagerNewsSettingsPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import traben.entity_model_features.EMFAnimationApi;

import java.io.IOException;
import java.util.function.Supplier;

public final class VillagerNewsAddonPortClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		VillagerNewsClientSettings.load();
		try {
			DialogueAnimationState.load();
			registerFloat("vnap_speaking", DialogueAnimationState::speaking, "Whether the Villager News character is speaking");
			registerFloat("vnap_mouth_open", DialogueAnimationState::mouthOpen, "Current Villager News mouth opening");
			registerFloat("vnap_mouth_width", DialogueAnimationState::mouthWidth, "Current Villager News mouth width");
			registerFloat("vnap_mouth_closed", DialogueAnimationState::mouthClosed, "Current Villager News closed-mouth layer");
			registerFloat("vnap_has_nose", DialogueAnimationState::hasNose, "Villager News nose visibility");
			registerFloat("vnap_cosmetic_mayor_hat", () -> DialogueAnimationState.cosmetic(1), "Villager News mayor hat visibility");
			registerFloat("vnap_cosmetic_helmet", () -> DialogueAnimationState.cosmetic(2), "Villager News helmet visibility");
			registerFloat("vnap_cosmetic_microphone", () -> DialogueAnimationState.cosmetic(3), "Villager News microphone visibility");
			registerFloat("vnap_cosmetic_moustache", () -> DialogueAnimationState.cosmetic(4), "Villager News moustache visibility");
			for (String variable : DialogueAnimationState.animationVariables()) {
				registerFloat(variable, () -> DialogueAnimationState.transform(variable), "Synchronized Villager News dialogue transform");
			}
		} catch (IOException | RuntimeException exception) {
			throw new IllegalStateException("Could not load Villager News animations", exception);
		} catch (Exception exception) {
			throw new IllegalStateException("Could not register Villager News EMF animation variables", exception);
		}

		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityType == EntityTypes.VILLAGER && entityRenderer instanceof VillagerRenderer villagerRenderer) {
				helper.register(new VillagerNewsSignLayer(villagerRenderer));
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(DialogueAnimationPayload.TYPE, (payload, context) ->
			context.client().execute(() -> {
				DialogueSoundState.start(payload);
				DialogueAnimationState.start(payload);
				DialogueSubtitleState.start(payload);
			})
		);
		ClientPlayNetworking.registerGlobalReceiver(VillagerNewsSettingsPayload.TYPE, (payload, context) ->
			context.client().execute(() -> VillagerNewsSettingsState.apply(payload))
		);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			DialogueSoundState.clear(client);
			DialogueAnimationState.clear();
			DialogueSubtitleState.clear();
			VillagerNewsSettingsState.reset();
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!level.isClientSide()) return InteractionResult.PASS;
			if (player.getItemInHand(hand).getItem() != VillagerNewsItems.HANDBOOK) return InteractionResult.PASS;
			Minecraft.getInstance().setScreenAndShow(new HandbookScreen());
			return InteractionResult.SUCCESS;
		});
		DialogueSubtitleState.register();
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			DialogueSoundState.tick(client);
			DialogueAnimationState.tick(client);
			DialogueSubtitleState.tick(client);
		});
		VillagerNewsAddonPort.LOGGER.info("Registered synchronized EMF facial and dialogue animations");
	}

	private static void registerFloat(String name, Supplier<Float> supplier, String description) throws Exception {
		EMFAnimationApi.registerSingletonAnimationVariable(VillagerNewsAddonPort.MOD_ID, name, description, supplier);
	}
}
