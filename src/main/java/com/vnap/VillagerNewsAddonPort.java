package com.vnap;

import com.vnap.command.DialogueTestCommand;
import com.vnap.config.VillagerNewsBuildSettings;
import com.vnap.config.VillagerNewsSettings;
import com.vnap.dialogue.ContextualDialogueController;
import com.vnap.dialogue.DialogueCatalog;
import com.vnap.item.VillagerNewsItems;
import com.vnap.network.DialogueAnimationPayload;
import com.vnap.network.VillagerNewsSettingsNetwork;
import com.vnap.network.VillagerNewsSettingsPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VillagerNewsAddonPort implements ModInitializer {
	public static final String MOD_ID = "villager-news-addon-port";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		VillagerNewsItems.register();
		PayloadTypeRegistry.clientboundPlay().register(DialogueAnimationPayload.TYPE, DialogueAnimationPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(VillagerNewsSettingsPayload.TYPE, VillagerNewsSettingsPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(VillagerNewsSettingsPayload.TYPE, VillagerNewsSettingsPayload.CODEC);
		VillagerNewsSettings.load();
		VillagerNewsSettingsNetwork.register();
		DialogueCatalog.register();
		ContextualDialogueController.register();
		if (VillagerNewsBuildSettings.dialogueTestCommand()) DialogueTestCommand.register();
		LOGGER.info("Villager News models, textures, and contextual dialogue are ready.");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
