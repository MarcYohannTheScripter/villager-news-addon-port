package com.vnap.item;

import com.vnap.VillagerNewsAddonPort;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public final class VillagerNewsItems {
	public static final ResourceKey<CreativeModeTab> CREATIVE_TAB_KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
		VillagerNewsAddonPort.id("items"));
	public static final Item HANDBOOK = register("handbook", properties -> new Item(properties.stacksTo(1)));
	public static final Item MAYOR_HAT = register("mayor_hat", properties -> new Item(properties.stacksTo(1).equippable(EquipmentSlot.HEAD)));
	public static final Item MICROPHONE = register("microphone", properties -> new Item(properties.stacksTo(1)));
	public static final Item MOUSTACHE = register("moustache", properties -> new Item(properties.stacksTo(1).equippable(EquipmentSlot.HEAD)));
	public static final Item TESTIFICATE_MAN_HELMET = register("testificate_man_helmet", properties -> new Item(properties.stacksTo(1).equippable(EquipmentSlot.HEAD)));
	public static final Item VILLAGER_NOSE = register("villager_nose", properties -> new Item(properties.stacksTo(1).equippable(EquipmentSlot.HEAD)));
	private static final Map<Item, Integer> COSMETICS = new LinkedHashMap<>();

	static {
		COSMETICS.put(MAYOR_HAT, 1);
		COSMETICS.put(TESTIFICATE_MAN_HELMET, 2);
		COSMETICS.put(MICROPHONE, 3);
		COSMETICS.put(MOUSTACHE, 4);
	}

	private VillagerNewsItems() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, CREATIVE_TAB_KEY, FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.villager-news-addon-port.items"))
			.icon(() -> new ItemStack(HANDBOOK))
			.displayItems((parameters, output) -> {
				output.accept(HANDBOOK);
				output.accept(MAYOR_HAT);
				output.accept(TESTIFICATE_MAN_HELMET);
				output.accept(MICROPHONE);
				output.accept(MOUSTACHE);
				output.accept(VILLAGER_NOSE);
			})
			.build());
	}

	public static int cosmetic(Item item) {
		return COSMETICS.getOrDefault(item, 0);
	}

	public static Item cosmeticItem(int cosmetic) {
		return COSMETICS.entrySet().stream().filter(entry -> entry.getValue() == cosmetic)
			.map(Map.Entry::getKey).findFirst().orElse(null);
	}

	private static Item register(String path, Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, VillagerNewsAddonPort.id(path));
		Item item = factory.apply(new Item.Properties().setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

}
