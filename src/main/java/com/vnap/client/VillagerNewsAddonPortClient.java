package com.vnap.client;

import com.vnap.VillagerNewsAddonPort;
import com.vnap.network.DialogueAnimationPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import traben.entity_model_features.EMFAnimationApi;

import java.io.IOException;
import java.util.function.Supplier;

public final class VillagerNewsAddonPortClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		try {
			DialogueAnimationState.load();
			registerFloat("vnap_speaking", DialogueAnimationState::speaking, "Whether the Villager News character is speaking");
			registerFloat("vnap_mouth_open", DialogueAnimationState::mouthOpen, "Current Villager News mouth opening");
			registerFloat("vnap_mouth_width", DialogueAnimationState::mouthWidth, "Current Villager News mouth width");
			registerFloat("vnap_mouth_closed", DialogueAnimationState::mouthClosed, "Current Villager News closed-mouth layer");
			for (String variable : DialogueAnimationState.animationVariables()) {
				registerFloat(variable, () -> DialogueAnimationState.transform(variable), "Synchronized Villager News dialogue transform");
			}
		} catch (IOException | RuntimeException exception) {
			throw new IllegalStateException("Could not load Villager News animations", exception);
		} catch (Exception exception) {
			throw new IllegalStateException("Could not register Villager News EMF animation variables", exception);
		}

		ClientPlayNetworking.registerGlobalReceiver(DialogueAnimationPayload.TYPE, (payload, context) ->
			context.client().execute(() -> DialogueAnimationState.start(payload))
		);
		VillagerNewsAddonPort.LOGGER.info("Registered synchronized EMF facial and dialogue animations");
	}

	private static void registerFloat(String name, Supplier<Float> supplier, String description) throws Exception {
		EMFAnimationApi.registerSingletonAnimationVariable(VillagerNewsAddonPort.MOD_ID, name, description, supplier);
	}
}
