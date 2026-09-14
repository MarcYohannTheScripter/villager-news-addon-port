package com.vnap.dialogue;

import com.vnap.config.VillagerNewsSettings;
import com.vnap.entity.VillagerNewsData;
import com.vnap.item.VillagerNewsItems;
import com.vnap.network.DialogueAnimationNetwork;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class ContextualDialogueController {
	public static final String DIALOGUE_TEST_TAG = "vnap_dialogue_test";
	private static final double OBSERVER_RANGE = 16.0;
	private static final double NEARBY_SUBJECT_RANGE = 8.0;
	private static final long SHORT_COOLDOWN = 20L * 45L;
	private static final long LONG_COOLDOWN = 20L * 150L;
	private static final Map<String, Long> COOLDOWNS = new HashMap<>();
	private static final Map<UUID, Long> BUSY_UNTIL = new HashMap<>();
	private static final Map<UUID, ActiveSound> ACTIVE_SOUNDS = new HashMap<>();
	private static final Map<UUID, PlayerObservation> PLAYER_OBSERVATIONS = new HashMap<>();
	private static final Map<UUID, Integer> PLAYER_DEATHS = new HashMap<>();
	private static final Map<UUID, Boolean> LAST_SLEEPING = new HashMap<>();
	private static final Map<UUID, Boolean> LAST_TRADER_INVISIBLE = new HashMap<>();
	private static final Map<UUID, VillagerSnapshot> VILLAGER_STATES = new HashMap<>();
	private static final Map<UUID, Set<String>> CONDITION_HISTORY = new HashMap<>();
	private static final Map<UUID, Integer> CONDITION_CURSORS = new HashMap<>();
	private static final Map<UUID, PendingConditionRelief> PENDING_CONDITION_RELIEF = new HashMap<>();
	private static final Map<UUID, Map<String, Integer>> VILLAGER_INVENTORIES = new HashMap<>();
	private static final Map<UUID, SpeechTarget> SPEECH_TARGETS = new HashMap<>();
	private static final Map<String, List<Integer>> SHARED_RECENT_VARIANTS = new HashMap<>();
	private static final Map<UUID, Long> NO_WORKSTATION_SINCE = new HashMap<>();
	private static final Map<UUID, Long> LAST_DANGER = new HashMap<>();
	private static final Map<UUID, Long> NO_BELL_SINCE = new HashMap<>();
	private static final Map<UUID, TradeSession> ACTIVE_TRADES = new HashMap<>();
	private static final Map<UUID, UnreachableState> UNREACHABLE_STATES = new HashMap<>();
	private static final Set<UUID> ACTIVE_PLAYER_ENCOUNTERS = new HashSet<>();
	private static final Map<UUID, PendingSleep> PENDING_SLEEP = new HashMap<>();
	private static final Map<UUID, PendingWake> PENDING_WAKE = new HashMap<>();
	private static final List<PendingBell> PENDING_BELLS = new ArrayList<>();
	private static final List<PendingBellReaction> PENDING_BELL_REACTIONS = new ArrayList<>();
	private static final Map<UUID, UUID> WAKE_SOURCES = new HashMap<>();
	private static final Set<UUID> SLEEP_BYPASS = new HashSet<>();
	private static final Set<UUID> INTERRUPTED_SLEEP = new HashSet<>();
	private static final List<PendingSpeech> PENDING_SPEECH = new ArrayList<>();
	private static final Map<String, Long> LAST_LEVEL_TIME = new HashMap<>();
	private static final Map<String, Difficulty> LAST_DIFFICULTY = new HashMap<>();
	private static final Map<String, Integer> PAIR_TICKS = new HashMap<>();
	private static final String NATURAL_SPECIAL_TAG = "vnap_natural_special";
	private static final String SPECIAL_OBJECTIVE = "vnap_special";
	private static final String SPECIAL_X_OBJECTIVE = "vnap_special_x";
	private static final String SPECIAL_Z_OBJECTIVE = "vnap_special_z";
	private static final Map<String, String> NATURAL_SPECIAL_NAMES = Map.of(
		"mayor", "The Mayor",
		"testificate", "Testificate Man",
		"number_5", "Villager #5",
		"number_9", "Villager #9",
		"unreachable", "Can't Catch Me!",
		"wooly", "Wooly The Sheep"
	);
	private static final Set<String> SPECIAL_NAMES = Set.of(
		"Mayor Villager", "The Mayor", "Testificate Man", "Villager #5", "Villager #9",
		"Villager Unreachable", "Can't Catch Me!", "Wooly The Sheep"
	);
	private static final Set<String> MOBILE_DIALOGUES = Set.of(
		"huhcbd", "gesjov", "gacgtq", "hmadgp", "nkcoqb", "qhpyaw", "uveohs", "caykki",
		"swomdw", "rtikom", "hfmwvf", "mytmrk", "ikrwzy", "fcbygh", "etkxko", "elryje",
		"rogpvp", "igebly", "vnaodx", "yzqpvi", "nsxmkr", "cifbit", "wyvzhk", "rueszy",
		"yjctyw", "qqyjjg", "hpnsfu", "vevdkl", "ahcvzd", "ecslqo", "ssbhiv", "ltdnvy",
		"fzoqwd", "behifz", "wrbvvp", "asuufu", "eyiraw", "ncyeaw", "uzdxum", "lpuocy",
		"slbqfwbayahw"
	);
	private static final Set<String> BABY_DIALOGUES = Set.of(
		"abfwiv", "aezdiy", "ahcvzd", "cmrqhw", "durjjd", "ecslqo", "fzyrfm", "ggitzq",
		"gotjxf", "gzsztp", "hbalps", "hcdvqm", "jfuftm", "lgjtnf", "mqnapy", "msemoe",
		"nxalcz", "qrdzmt", "rfnirh", "saxuwk", "svdjdk", "vbclem", "vhwksn", "wkwcrf",
		"wsxfok", "wtuguc", "zeykfp", "cxeziv", "riezum", "rlkdqd"
	);
	private static final Set<String> COSMETIC_RECIPIENT_DIALOGUES = Set.of(
		"wurmgu", "inirxg", "ozxzla", "cxeziv", "riezum", "rlkdqd"
	);
	private static final Set<String> ONGOING_DAMAGE_DIALOGUES = Set.of(
		"elryje", "rogpvp", "etkxko", "igebly", "vnaodx"
	);
	private static final Set<String> DAMAGE_LOCK_DIALOGUES = Set.of(
		"huhcbd", "gesjov", "gacgtq", "hmadgp", "nkcoqb", "qhpyaw", "uveohs", "caykki",
		"swomdw", "rtikom", "hfmwvf", "mytmrk", "ikrwzy", "fcbygh", "elryje", "rogpvp",
		"etkxko", "igebly", "vnaodx", "yzqpvi", "nsxmkr", "cifbit", "wyvzhk", "rueszy",
		"yjctyw", "qqyjjg", "hpnsfu", "vevdkl", "ahcvzd", "ecslqo", "ssbhiv", "ltdnvy",
		"fzoqwd", "behifz", "wrbvvp", "asuufu", "eyiraw", "ncyeaw", "onindz", "xemyaj", "yebifs"
	);
	private static final List<List<String>> WANDERING_CONVERSATIONS = List.of(
		List.of("gmrypkswxeva", "gmrypkbayahw", "gmrypkmudlec"),
		List.of("gmrypkoallbt", "gmrypkfobzlt", "gmrypkcljvls"),
		List.of("gmrypkhiqnpi", "gmrypkvkuidc", "gmrypkhnvsiu", "gmrypkvswnrg"),
		List.of("gmrypkmwtiaf", "gmrypkgougka")
	);
	private static final List<String> CAMPFIRE_CONVERSATION = List.of(
		"wrswgiswxeva", "wrswgibayahw", "wrswgimudlec", "wrswgitvewwu", "wrswgisrlwzw", "wrswgicsmkgk"
	);
	private static final List<String> GOSSIP_CONVERSATION = List.of(
		"wrjbddswxeva", "wrjbddbayahw", "wrjbddmudlec", "wrjbddtvewwu", "wrjbddsrlwzw"
	);
	private static final List<List<String>> ONE_MISSING_NOSE_CONVERSATIONS = List.of(
		List.of("bygaxwswxeva", "bygaxwbayahw"),
		List.of("bygaxwoallbt", "bygaxwfobzlt", "bygaxwcljvls"),
		List.of("bygaxwhiqnpi"),
		List.of("bygaxwmwtiaf")
	);
	private static final List<List<String>> TWO_MISSING_NOSES_CONVERSATIONS = List.of(
		List.of("loicswswxeva", "loicswbayahw"),
		List.of("loicswrotbcq"),
		List.of("loicswhiqnpi", "loicswvkuidc", "loicswhnvsiu")
	);
	private static final Map<String, String> NEARBY_ENTITY_DIALOGUES = Map.ofEntries(
		Map.entry("allay", "rnlher"), Map.entry("armor_stand", "ckniqq"), Map.entry("bat", "ozmthf"),
		Map.entry("bee", "rbkjsr"), Map.entry("cave_spider", "gtmfpl"),
		Map.entry("bogged", "nsosix"), Map.entry("camel", "turlrl"), Map.entry("cat", "ynxhfb"),
		Map.entry("chicken", "hggexx"), Map.entry("cow", "lvzfcv"), Map.entry("creaking", "nwlcij"),
		Map.entry("creeper", "odwhzm"), Map.entry("dolphin", "aqtshb"), Map.entry("drowned", "atwycp"),
		Map.entry("enderman", "yeqxvm"), Map.entry("frog", "pguaqp"), Map.entry("horse", "yazvzs"),
		Map.entry("husk", "gcoysc"), Map.entry("llama", "ysbfqu"), Map.entry("trader_llama", "ysbfqu"),
		Map.entry("panda", "swewsr"), Map.entry("parrot", "vapupl"), Map.entry("phantom", "nwzvkb"),
		Map.entry("pig", "jqdeef"), Map.entry("rabbit", "spfefr"), Map.entry("sheep", "vxycol"),
		Map.entry("skeleton", "lqzdqk"), Map.entry("slime", "rzvitn"), Map.entry("sniffer", "tqishj"),
		Map.entry("spider", "gtmfpl"), Map.entry("stray", "bxbibd"), Map.entry("turtle", "neoxpu"),
		Map.entry("warden", "jicosq"), Map.entry("witch", "lwcrnt"), Map.entry("wither", "satsrf"),
		Map.entry("wolf", "vvntcf"), Map.entry("zombie", "dortcb"), Map.entry("zombie_villager", "xtooxu"),
		Map.entry("zombified_piglin", "wboncy"), Map.entry("copper_golem", "ktdshy"),
		Map.entry("snow_golem", "kxjegd"), Map.entry("iron_golem", "cuchwi"),
		Map.entry("ender_dragon", "xxjkmo"), Map.entry("happy_ghast", "lxvofx"),
		Map.entry("polar_bear", "toolzx"), Map.entry("sulfur_cube", "dmcjmd"),
		Map.entry("cod", "trkugw"), Map.entry("salmon", "trkugw"), Map.entry("pufferfish", "trkugw"),
		Map.entry("tropical_fish", "trkugw")
	);
	private static final Map<String, String> BABY_ENTITY_DIALOGUES = Map.ofEntries(
		Map.entry("bee", "qqtnlm"), Map.entry("cat", "knjdbi"), Map.entry("chicken", "pjcwec"),
		Map.entry("cow", "hzahog"), Map.entry("drowned", "vakwgb"), Map.entry("horse", "ualabt"),
		Map.entry("husk", "hwltxk"), Map.entry("panda", "gggzar"), Map.entry("pig", "htibul"),
		Map.entry("sheep", "eccdga"), Map.entry("wolf", "hyzwpr"), Map.entry("zombie", "zvwapr"),
		Map.entry("zombified_piglin", "qltnkz"), Map.entry("zombie_villager", "nstwos")
	);
	private static long ticks;

	private ContextualDialogueController() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ContextualDialogueController::tick);
		ServerEntityEvents.ENTITY_LOAD.register(ContextualDialogueController::onEntityLoad);
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			UUID id = entity.getUUID();
			String encodedId = id.toString();
			BUSY_UNTIL.remove(id);
			ACTIVE_SOUNDS.remove(id);
			SPEECH_TARGETS.remove(id);
			LAST_SLEEPING.remove(id);
			LAST_TRADER_INVISIBLE.remove(id);
			VILLAGER_STATES.remove(id);
			CONDITION_HISTORY.remove(id);
			CONDITION_CURSORS.remove(id);
			PENDING_CONDITION_RELIEF.remove(id);
			VILLAGER_INVENTORIES.remove(id);
			NO_WORKSTATION_SINCE.remove(id);
			LAST_DANGER.remove(id);
			NO_BELL_SINCE.remove(id);
			PLAYER_OBSERVATIONS.remove(id);
			PLAYER_DEATHS.remove(id);
			ACTIVE_TRADES.remove(id);
			ACTIVE_PLAYER_ENCOUNTERS.remove(id);
			ACTIVE_TRADES.entrySet().removeIf(entry -> entry.getValue().traderId.equals(id));
			UNREACHABLE_STATES.remove(id);
			PENDING_SLEEP.remove(id);
			PENDING_WAKE.remove(id);
			WAKE_SOURCES.remove(id);
			SLEEP_BYPASS.remove(id);
			INTERRUPTED_SLEEP.remove(id);
			PENDING_SPEECH.removeIf(pending -> pending.speakerId.equals(id) || id.equals(pending.targetId));
			PAIR_TICKS.keySet().removeIf(pair -> pair.contains(encodedId));
		});

		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (level instanceof ServerLevel serverLevel) {
				if (queueFreedSuffocationRelief(serverLevel, pos)) return;
				PlayerObservation observation = PLAYER_OBSERVATIONS.computeIfAbsent(player.getUUID(), ignored -> new PlayerObservation());
				String title = selectBreakContext(state, observation);
				if (title.equals("Harvest Crops") && nearbyVillagers(serverLevel, Vec3.atCenterOf(pos), OBSERVER_RANGE).stream()
						.anyMatch(villager -> profession(villager).equals("farmer"))) title = "Harvest Crops Near a Farmer";
				playObserved(serverLevel, player, Vec3.atCenterOf(pos), title, SHORT_COOLDOWN);
			}
		});

		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (level instanceof ServerLevel serverLevel) {
				ItemStack held = player.getItemInHand(hand);
				BlockState clicked = level.getBlockState(hitResult.getBlockPos());
				String clickedPath = BuiltInRegistries.BLOCK.getKey(clicked.getBlock()).getPath();
				if (clickedPath.equals("bell")) {
					queueBell(serverLevel, hitResult.getLocation());
					return InteractionResult.PASS;
				}
				String title = selectHeldBlockContext(held, clicked);
				if (title == null && held.getItem() instanceof BlockItem blockItem) {
					title = selectPlaceContext(blockItem.getBlock(), serverLevel, hitResult.getBlockPos());
				} else if (title == null) {
					title = selectUseBlockContext(clicked);
				}
				if (clickedPath.endsWith("_door") && clicked.hasProperty(BlockStateProperties.OPEN)
						&& clicked.getValue(BlockStateProperties.OPEN)
						&& !nearbyVillagers(serverLevel, hitResult.getLocation(), 3.0).isEmpty()) title = "Close a Door in a Villager's Face";
				String heldPath = BuiltInRegistries.ITEM.getKey(held.getItem()).getPath();
				if ((heldPath.equals("pumpkin") || heldPath.equals("carved_pumpkin"))
						&& nearBlock(serverLevel, hitResult.getBlockPos(), "iron_block", 3)) title = "Build an Iron Golem Frame";
				boolean played = (clickedPath.equals("chest") || clickedPath.equals("trapped_chest"))
					&& playHomeChestReaction(serverLevel, player, hitResult.getBlockPos());
				if (!played) played = title != null && playObserved(serverLevel, player, hitResult.getLocation(), title, SHORT_COOLDOWN);

			}
			return InteractionResult.PASS;
		});

		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (level instanceof ServerLevel serverLevel) {
				String title = selectUseItemContext(player.getItemInHand(hand));
				if (title != null) playObserved(serverLevel, player, player.position(), title, SHORT_COOLDOWN);
			}
			return InteractionResult.PASS;
		});

		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (level instanceof ServerLevel) return onUseEntity(player, entity, hand);
			return InteractionResult.PASS;
		});

		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (entity.entityTags().contains(DIALOGUE_TEST_TAG)) return InteractionResult.SUCCESS;
			if (level instanceof ServerLevel && entity instanceof Villager villager
					&& cast(villager) == CastProfile.UNREACHABLE) {
				repelPlayer(villager, player);
				return InteractionResult.SUCCESS;
			}
			if (level instanceof ServerLevel) onAttackEntity(player, entity);
			return InteractionResult.PASS;
		});

		EntitySleepEvents.STOP_SLEEPING.register((entity, sleepingPos) -> {
			if (entity instanceof ServerPlayer player) {
				boolean armored = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)
					.stream().anyMatch(slot -> !player.getItemBySlot(slot).isEmpty());
				playObserved(player.level(), player, player.position(), armored ? "Wake Up in Armor" : "Player Wakes Up", LONG_COOLDOWN);
			} else if (entity instanceof Villager villager) {
				UUID id = villager.getUUID();
				boolean explicitlyInterrupted = INTERRUPTED_SLEEP.remove(id);
				PENDING_SLEEP.remove(id);
				SLEEP_BYPASS.remove(id);
				interrupt(villager);
				if (entity.level() instanceof ServerLevel level) {
					boolean interrupted = explicitlyInterrupted || isVillagerSleepTime(villager, level);
					String dialogue = interrupted ? "viwaal" : "ctptjt";
					UUID targetId = interrupted ? WAKE_SOURCES.remove(id) : null;
					PENDING_WAKE.put(id, new PendingWake(level, id, dialogue, targetId, ticks + 4L));
					LAST_SLEEPING.put(id, false);
				} else {
					PENDING_WAKE.remove(id);
					WAKE_SOURCES.remove(id);
				}
			}
		});

		ServerLivingEntityEvents.AFTER_DAMAGE.register(ContextualDialogueController::onDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(ContextualDialogueController::onDeath);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> clearState());
	}

	private static void onEntityLoad(Entity entity, ServerLevel level) {
		if (entity.entityTags().contains(DIALOGUE_TEST_TAG)) return;
		normalizeSpecialEntity(entity);
		String entityPath = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
		if (entityPath.equals("firework_rocket")) {
			playFireworkReactions(level, entity);
			return;
		}
		if (entityPath.equals("lightning_bolt")) {
			playLightningReaction(level, entity);
			return;
		}
		if (entity instanceof Sheep sheep) {
			if (isWooly(sheep) && sheep.getColor() == DyeColor.RED) sheep.setColor(DyeColor.WHITE);
			return;
		}
		if (!(entity instanceof Villager villager)) return;
		if (tryCreateNaturalSpecial(villager, level)) return;
		ensureSpecialTrade(villager);
		if (cast(villager) == CastProfile.UNREACHABLE) {
			UNREACHABLE_STATES.putIfAbsent(villager.getUUID(), new UnreachableState(ticks));
		}
		VILLAGER_STATES.put(villager.getUUID(), snapshot(villager, false));
		VILLAGER_INVENTORIES.put(villager.getUUID(), inventoryCounts(villager));
		EntitySpawnReason reason = villager.spawnReason();
		if (reason == EntitySpawnReason.SPAWN_ITEM_USE || reason == EntitySpawnReason.DISPENSER) {
			ServerPlayer player = nearestPlayer(level, villager.position(), 12.0);
			PENDING_SPEECH.add(new PendingSpeech(level, villager.getUUID(), villager.isBaby() ? "abfwiv" : "vskjkl",
				player == null ? null : player.getUUID(), ticks + 2L));
		} else if (reason == EntitySpawnReason.BREEDING) {
			Villager parent = nearbyVillagers(level, villager.position(), 12.0).stream()
				.filter(other -> other != villager && !other.isBaby())
				.min(Comparator.comparingDouble(other -> other.distanceToSqr(villager))).orElse(null);
			if (parent != null) {
				PENDING_SPEECH.add(new PendingSpeech(level, parent.getUUID(), "fbuabj", villager.getUUID(), ticks + 2L, true));
				PENDING_SPEECH.add(new PendingSpeech(level, villager.getUUID(), "lgjtnf", parent.getUUID(),
					ticks + DialogueCatalog.byId("fbuabj").durationTicks() + 4L));
			} else PENDING_SPEECH.add(new PendingSpeech(level, villager.getUUID(), "lgjtnf", null, ticks + 2L));
		} else if (reason == EntitySpawnReason.CONVERSION) {
			PENDING_SPEECH.add(new PendingSpeech(level, villager.getUUID(), villager.isBaby() ? "ggitzq" : "ivumgm", null, ticks + 2L));
		}
	}

	public static void onBabySpawnedFromEgg(Villager villager, Player player) {
		if (!(villager.level() instanceof ServerLevel level) || !villager.isBaby()
				|| villager.entityTags().contains(DIALOGUE_TEST_TAG)) return;
		UUID id = villager.getUUID();
		PENDING_SPEECH.removeIf(pending -> pending.speakerId.equals(id) || id.equals(pending.targetId));
		PENDING_SPEECH.add(new PendingSpeech(level, id, "abfwiv", player.getUUID(), ticks + 2L));
	}
	private static void processPendingSpeech() {
		PENDING_SPEECH.removeIf(pending -> {
			if (pending.dueTick > ticks) return false;
			Entity speaker = pending.level.getEntity(pending.speakerId);
			Entity target = pending.targetId == null ? null : pending.level.getEntity(pending.targetId);
			if (speaker instanceof LivingEntity living && living.isAlive()) {
				DialogueCatalog.DialogueGroup group = DialogueCatalog.byId(pending.dialogueId);
				boolean played = group != null && (pending.sharedAdult
					? playSharedId(living, pending.dialogueId, "queued:" + living.getUUID() + ":" + pending.dialogueId, 1L, target)
					: playId(living, pending.dialogueId, "queued:" + living.getUUID() + ":" + pending.dialogueId, 1L, target));
				if (played
						&& target instanceof LivingEntity listener) {
					holdListener(listener, living, group.durationTicks());
				}
			}
			return true;
		});
	}

	private static void maintainSpeechTargets(MinecraftServer server) {
		SPEECH_TARGETS.entrySet().removeIf(entry -> {
			SpeechTarget speech = entry.getValue();
			if (speech.untilTick <= ticks) return true;
			for (ServerLevel level : server.getAllLevels()) {
				Entity speaker = level.getEntity(entry.getKey());
				if (!(speaker instanceof Mob mob) || !mob.isAlive()) continue;
				Entity target = speech.targetId == null ? null : level.getEntity(speech.targetId);
				Vec3 position = target != null && target.isAlive() ? target.getEyePosition() : speech.position;
				if (speech.lockMovement) holdMob(mob, position);
				else faceMob(mob, position);
				return false;
			}
			return true;
		});
	}

	private static void processTradeSessions(MinecraftServer server) {
		ACTIVE_TRADES.entrySet().removeIf(entry -> {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			TradeSession session = entry.getValue();
			if (player != null && player.containerMenu instanceof MerchantMenu) {
				if (!session.opened) {
					session.opened = true;
					Entity trader = session.level.getEntity(session.traderId);
					if (trader instanceof Villager villager) {
						String id = tradeOpeningId(villager, player);
						playId(villager, id, "trade_open:" + villager.getUUID() + ":" + id, SHORT_COOLDOWN, player);
					} else if (trader instanceof WanderingTrader wanderingTrader) {
						playId(wanderingTrader, "yubpbb", "trade_open:" + wanderingTrader.getUUID(), SHORT_COOLDOWN, player);
					}
				}
				return false;
			}
			if (!session.opened && ticks - session.createdTick <= 20L) return false;
			if (!session.opened && player != null) {
				Entity trader = session.level.getEntity(session.traderId);
				if (trader instanceof Villager villager) {
					String id = unavailableTradeId(villager, player);
					if (id != null) playId(villager, id, "trade_unavailable:" + villager.getUUID() + ":" + id, SHORT_COOLDOWN, player);
				}
			}
			if (session.opened) {
				Entity trader = session.level.getEntity(session.traderId);
				if (trader instanceof Villager villager) {
					CastProfile profile = cast(villager);
					String id = switch (profile) {
						case MAYOR -> session.completed ? "shrrya" : "bgzmea";
						case TESTIFICATE_MAN -> session.completed ? "xcjort" : "rdugrl";
						case NUMBER_5 -> session.completed ? "msofrj" : "lilimm";
						case NUMBER_9 -> session.completed ? "czvvwy" : "lilimm";
						default -> session.completed ? "czvvwy" : ticks % 2L == 0L ? "lilimm" : "laztau";
					};
					boolean played = playId(villager, id, "trade_close:" + villager.getUUID(), 10L, player);
					if (!played) {
						String fallback = profile == CastProfile.TESTIFICATE_MAN ? "ctzfzj"
							: profile == CastProfile.NUMBER_5 ? "nfdery" : profile == CastProfile.NUMBER_9 ? "hvjfnk" : null;
						if (fallback != null) playId(villager, fallback, "trade_close_fallback:" + villager.getUUID(), 10L, player);
					}
				} else if (trader instanceof WanderingTrader wanderingTrader) {
					playId(wanderingTrader, session.completed ? "uzdvsi" : "erbcfn",
						"trade_close:" + wanderingTrader.getUUID(), 10L, player);
				}
			}
			return true;
		});
	}

	private static String tradeOpeningId(Villager villager, Player player) {
		CastProfile profile = cast(villager);
		if (profile != CastProfile.VILLAGER) return profile.trade;
		String unavailable = unavailableTradeId(villager, player);
		if (unavailable != null) return unavailable;
		int reputation = villager.getPlayerReputation(player);
		if (reputation < -225) return "xduuwm";
		if (reputation < -75) return "qmdvft";
		if (reputation >= 75) return "vlrsrn";
		if (reputation >= 25) return "kuhvdv";
		return profile.trade;
	}

	private static String unavailableTradeId(Villager villager, Player player) {
		if (cast(villager) != CastProfile.VILLAGER) return null;
		String profession = profession(villager);
		if (profession.equals("nitwit")) return "nukxsf";
		if (profession.equals("none")) return "nlbhku";
		if (villager.level() instanceof ServerLevel level && level.isRaided(villager.blockPosition())) return "klabhl";
		if (villager.getOffers().isEmpty()) return "zalmof";
		if (villager.getPlayerReputation(player) <= -150) return "lhdgsy";
		return null;
	}

	private static ServerPlayer nearestPlayer(ServerLevel level, Vec3 position, double range) {
		return level.players().stream()
			.filter(Player::isAlive)
			.filter(player -> player.distanceToSqr(position) <= range * range)
			.min(Comparator.comparingDouble(player -> player.distanceToSqr(position)))
			.orElse(null);
	}

	private static void processTimeChange(ServerLevel level) {
		String key = level.dimension().identifier().toString();
		long now = level.getOverworldClockTime();
		Long before = LAST_LEVEL_TIME.put(key, now);
		if (before == null || Math.abs(now - before) <= 40L || level.players().isEmpty()) return;
		ServerPlayer player = level.players().getFirst();
		Villager speaker = nearbyVillagers(level, player.position(), 32.0).stream()
			.filter(villager -> !villager.isSleeping())
			.min(Comparator.comparingDouble(villager -> villager.distanceToSqr(player))).orElse(null);
		if (speaker == null) return;
		boolean wasDay = Math.floorMod(before, 24000L) < 12000L;
		boolean isDay = Math.floorMod(now, 24000L) < 12000L;
		String id = wasDay == isDay ? (speaker.isBaby() ? "durjjd" : "uqwdqn")
			: isDay ? (speaker.isBaby() ? "wkwcrf" : "mgmzeh")
			: (speaker.isBaby() ? "msemoe" : "ohdwnz");
		playSharedId(speaker, id, "time_skip:" + key, SHORT_COOLDOWN, player);
	}

	private static void processDifficultyChange(ServerLevel level) {
		String key = level.dimension().identifier().toString();
		Difficulty difficulty = level.getDifficulty();
		Difficulty previous = LAST_DIFFICULTY.put(key, difficulty);
		if (previous == null || previous == difficulty || level.players().isEmpty()) return;
		ServerPlayer player = level.players().getFirst();
		Villager speaker = nearbyVillagers(level, player.position(), 32.0).stream()
			.filter(villager -> !villager.isBaby() && !villager.isSleeping())
			.min(Comparator.comparingDouble(villager -> villager.distanceToSqr(player))).orElse(null);
		if (speaker == null) return;
		String id = difficulty == Difficulty.HARD ? "arzojk" : difficulty == Difficulty.PEACEFUL ? "xuyypm" : "ibcrvx";
		playSharedId(speaker, id, "difficulty:" + key + ":" + difficulty.name(), SHORT_COOLDOWN, player);
	}

	private static void tick(MinecraftServer server) {
		if (!server.tickRateManager().runsNormally()) {
			stopActiveDialogue(server);
			return;
		}
		ticks++;
		processUnreachableVillagers(server);
		if (!VillagerNewsSettings.dialogueEnabled()) {
			stopActiveDialogue(server);
			ACTIVE_PLAYER_ENCOUNTERS.clear();
			return;
		}
		ACTIVE_SOUNDS.entrySet().removeIf(entry -> entry.getValue().endTick <= ticks);
		maintainSpeechTargets(server);
		processPendingSleep();
		processPendingConditionRelief();
		processPendingWake();
		processPendingSpeech();
		processPendingBells();
		processTradeSessions(server);
		if (ticks % 10L != 0L) return;

		for (ServerLevel level : server.getAllLevels()) {
			processTimeChange(level);
			processDifficultyChange(level);
			for (ServerPlayer player : level.players()) {
				if (player.gameMode().getName().equals("spectator")) {
					ACTIVE_PLAYER_ENCOUNTERS.remove(player.getUUID());
					PlayerObservation observation = PLAYER_OBSERVATIONS.computeIfAbsent(player.getUUID(), ignored -> new PlayerObservation());
					observation.lastPosition = player.position();
					observation.lastGameMode = "spectator";
					observation.lastPlayerContext = null;
					observation.stillTicks = 0;
					observation.stareTicks = 0;
					continue;
				}
				processPlayer(level, player);
				processWanderingTrader(level, player);
				processWooly(level, player);
			}
			if (ticks % 100L == 0L) processConversations(level);
		}

		if (ticks % 1200L == 0L) {
			COOLDOWNS.entrySet().removeIf(entry -> entry.getValue() + 20L * 600L < ticks);
			BUSY_UNTIL.entrySet().removeIf(entry -> entry.getValue() < ticks);
		}
	}

	private static void clearState() {
		COOLDOWNS.clear();
		BUSY_UNTIL.clear();
		ACTIVE_SOUNDS.clear();
		PLAYER_OBSERVATIONS.clear();
		PLAYER_DEATHS.clear();
		LAST_SLEEPING.clear();
		LAST_TRADER_INVISIBLE.clear();
		VILLAGER_STATES.clear();
		CONDITION_HISTORY.clear();
		CONDITION_CURSORS.clear();
		PENDING_CONDITION_RELIEF.clear();
		VILLAGER_INVENTORIES.clear();
		SPEECH_TARGETS.clear();
		SHARED_RECENT_VARIANTS.clear();
		NO_WORKSTATION_SINCE.clear();
		LAST_DANGER.clear();
		NO_BELL_SINCE.clear();
		ACTIVE_TRADES.clear();
		UNREACHABLE_STATES.clear();
		ACTIVE_PLAYER_ENCOUNTERS.clear();
		PENDING_SLEEP.clear();
		PENDING_WAKE.clear();
		PENDING_BELLS.clear();
		PENDING_BELL_REACTIONS.clear();
		WAKE_SOURCES.clear();
		SLEEP_BYPASS.clear();
		INTERRUPTED_SLEEP.clear();
		PENDING_SPEECH.clear();
		LAST_LEVEL_TIME.clear();
		LAST_DIFFICULTY.clear();
		PAIR_TICKS.clear();
		ticks = 0L;
	}

	private static void processPendingWake() {
		PENDING_WAKE.entrySet().removeIf(entry -> {
			PendingWake pending = entry.getValue();
			if (pending.dueTick > ticks) return false;
			Entity entity = pending.level.getEntity(pending.villagerId);
			if (!(entity instanceof Villager villager) || !villager.isAlive() || villager.isSleeping()) return true;
			if (isBusy(villager)) return false;
			Entity target = pending.targetId == null ? null : pending.level.getEntity(pending.targetId);
			return playSharedId(villager, pending.dialogueId,
				"wake:" + pending.villagerId + ":" + pending.dialogueId, 1L, target);
		});
	}

	private static void queueBell(ServerLevel level, Vec3 position) {
		boolean queued = PENDING_BELLS.stream().anyMatch(pending -> pending.level == level
			&& pending.position.distanceToSqr(position) < 0.25 && pending.dueTick > ticks);
		if (!queued) PENDING_BELLS.add(new PendingBell(level, position, ticks + 15L));
	}

	private static void processPendingBells() {
		PENDING_BELLS.removeIf(pending -> {
			if (pending.dueTick > ticks) return false;
			for (Villager villager : nearbyVillagers(pending.level, pending.position, 50.0)) {
				if (villager.isSleeping() || cast(villager) != CastProfile.VILLAGER) continue;
				long dueTick = ticks + ThreadLocalRandom.current().nextInt(5);
				PENDING_BELL_REACTIONS.add(new PendingBellReaction(pending.level, villager.getUUID(), pending.position,
					dueTick, dueTick + 10L));
			}
			return true;
		});
		PENDING_BELL_REACTIONS.removeIf(pending -> {
			if (pending.dueTick > ticks) return false;
			Entity entity = pending.level.getEntity(pending.villagerId);
			if (!(entity instanceof Villager villager) || !villager.isAlive() || villager.isSleeping()
					|| cast(villager) != CastProfile.VILLAGER) return true;
			if (isBusy(villager)) return ticks >= pending.expireTick;
			String id = villager.isBaby() ? "nxalcz" : "kljgyu";
			return playId(villager, id, "bell:" + pending.dueTick + ":" + villager.getUUID(), 1L, pending.position)
				|| ticks >= pending.expireTick;
		});
	}
	private static boolean isVillagerSleepTime(Villager villager, ServerLevel level) {
		long time = Math.floorMod(level.getOverworldClockTime(), 24000L);
		return profession(villager).equals("nitwit") ? time >= 14000L || time < 2000L : time >= 12000L;
	}

	private static void processPendingSleep() {
		PENDING_SLEEP.entrySet().removeIf(entry -> {
			PendingSleep pending = entry.getValue();
			ActiveSound active = ACTIVE_SOUNDS.get(entry.getKey());
			if (active != null && active.endTick >= pending.dueTick) pending.dueTick = active.endTick + 1L;
			if (pending.dueTick > ticks) return false;
			Entity entity = pending.level.getEntity(entry.getKey());
			if (entity instanceof Villager villager && villager.isAlive() && !villager.isSleeping()
					&& villager.distanceToSqr(Vec3.atCenterOf(pending.bedPos)) <= 16.0
					&& BuiltInRegistries.BLOCK.getKey(pending.level.getBlockState(pending.bedPos).getBlock()).getPath().endsWith("_bed")) {
				if (!pending.bedtimeStarted) {
					DialogueCatalog.DialogueGroup group = DialogueCatalog.byId("ioxtmt");
					if (group != null && play(villager, group, "bed:" + villager.getUUID(), LONG_COOLDOWN,
							null, Vec3.atCenterOf(pending.bedPos))) {
						ActiveSound bedtime = ACTIVE_SOUNDS.get(villager.getUUID());
						pending.bedtimeStarted = true;
						pending.dueTick = bedtime == null ? ticks + group.durationTicks() : bedtime.endTick + 1L;
						return false;
					}
				}
				SLEEP_BYPASS.add(villager.getUUID());
				try {
					villager.startSleeping(pending.bedPos);
				} finally {
					SLEEP_BYPASS.remove(villager.getUUID());
				}
			}
			return true;
		});
	}

	public static boolean delayVillagerSleep(Villager villager, BlockPos bedPos) {
		UUID id = villager.getUUID();
		if (SLEEP_BYPASS.remove(id)) return false;
		if (!(villager.level() instanceof ServerLevel level) || villager.isSleeping()) return false;
		if (PENDING_SLEEP.containsKey(id)) return true;
		ActiveSound active = ACTIVE_SOUNDS.get(id);
		if (active != null && active.endTick > ticks) {
			PENDING_SLEEP.put(id, new PendingSleep(level, bedPos.immutable(), active.endTick + 1L, false));
			LAST_SLEEPING.put(id, false);
			return true;
		}
		DialogueCatalog.DialogueGroup group = DialogueCatalog.byId("ioxtmt");
		if (group == null || !play(villager, group, "bed:" + id, LONG_COOLDOWN, null, Vec3.atCenterOf(bedPos))) return false;
		ActiveSound bedtime = ACTIVE_SOUNDS.get(id);
		long dueTick = bedtime == null ? ticks + group.durationTicks() : bedtime.endTick + 1L;
		PENDING_SLEEP.put(id, new PendingSleep(level, bedPos.immutable(), dueTick, true));
		LAST_SLEEPING.put(id, false);
		return true;
	}

	private static void stopActiveDialogue(MinecraftServer server) {
		for (UUID id : List.copyOf(ACTIVE_SOUNDS.keySet())) {
			LivingEntity speaker = null;
			for (ServerLevel level : server.getAllLevels()) {
				Entity entity = level.getEntity(id);
				if (entity instanceof LivingEntity living) {
					speaker = living;
					break;
				}
			}
			if (speaker != null) interrupt(speaker);
			else {
				ACTIVE_SOUNDS.remove(id);
				BUSY_UNTIL.remove(id);
				SPEECH_TARGETS.remove(id);
			}
		}
	}

	private static void processWanderingTrader(ServerLevel level, ServerPlayer player) {
		AABB area = AABB.ofSize(player.position(), OBSERVER_RANGE * 2.0, OBSERVER_RANGE, OBSERVER_RANGE * 2.0);
		WanderingTrader trader = level.getEntitiesOfClass(WanderingTrader.class, area, Entity::isAlive).stream()
			.filter(candidate -> candidate.hasLineOfSight(player))
			.min(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(player)))
			.orElse(null);
		if (trader == null) return;
		String pair = player.getUUID() + ":" + trader.getUUID();
		if (playId(trader, "hxlyuc", "approach:" + pair, LONG_COOLDOWN, player)) return;
		boolean invisible = trader.hasEffect(MobEffects.INVISIBILITY);
		boolean wasInvisible = LAST_TRADER_INVISIBLE.put(trader.getUUID(), invisible) == Boolean.TRUE;
		if (invisible) {
			long llamas = level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(trader.position(), 24, 12, 24), Entity::isAlive)
				.stream().filter(entity -> BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath().equals("trader_llama")).count();
			String id = wasInvisible ? "dbzjqi" : llamas >= 2 ? "myajyt" : llamas == 1 ? "jkeahu" : "vggdrt";
			if (playId(trader, id, "invisible:" + trader.getUUID() + ":" + id, LONG_COOLDOWN, player)) return;
		}
		if (level.isRainingAt(trader.blockPosition())
				&& playId(trader, "kxoqky", "rain:" + trader.getUUID(), LONG_COOLDOWN)) return;
		if (ticks % 100L == 0L && trader.getDeltaMovement().horizontalDistanceSqr() > 0.0004) {
			playId(trader, "stqafd", "idle:" + trader.getUUID(), LONG_COOLDOWN);
		}
	}

	private static void processWooly(ServerLevel level, ServerPlayer player) {
		AABB area = AABB.ofSize(player.position(), OBSERVER_RANGE * 2.0, OBSERVER_RANGE, OBSERVER_RANGE * 2.0);
		Sheep wooly = level.getEntitiesOfClass(Sheep.class, area, sheep -> sheep.isAlive() && isWooly(sheep)
			&& !sheep.entityTags().contains(DIALOGUE_TEST_TAG)).stream()
			.filter(candidate -> candidate.hasLineOfSight(player))
			.min(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(player)))
			.orElse(null);
		if (wooly == null) return;
		String pair = player.getUUID() + ":" + wooly.getUUID();
		if (playId(wooly, "uvtocs", "approach:" + pair, LONG_COOLDOWN, player)) return;
		if (ticks % 100L == 0L && wooly.getDeltaMovement().horizontalDistanceSqr() > 0.0004) {
			playId(wooly, "vmohcm", "idle:" + wooly.getUUID(), LONG_COOLDOWN);
		}
	}

	private static void processUnreachableVillagers(MinecraftServer server) {
		Set<UUID> processed = new HashSet<>();
		for (ServerLevel level : server.getAllLevels()) {
			for (ServerPlayer player : level.players()) {
				AABB area = player.getBoundingBox().inflate(16.0);
				for (Villager villager : level.getEntitiesOfClass(Villager.class, area,
						candidate -> candidate.isAlive() && cast(candidate) == CastProfile.UNREACHABLE)) {
					if (processed.contains(villager.getUUID())) continue;
					ServerPlayer nearest = nearestPlayer(level, villager.position(), 16.0);
					if (nearest != null) {
						processed.add(villager.getUUID());
						updateUnreachable(villager, nearest);
					}
				}
			}
		}
		UNREACHABLE_STATES.entrySet().removeIf(entry -> {
			if (processed.contains(entry.getKey())) return false;
			for (ServerLevel level : server.getAllLevels()) {
				Entity entity = level.getEntity(entry.getKey());
				if (entity instanceof Villager villager && villager.isAlive()
						&& cast(villager) == CastProfile.UNREACHABLE) {
					villager.getNavigation().stop();
					entry.getValue().stopFleeing(ticks);
					return false;
				}
			}
			return true;
		});
	}

	private static void updateUnreachable(Villager villager, ServerPlayer player) {
		UnreachableState state = UNREACHABLE_STATES.computeIfAbsent(villager.getUUID(), ignored -> new UnreachableState(ticks));
		state.updateFleeing(ticks);
		double distance = villager.distanceTo(player);
		if (distance <= 4.0) teleportUnreachable(villager, player);
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		if (ticks >= state.nextPathTick || villager.getNavigation().isDone()) {
			Vec3 destination = DefaultRandomPos.getPosAway(villager, 16, 7, player.position());
			if (destination == null || destination.distanceToSqr(player.position()) <= villager.distanceToSqr(player)) {
				Vec3 away = villager.position().subtract(player.position());
				if (away.horizontalDistanceSqr() < 0.0001) {
					double angle = villager.getRandom().nextDouble() * Math.PI * 2.0;
					away = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
				} else away = new Vec3(away.x, 0.0, away.z).normalize();
				destination = villager.position().add(away.scale(16.0));
			}
			villager.getNavigation().moveTo(destination.x, destination.y, destination.z, 0.9);
			state.nextPathTick = ticks + 10L;
		}
		if (distance <= 12.0) {
			faceMob(villager, player.getEyePosition());
		}
		if (state.canTaunt(ticks)
				&& playId(villager, "eltxge", "unreachable_taunt:" + villager.getUUID(), 1L, player)) {
			state.taunted(ticks);
		}
	}

	private static void repelPlayer(Villager villager, Player player) {
		if (villager.distanceToSqr(player) <= 16.0) teleportUnreachable(villager, player);
		if (player instanceof ServerPlayer serverPlayer && villager.distanceTo(player) <= 16.0) {
			updateUnreachable(villager, serverPlayer);
		}
	}

	private static boolean teleportUnreachable(Villager villager, Player player) {
		Vec3 away = villager.position().subtract(player.position());
		double baseAngle = away.horizontalDistanceSqr() < 0.0001
			? villager.getRandom().nextDouble() * Math.PI * 2.0
			: Math.atan2(away.z, away.x);
		for (int attempt = 0; attempt < 24; attempt++) {
			double angle = baseAngle + (villager.getRandom().nextDouble() - 0.5) * Math.PI * 0.75;
			double distance = 14.0 + villager.getRandom().nextDouble() * 4.0;
			double x = villager.getX() + Math.cos(angle) * distance;
			double y = villager.getY() + villager.getRandom().nextInt(11) - 5;
			double z = villager.getZ() + Math.sin(angle) * distance;
			if (villager.randomTeleport(x, y, z, true)) {
				villager.getNavigation().stop();
				return true;
			}
		}
		return false;
	}

	private static void processPlayer(ServerLevel level, ServerPlayer player) {
		PlayerObservation observation = PLAYER_OBSERVATIONS.computeIfAbsent(player.getUUID(), ignored -> new PlayerObservation());
		String gameMode = player.gameMode().getName();
		boolean changedGameMode = observation.lastGameMode != null && !observation.lastGameMode.equals(gameMode);
		observation.lastGameMode = gameMode;
		Vec3 movement = player.position().subtract(observation.lastPosition);
		if (movement.horizontalDistanceSqr() < 0.0004 && Math.abs(movement.y) < 0.01) observation.stillTicks += 10;
		else observation.stillTicks = 0;
		observation.lastPosition = player.position();
		BlockPos ground = player.blockPosition().below();
		String groundBlock = BuiltInRegistries.BLOCK.getKey(level.getBlockState(ground).getBlock()).getPath();
		if (ground.equals(observation.lastGroundPos) && observation.lastGroundBlock.equals("farmland") && groundBlock.equals("dirt")) {
			playObserved(level, player, player.position(), "Trample Crops", SHORT_COOLDOWN);
		}
		observation.lastGroundPos = ground;
		observation.lastGroundBlock = groundBlock;

		List<Villager> nearby = nearbyVillagers(level, player.position(), OBSERVER_RANGE).stream()
			.filter(villager -> !villager.isSleeping()).toList();
		for (Villager villager : nearby) processConditionDialogues(villager);
		Villager adult = nearby.stream()
			.filter(villager -> !villager.isBaby())
			.filter(villager -> cast(villager) != CastProfile.UNREACHABLE)
			.filter(villager -> villager.hasLineOfSight(player))
			.min(Comparator.comparingDouble(villager -> villager.distanceToSqr(player)))
			.orElse(null);
		if (adult == null) {
			Villager baby = nearby.stream().filter(Villager::isBaby)
				.filter(villager -> cast(villager) != CastProfile.UNREACHABLE).filter(villager -> villager.hasLineOfSight(player))
				.min(Comparator.comparingDouble(villager -> villager.distanceToSqr(player))).orElse(null);
			if (baby != null) {
				boolean firstNotice = ACTIVE_PLAYER_ENCOUNTERS.add(player.getUUID());
				if (ticks % 40L == 0L && playNearbyEntityContext(level, baby)) return;
				String id = player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE) ? "fzyrfm"
					: isNegativeReputation(baby, player) ? "jfuftm" : "wtuguc";
				if (firstNotice) playId(baby, id, "player_greeting:" + player.getUUID(), LONG_COOLDOWN, player);
			} else ACTIVE_PLAYER_ENCOUNTERS.remove(player.getUUID());
			return;
		}

		boolean firstNotice = ACTIVE_PLAYER_ENCOUNTERS.add(player.getUUID());
		String pair = player.getUUID() + ":" + adult.getUUID();
		if (playCosmeticObservation(player, adult)) return;
		if (player.getBoundingBox().inflate(0.15).intersects(adult.getBoundingBox()) && movement.horizontalDistanceSqr() > 0.002) {
			String id = adult.getVehicle() != null && BuiltInRegistries.ENTITY_TYPE.getKey(adult.getVehicle().getType()).getPath().contains("boat")
				? "zvbnea" : "ajexrq";
			if (playId(adult, id, "nudge:" + adult.getUUID(), SHORT_COOLDOWN, player)) return;
		}
		if (changedGameMode && playSharedId(adult, gameMode.equals("creative") ? "ohtblt" : "fhhqxg",
				"gamemode:" + player.getUUID() + ":" + gameMode, SHORT_COOLDOWN, player)) return;
		CastProfile adultProfile = cast(adult);
		if (firstNotice && adultProfile != CastProfile.VILLAGER && player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)
				&& playSharedId(adult, "gnetsk", "player_greeting:" + player.getUUID(), LONG_COOLDOWN, player)) return;
		String approach = adultProfile == CastProfile.VILLAGER ? reputationApproach(adult, player) : adultProfile.approach;
		if (firstNotice && playId(adult, approach, "player_greeting:" + player.getUUID(), LONG_COOLDOWN, player)) {
			return;
		}

		Vec3 toVillager = adult.getEyePosition().subtract(player.getEyePosition()).normalize();
		double lookDot = player.getLookAngle().dot(toVillager);
		if (lookDot > 0.985) observation.stareTicks += 10;
		else observation.stareTicks = 0;

		if (observation.stareTicks >= 60 && playSharedTitle(adult, "Stare at a Villager", "stare:" + pair, LONG_COOLDOWN, player)) {
			observation.stareTicks = 0;
			return;
		}
		if (ticks % 400L == 0L && observation.stillTicks >= 2400
				&& playSharedTitle(adult, "Stand Completely Still", "still:" + player.getUUID(), LONG_COOLDOWN, player)) {
			observation.stillTicks = 0;
			return;
		}

		String playerContext = playerContext(player, adult);
		boolean changedPlayerContext = playerContext != null && !playerContext.equals(observation.lastPlayerContext);
		observation.lastPlayerContext = playerContext;
		if (changedPlayerContext && playSharedTitle(adult, playerContext,
				"player_context:" + player.getUUID() + ":" + playerContext, LONG_COOLDOWN, player)) return;
		long nearbyPlayers = level.players().stream().filter(other -> other.distanceToSqr(adult) <= 64.0).count();
		if (nearbyPlayers >= 2 && playSharedId(adult, "cstyvg", "player_crowd:" + adult.getUUID(), LONG_COOLDOWN, player)) return;

		String environment = environmentContext(level, adult);
		if (environment != null && playTitle(adult, environment, "environment:" + adult.getUUID() + ":" + environment, LONG_COOLDOWN)) return;

		if (ticks % 40L == 0L && playNearbyEntityContext(level, adult)) return;

		if (ticks % 200L == 0L) {
			String time = timeContext(level, adult);
			if (playTitle(adult, time, "time:" + adult.getUUID() + ":" + time, LONG_COOLDOWN)) return;
		}

		if (ticks % 100L == 0L && adult.getDeltaMovement().horizontalDistanceSqr() > 0.0004) {
			CastProfile profile = cast(adult);
			if (profile != CastProfile.VILLAGER) playId(adult, profile.idle, "idle:" + adult.getUUID(), LONG_COOLDOWN);
			else {
				String ambient = ambientDialogue(adult);
				playId(adult, ambient, "idle:" + adult.getUUID() + ":" + ambient, LONG_COOLDOWN);
			}
		}
	}

	private static String environmentContext(ServerLevel level, Villager villager) {
		Entity vehicle = villager.getVehicle();
		if (vehicle != null) {
			String vehiclePath = BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType()).getPath();
			if (vehiclePath.contains("minecart")) {
				return vehicle.getDeltaMovement().horizontalDistanceSqr() > 0.001 ? "Ride in a Moving Minecart" : "Sit in a Minecart";
			}
			if (vehiclePath.contains("boat")) {
				if (ticks / LONG_COOLDOWN % 3L == 0L) return "Sit in a Boat";
				return vehicle.isInWater() ? "Boat on Water" : "Boat on Land";
			}
		}
		if (level.dimension() == Level.NETHER) return "Wander in the Nether";
		if (level.dimension() == Level.END) return "Wander in the End";
		if (level.dimension() != Level.OVERWORLD) return "Wander in Another Dimension";
		if (level.isRainingAt(villager.blockPosition())) return "Caught in the Rain";
		if (villager.isInWater()) return "Stand in Shallow Water";
		String biome = level.getBiome(villager.blockPosition()).unwrapKey().map(key -> key.identifier().getPath()).orElse("");
		if (biome.contains("desert") || biome.contains("badlands") || biome.contains("savanna")) return "Wander Somewhere Hot";
		if (biome.contains("snow") || biome.contains("frozen") || biome.contains("ice") || biome.contains("cold")) return "Wander Somewhere Cold";
		BlockState below = level.getBlockState(villager.blockPosition().below());
		if (below.is(Blocks.ICE) || below.is(Blocks.PACKED_ICE) || below.is(Blocks.BLUE_ICE)) return "Stand on Ice";
		if (below.is(Blocks.SNOW_BLOCK) || below.is(Blocks.POWDER_SNOW)) return "Stand on Snow";
		if (below.is(Blocks.MAGMA_BLOCK)) return "Stand on Magma";
		for (int x = -3; x <= 3; x++) for (int y = -2; y <= 2; y++) for (int z = -3; z <= 3; z++) {
			String block = BuiltInRegistries.BLOCK.getKey(level.getBlockState(villager.blockPosition().offset(x, y, z)).getBlock()).getPath();
			if (block.contains("campfire")) return "See a Campfire";
			if (block.equals("fire") || block.equals("soul_fire")) return "Stand Near Fire";
			if (block.equals("bookshelf") && villager.getDeltaMovement().horizontalDistanceSqr() < 0.0004) return "Inspect Bookshelves";
		}
		if (villager.getY() < level.getSeaLevel() - 30) return "Wander Deep Underground";
		if (villager.getY() > level.getSeaLevel() + 75) return "Wander High Above the Ground";
		return null;
	}

	private static String timeContext(ServerLevel level, Villager villager) {
		float sunAngle = level.environmentAttributes().getValue(EnvironmentAttributes.SUN_ANGLE, villager.blockPosition());
		if (sunAngle < 0.125F || sunAngle >= 0.875F) return "Morning";
		if (sunAngle < 0.45F) return "Afternoon";
		if (sunAngle < 0.625F) return "Evening";
		return "Night";
	}

	private static String ambientDialogue(Villager villager) {
		String profession = profession(villager);
		if (profession.equals("nitwit")) return "uookqp";
		if (profession.equals("none")) return "gbxzxv";
		LocalDate date = LocalDate.now();
		List<String> choices = new ArrayList<>();
		if (date.getMonthValue() == 10) choices.add("mltyge");
		if (date.getMonthValue() == 12) choices.add("tkkegl");
		if (date.getMonthValue() == 1 && date.getDayOfMonth() == 1) choices.add("uyqiwv");
		if (date.getMonthValue() == 2 && date.getDayOfMonth() == 14) choices.add("fabiyx");
		if (date.getMonthValue() == 4 && date.getDayOfMonth() == 1) choices.add("obitls");
		if (date.getMonthValue() == 5 && date.getDayOfMonth() == 17) choices.add("iriuqa");
		if (date.getMonthValue() == 10 && date.getDayOfMonth() == 31) choices.add("adhxce");
		if (date.getMonthValue() == 12 && date.getDayOfMonth() == 24) choices.add("zoqxvy");
		if (date.getMonthValue() == 12 && date.getDayOfMonth() == 25) choices.add("rclyrl");
		if (date.getMonthValue() == 12 && date.getDayOfMonth() == 31) choices.add("xljknt");
		if (date.getDayOfMonth() == 13 && date.getDayOfWeek() == DayOfWeek.FRIDAY) choices.add("qfcwvz");
		if (LocalDateTime.now().getMinute() == 0) choices.add("jqgkhy");
		if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
			choices.add("bkyidl");
			choices.add("ckngck");
		}
		choices.add(switch (date.getDayOfWeek()) {
			case MONDAY -> ticks / LONG_COOLDOWN % 2L == 0L ? "gkvlqc" : "jpucos";
			case TUESDAY -> ticks / LONG_COOLDOWN % 2L == 0L ? "dkpihl" : "lgeeem";
			case WEDNESDAY -> ticks / LONG_COOLDOWN % 2L == 0L ? "gwakiz" : "qiqiez";
			case THURSDAY -> ticks / LONG_COOLDOWN % 2L == 0L ? "zglkgp" : "caiyte";
			case FRIDAY -> ticks / LONG_COOLDOWN % 2L == 0L ? "ypyumu" : "cxtvsx";
			case SATURDAY -> ticks / LONG_COOLDOWN % 2L == 0L ? "ildosa" : "lfhnxz";
			case SUNDAY -> ticks / LONG_COOLDOWN % 2L == 0L ? "uzvatl" : "zckxrc";
		});
		if (choices.size() == 1) choices.add("lvigit");
		return choices.get(Math.floorMod((int) (ticks / LONG_COOLDOWN), choices.size()));
	}

	private static String playerContext(ServerPlayer player, Villager villager) {
		if (player.isFallFlying()) return "Glide with Elytra";
		if (player.gameMode().getName().equals("creative") && player.getAbilities().flying) return "Fly in Creative Mode";
		if (player.isShiftKeyDown() && player.getDeltaMovement().horizontalDistanceSqr() > 0.0001) return "Crouch-Walk";
		if (player.getHealth() <= player.getMaxHealth() * 0.3F) return "Low Health";
		if (player.hasEffect(MobEffects.INVISIBILITY)) return "Invisibility";
		if (player.hasEffect(MobEffects.DARKNESS)) return "Darkness";
		if (player.hasEffect(MobEffects.NIGHT_VISION)) return "Night Vision";
		if (player.hasEffect(MobEffects.WATER_BREATHING)) return "Water Breathing";
		if (player.hasEffect(MobEffects.SPEED)) return "Swiftness";
		if (player.hasEffect(MobEffects.SLOWNESS)) return "Slowness";
		if (player.hasEffect(MobEffects.STRENGTH)) return "Strength";
		if (player.hasEffect(MobEffects.WEAKNESS)) return "Weakness";
		if (player.hasEffect(MobEffects.HUNGER)) return "Hunger";
		if (player.hasEffect(MobEffects.NAUSEA)) return "Nausea";
		if (player.hasEffect(MobEffects.BAD_OMEN) || player.hasEffect(MobEffects.RAID_OMEN)) return "Bad Omen";
		if (player.hasEffect(MobEffects.OOZING)) return "Oozing";
		if (player.getActiveEffects().size() >= 2) return "Multiple Status Effects";
		if (BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(player.blockPosition().below()).getBlock())
				.getPath().endsWith("_bed")) return "Stand on a Villager's Bed";

		ItemStack held = player.getMainHandItem();
		if (held.isDamageableItem() && held.getDamageValue() >= held.getMaxDamage() * 0.85F) return "Hold a Nearly Broken Item";
		int armor = 0;
		Set<String> armorMaterials = new HashSet<>();
		for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
			ItemStack stack = player.getItemBySlot(slot);
			if (stack.isEmpty()) continue;
			armor++;
			String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
			armorMaterials.add(path.substring(0, path.indexOf('_') > 0 ? path.indexOf('_') : path.length()));
		}
		if (armor == 4 && armorMaterials.size() == 1 && armorMaterials.contains("iron")) return "Wear Full Iron Armor";
		if (armor == 4 && armorMaterials.size() > 1) return "Wear Mixed Armor";
		if (armor == 4 && (armorMaterials.contains("diamond") || armorMaterials.contains("netherite"))) return "Wear High-Level Armor";
		if (armor > 0) return "Wear Armor";
		return null;
	}

	private static String reputationApproach(Villager villager, ServerPlayer player) {
		if (player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)) return "gnetsk";
		int reputation = villager.getPlayerReputation(player);
		if (reputation < -225) {
			return BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).getPath().endsWith("_sword")
				? "stuirs" : "zstdjn";
		}
		if (reputation < -75) return "tfzlsw";
		if (reputation >= 75) return "kcbenk";
		if (reputation >= 25) return "omgcte";
		return "xfpjxq";
	}

	private static boolean isNegativeReputation(Villager villager, ServerPlayer player) {
		return villager.getPlayerReputation(player) < -75;
	}

	private static boolean playCosmeticObservation(ServerPlayer player, Villager villager) {
		ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
		ItemStack held = player.getMainHandItem();
		CastProfile profile = cast(villager);
		String id = null;
		if (head.getItem() == VillagerNewsItems.VILLAGER_NOSE) id = "kejscw";
		else if (head.getItem() == VillagerNewsItems.MAYOR_HAT && profile == CastProfile.MAYOR) id = "cmkesu";
		else if (head.getItem() == VillagerNewsItems.TESTIFICATE_MAN_HELMET && profile == CastProfile.TESTIFICATE_MAN) id = "rooiup";
		else if (head.getItem() == VillagerNewsItems.MOUSTACHE && profile == CastProfile.NUMBER_5) id = "mjyhgw";
		else if (held.getItem() == VillagerNewsItems.MICROPHONE && profile == CastProfile.NUMBER_9) id = "adhvqz";
		if (id == null) return false;
		String key = "player_cosmetic:" + villager.getUUID() + ":" + player.getUUID() + ":" + id;
		return id.equals("kejscw") ? playSharedId(villager, id, key, LONG_COOLDOWN, player)
			: playId(villager, id, key, LONG_COOLDOWN, player);
	}

	private static void processConversations(ServerLevel level) {
		Set<UUID> updatedVillagers = new HashSet<>();
		for (ServerPlayer player : level.players()) {
			for (Villager villager : nearbyVillagers(level, player.position(), 24)) {
				if (cast(villager) != CastProfile.UNREACHABLE && updatedVillagers.add(villager.getUUID())) processVillagerState(villager);
			}
		}
		Set<String> checkedPairs = new HashSet<>();
		for (ServerPlayer player : level.players()) {
			List<Villager> villagers = nearbyVillagers(level, player.position(), 24).stream()
				.filter(villager -> !villager.isBaby() && !villager.isSleeping())
				.filter(villager -> cast(villager) != CastProfile.UNREACHABLE)
				.filter(villager -> activeConditionDialogues(villager).isEmpty()).toList();
			for (Villager subject : villagers) {
				if (data(subject).vnap$cosmetic() == 0) continue;
				Villager witness = villagers.stream().filter(other -> other != subject && other.hasLineOfSight(subject))
					.min(Comparator.comparingDouble(other -> other.distanceToSqr(subject))).orElse(null);
				if (witness != null) {
					String id = cast(witness) == CastProfile.TESTIFICATE_MAN && data(subject).vnap$cosmetic() == 2 ? "pbbywc" : "anrhns";
					if (playSharedId(witness, id, "cosmetic_witness:" + witness.getUUID() + ":" + subject.getUUID() + ":" + id,
						LONG_COOLDOWN, subject)) return;
				}
			}
			if (villagers.size() >= 8 && playSharedId(villagers.getFirst(), "kzemrz", "villager_crowd:" + player.getUUID(), LONG_COOLDOWN, player)) return;
			Villager gatheringSpeaker = villagers.stream().filter(villager -> cast(villager) == CastProfile.VILLAGER).findFirst().orElse(null);
			Villager gatheringTarget = gatheringSpeaker == null ? null : nearestConversationPartner(gatheringSpeaker, villagers);
			if (gatheringTarget != null && villagers.size() >= 3 && playId(gatheringSpeaker, "ebfifz",
					"gathering:" + player.getUUID(), LONG_COOLDOWN, gatheringTarget)) return;
			for (Villager candidate : villagers) {
				Villager partner = nearestConversationPartner(candidate, villagers);
				if (partner == null) continue;
				String pair = orderedPair(candidate.getUUID(), partner.getUUID());
				if (!checkedPairs.add(pair)) continue;
				Villager first = candidate.getUUID().compareTo(partner.getUUID()) <= 0 ? candidate : partner;
				Villager second = first == candidate ? partner : candidate;
				if (isBusy(first) || isBusy(second)) continue;
				int togetherTicks = PAIR_TICKS.merge(pair, 100, Integer::sum);
				if (togetherTicks < 200) continue;
				boolean firstHasNose = data(first).vnap$hasNose();
				boolean secondHasNose = data(second).vnap$hasNose();
				if (!firstHasNose || !secondHasNose) {
					boolean bothMissing = !firstHasNose && !secondHasNose;
					Villager conversationSpeaker = !bothMissing && !firstHasNose ? second : first;
					Villager conversationSubject = conversationSpeaker == first ? second : first;
					List<List<String>> choices = bothMissing
						? TWO_MISSING_NOSES_CONVERSATIONS : ONE_MISSING_NOSE_CONVERSATIONS;
					List<String> sequence = choices.get(Math.floorMod(pair.hashCode() + (int) (ticks / LONG_COOLDOWN), choices.size()));
					if (playId(conversationSpeaker, sequence.getFirst(), "nose_conversation:" + pair + ":" + sequence.getFirst(),
							LONG_COOLDOWN, conversationSubject)) {
						holdListener(conversationSubject, conversationSpeaker, DialogueCatalog.byId(sequence.getFirst()).durationTicks());
						queueConversation(level, conversationSpeaker, conversationSubject, sequence);
						PAIR_TICKS.put(pair, 0);
					}
					return;
				}
				CastProfile firstCast = cast(first);
				CastProfile secondCast = cast(second);
				String meetId = meetDialogue(firstCast == CastProfile.VILLAGER ? secondCast : firstCast);
				if (meetId != null && (firstCast == CastProfile.VILLAGER || secondCast == CastProfile.VILLAGER)) {
					Villager speaker = firstCast == CastProfile.VILLAGER ? first : second;
					Villager subject = speaker == first ? second : first;
					if (playId(speaker, meetId, "meet:" + pair, LONG_COOLDOWN, subject)) {
						holdListener(subject, speaker, 80L);
						PAIR_TICKS.put(pair, 0);
					}
					return;
				}
				boolean atCampfire = nearBlock(level, first.blockPosition(), "campfire", 4)
					&& nearBlock(level, second.blockPosition(), "campfire", 4);
				boolean negativeGossip = isNegativeReputation(first, player) || isNegativeReputation(second, player);
				String conversation = atCampfire ? CAMPFIRE_CONVERSATION.getFirst()
					: first.getVehicle() != null && first.getVehicle() == second.getVehicle()
					? "zqfvby"
					: negativeGossip && first.getDeltaMovement().horizontalDistanceSqr()
						+ second.getDeltaMovement().horizontalDistanceSqr() < 0.0004
						? (Math.floorMod(pair.hashCode() + (int) (ticks / LONG_COOLDOWN), 2) == 0 ? "wrjbdd" : GOSSIP_CONVERSATION.getFirst())
						: wanderingConversation(pair).getFirst();
				if (playId(first, conversation, "conversation:" + pair + ":" + conversation, LONG_COOLDOWN, second)) {
					holdListener(second, first, DialogueCatalog.byId(conversation).durationTicks());
					if (conversation.startsWith("gmrypk")) queueWanderingConversation(level, first, second, wanderingConversation(pair));
					else if (atCampfire) queueConversation(level, first, second, CAMPFIRE_CONVERSATION);
					else if (conversation.equals(GOSSIP_CONVERSATION.getFirst())) queueConversation(level, first, second, GOSSIP_CONVERSATION);
					PAIR_TICKS.put(pair, 0);
					return;
				}
			}

			Villager adult = villagers.stream().findFirst().orElse(null);
			List<Villager> babies = nearbyVillagers(level, player.position(), 24).stream()
				.filter(villager -> villager.isBaby() && !villager.isSleeping()).toList();
			Villager baby = babies.stream().findFirst().orElse(null);
			if (babies.size() >= 2 && babies.get(0).distanceToSqr(babies.get(1)) <= 64.0
					&& babies.get(0).getDeltaMovement().horizontalDistanceSqr() + babies.get(1).getDeltaMovement().horizontalDistanceSqr() > 0.01) {
				playId(babies.get(0), "rfnirh", "baby_chase:" + orderedPair(babies.get(0).getUUID(), babies.get(1).getUUID()), LONG_COOLDOWN, babies.get(1));
			}
			if (adult != null && baby != null && adult.distanceToSqr(baby) <= 64.0) {
				playSharedId(adult, "pbmrxx", "see_baby:" + adult.getUUID() + ":" + baby.getUUID(), LONG_COOLDOWN, baby);
			}
		}
	}

	private static List<String> wanderingConversation(String pair) {
		return WANDERING_CONVERSATIONS.get(Math.floorMod(pair.hashCode() + (int) (ticks / LONG_COOLDOWN), WANDERING_CONVERSATIONS.size()));
	}

	private static void queueWanderingConversation(ServerLevel level, Villager first, Villager second, List<String> sequence) {
		queueConversation(level, first, second, sequence);
	}

	private static void queueConversation(ServerLevel level, Villager first, Villager second, List<String> sequence) {
		long due = ticks + DialogueCatalog.byId(sequence.getFirst()).durationTicks() + 2L;
		for (int index = 1; index < sequence.size(); index++) {
			Villager speaker = index % 2 == 1 ? second : first;
			Villager target = speaker == first ? second : first;
			String id = sequence.get(index);
			PENDING_SPEECH.add(new PendingSpeech(level, speaker.getUUID(), id, target.getUUID(), due));
			due += DialogueCatalog.byId(id).durationTicks() + 2L;
		}
	}

	private static Villager nearestConversationPartner(Villager villager, List<Villager> candidates) {
		return candidates.stream()
			.filter(candidate -> candidate != villager && !isBusy(candidate))
			.filter(candidate -> villager.distanceToSqr(candidate) <= 6.25 && villager.hasLineOfSight(candidate))
			.min(Comparator.comparingDouble(villager::distanceToSqr))
			.orElse(null);
	}

	private static boolean nearBlock(ServerLevel level, BlockPos origin, String pathPart, int range) {
		for (int x = -range; x <= range; x++) for (int y = -2; y <= 2; y++) for (int z = -range; z <= range; z++) {
			String path = BuiltInRegistries.BLOCK.getKey(level.getBlockState(origin.offset(x, y, z)).getBlock()).getPath();
			if (path.contains(pathPart)) return true;
		}
		return false;
	}

	private static boolean playHomeChestReaction(ServerLevel level, Player player, BlockPos chestPos) {
		boolean bedNearby = nearBlock(level, chestPos, "bed", 6);
		return nearbyVillagers(level, Vec3.atCenterOf(chestPos), OBSERVER_RANGE).stream()
			.filter(villager -> !villager.isBaby() && !villager.isSleeping() && cast(villager) == CastProfile.VILLAGER)
			.filter(villager -> bedNearby || homeMatches(villager, chestPos, 12))
			.filter(villager -> villager.hasLineOfSight(player))
			.min(Comparator.comparingDouble(villager -> villager.distanceToSqr(player)))
			.map(villager -> playId(villager, "qfhrlh", "home_chest:" + villager.getUUID(), SHORT_COOLDOWN, player))
			.orElse(false);
	}

	private static boolean homeMatches(Villager villager, BlockPos pos, int range) {
		return villager.getBrain().getMemory(MemoryModuleType.HOME)
			.map(home -> home.isCloseEnough(villager.level().dimension(), pos, range)).orElse(false);
	}

	private static void playFireworkReactions(ServerLevel level, Entity firework) {
		List<Villager> witnesses = nearbyVillagers(level, firework.position(), OBSERVER_RANGE).stream()
			.filter(villager -> !villager.isSleeping())
			.filter(villager -> villager.hasLineOfSight(firework)).toList();
		witnesses.stream().filter(villager -> !villager.isBaby())
			.min(Comparator.comparingDouble(villager -> villager.distanceToSqr(firework)))
			.ifPresent(villager -> playSharedId(villager, "dfdkli", "firework_spawn:" + villager.getUUID(), SHORT_COOLDOWN, firework));
		witnesses.stream().filter(Villager::isBaby)
			.min(Comparator.comparingDouble(villager -> villager.distanceToSqr(firework)))
			.ifPresent(villager -> playId(villager, "zeykfp", "firework_seen:" + villager.getUUID(), SHORT_COOLDOWN, firework));
	}

	private static void playLightningReaction(ServerLevel level, Entity lightning) {
		nearbyVillagers(level, lightning.position(), 128.0).stream()
			.filter(villager -> !villager.isBaby() && !villager.isSleeping() && villager.hasLineOfSight(lightning))
			.min(Comparator.comparingDouble(villager -> villager.distanceToSqr(lightning)))
			.ifPresent(villager -> playSharedId(villager, "ikrwzy", "lightning:" + villager.getUUID(), SHORT_COOLDOWN, lightning));
	}

	private static void processVillagerState(Villager villager) {
		ensureSpecialTrade(villager);
		VillagerSnapshot previous = VILLAGER_STATES.get(villager.getUUID());
		BlockPos workstation = findWorkstation(villager);
		VillagerSnapshot current = snapshot(villager, workstation != null);
		Map<String, Integer> oldInventory = VILLAGER_INVENTORIES.get(villager.getUUID());
		boolean sleeping = villager.isSleeping();
		boolean wasSleeping = LAST_SLEEPING.getOrDefault(villager.getUUID(), sleeping);
		if (sleeping) {
			if (wasSleeping) playTitle(villager, "Sleeping", "sleeping:" + villager.getUUID(), LONG_COOLDOWN);
			LAST_SLEEPING.put(villager.getUUID(), true);
			VILLAGER_INVENTORIES.put(villager.getUUID(), inventoryCounts(villager));
			VILLAGER_STATES.put(villager.getUUID(), current);
			return;
		}
		if (wasSleeping) playSharedTitle(villager, "Wake Up Naturally", "wake:" + villager.getUUID(), LONG_COOLDOWN, null);
		LAST_SLEEPING.put(villager.getUUID(), false);
		ItemStack pickedUp = findNewItem(villager, oldInventory);
		if (pickedUp != null) {
			String path = BuiltInRegistries.ITEM.getKey(pickedUp.getItem()).getPath();
			boolean armor = path.endsWith("_helmet") || path.endsWith("_chestplate") || path.endsWith("_leggings") || path.endsWith("_boots");
			String id = armor ? (pickedUp.isEnchanted() ? "habfnx" : "zjwpzi") : "dxmmiu";
			boolean food = path.equals("beetroot") || path.equals("bread") || path.equals("carrot") || path.equals("potato");
			Villager donor = villager.level() instanceof ServerLevel level ? nearbyVillagers(level, villager.position(), 4.0).stream()
				.filter(other -> other != villager).min(Comparator.comparingDouble(other -> other.distanceToSqr(villager))).orElse(null) : null;
			ServerPlayer nearbyPlayer = villager.level() instanceof ServerLevel level ? nearestPlayer(level, villager.position(), 6.0) : null;
			if (villager.isBaby() && donor != null && food
					&& playId(donor, "locuih", "share_food:" + donor.getUUID(), SHORT_COOLDOWN, villager)) {
				long due = ticks + DialogueCatalog.byId("locuih").durationTicks() + 2L;
				PENDING_SPEECH.add(new PendingSpeech((ServerLevel) villager.level(), villager.getUUID(), "saxuwk", donor.getUUID(), due));
			} else if (donor != null && !armor) {
				playId(villager, "ujyxfg", "pickup_villager:" + villager.getUUID() + ":" + path, SHORT_COOLDOWN, donor);
			} else if (nearbyPlayer != null) {
				playId(villager, armor ? id : "vkhrme", "pickup_player:" + villager.getUUID() + ":" + path, SHORT_COOLDOWN, nearbyPlayer);
			} else playId(villager, id, "pickup:" + villager.getUUID() + ":" + path, SHORT_COOLDOWN);
		}
		VILLAGER_INVENTORIES.put(villager.getUUID(), inventoryCounts(villager));
		if (previous != null) {
			if (previous.baby && !current.baby) {
				playId(villager, "smvnbj", "grow:" + villager.getUUID(), 1L);
			} else if ((previous.profession.equals("none") || previous.profession.equals("nitwit"))
					&& !current.profession.equals("none") && !current.profession.equals("nitwit")) {
				playId(villager, "zndzjx", "job:" + villager.getUUID(), 1L);
			} else if (current.level > previous.level) {
				playId(villager, current.level >= 5 ? "pnvkfy" : "fltegg", "level:" + villager.getUUID() + ":" + current.level, 1L);
			} else if (!current.name.isEmpty() && !current.name.equals(previous.name)) {
				String lower = current.name.toLowerCase(Locale.ROOT);
				String id = current.baby ? (lower.equals("dragon") ? "cmrqhw" : "gzsztp")
					: lower.equals("dinnerbone") ? "qmpcxi" : lower.equals("jeb") || lower.equals("jeb_") ? "armupg" : "spfsrr";
				playId(villager, id, "name:" + villager.getUUID() + ":" + current.name, 1L);
			}
			if (!previous.working && current.working) {
				playId(villager, "qawras", "work_start:" + villager.getUUID(), LONG_COOLDOWN, workstation == null ? null : Vec3.atCenterOf(workstation));
			} else if (current.working) {
				String work = ticks / LONG_COOLDOWN % 3L == 0L ? "sdhkke" : professionWorkDialogue(current.profession);
				if (work != null) playId(villager, work, "work:" + villager.getUUID() + ":" + work, LONG_COOLDOWN,
					workstation == null ? null : Vec3.atCenterOf(workstation));
			}
		}
		processConditionDialogues(villager);
		if (!current.profession.equals("none") && !current.profession.equals("nitwit") && workstation == null) {
			long since = NO_WORKSTATION_SINCE.computeIfAbsent(villager.getUUID(), ignored -> ticks);
			if (ticks - since >= 600L) {
				playId(villager, "ywzhwz", "missing_workstation:" + villager.getUUID(), LONG_COOLDOWN);
				NO_WORKSTATION_SINCE.put(villager.getUUID(), ticks);
			}
		} else NO_WORKSTATION_SINCE.remove(villager.getUUID());
		if (!villager.isBaby() && !villager.getBrain().hasMemoryValue(MemoryModuleType.MEETING_POINT)) {
			long since = NO_BELL_SINCE.computeIfAbsent(villager.getUUID(), ignored -> ticks);
			if (ticks - since >= 1200L) {
				playId(villager, "trphsn", "missing_bell:" + villager.getUUID(), LONG_COOLDOWN);
				NO_BELL_SINCE.put(villager.getUUID(), ticks);
			}
		} else NO_BELL_SINCE.remove(villager.getUUID());
		if (profession(villager).equals("farmer") && nearCrops(villager.level(), villager.blockPosition(), 4)) {
			playId(villager, "aobqjt", "farming:" + villager.getUUID(), LONG_COOLDOWN);
		}
		if (!villager.isSleeping() && villager.getDeltaMovement().horizontalDistanceSqr() > 0.0004
				&& villager.level() instanceof ServerLevel level) {
			if (!data(villager).vnap$hasNose()) {
				playId(villager, "dcvgnm", "no_nose_wander:" + villager.getUUID(), LONG_COOLDOWN);
			}
			float sunAngle = level.environmentAttributes().getValue(EnvironmentAttributes.SUN_ANGLE, villager.blockPosition());
			if (sunAngle >= 0.5F && sunAngle < 0.85F) {
				String id = level.dimension() == Level.END ? "iubjul" : level.dimension() == Level.NETHER ? "bvtmmz"
					: level.dimension() != Level.OVERWORLD ? "uhbigm"
					: villager.getBrain().hasMemoryValue(MemoryModuleType.HOME) ? "wkfbuv" : "uqguqj";
				playId(villager, id, "return_home:" + villager.getUUID() + ":" + id, LONG_COOLDOWN);
			}
		}
		long danger = LAST_DANGER.getOrDefault(villager.getUUID(), Long.MIN_VALUE / 2);
		if (ticks - danger >= 100L && ticks - danger <= 200L) {
			playId(villager, villager.isBaby() ? "wsxfok" : "wbbxpo", "calm:" + villager.getUUID(), LONG_COOLDOWN);
		}
		if (villager.isBaby() && villager.getDeltaMovement().horizontalDistanceSqr() > 0.02) {
			boolean weekend = LocalDate.now().getDayOfWeek() == DayOfWeek.SATURDAY || LocalDate.now().getDayOfWeek() == DayOfWeek.SUNDAY;
			playId(villager, weekend ? "vbclem" : "vhwksn", "baby_sprint:" + villager.getUUID(), LONG_COOLDOWN);
		}
		VILLAGER_STATES.put(villager.getUUID(), current);
	}

	private static Map<String, Integer> inventoryCounts(Villager villager) {
		Map<String, Integer> counts = new HashMap<>();
		for (ItemStack stack : villager.getInventory().getItems()) {
			if (!stack.isEmpty()) counts.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(), stack.getCount(), Integer::sum);
		}
		return counts;
	}

	private static ItemStack findNewItem(Villager villager, Map<String, Integer> previous) {
		if (previous == null) return null;
		Map<String, Integer> current = inventoryCounts(villager);
		for (ItemStack stack : villager.getInventory().getItems()) {
			if (stack.isEmpty()) continue;
			String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
			if (current.getOrDefault(path, 0) > previous.getOrDefault(path, 0)) return stack;
		}
		return null;
	}

	private static VillagerSnapshot snapshot(Villager villager, boolean working) {
		String name = villager.hasCustomName() && villager.getCustomName() != null ? villager.getCustomName().getString() : "";
		return new VillagerSnapshot(villager.isBaby(), profession(villager), villager.getVillagerData().level(), working, name,
			villager.hasEffect(MobEffects.POISON), villager.hasEffect(MobEffects.SLOWNESS), villager.hasEffect(MobEffects.WEAKNESS),
			villager.isInWall());
	}

	private static List<String> activeConditionDialogues(Villager villager) {
		List<String> active = new ArrayList<>();
		if (villager.hasEffect(MobEffects.POISON)) active.add("onindz");
		if (villager.hasEffect(MobEffects.SLOWNESS)) active.add("xemyaj");
		if (villager.hasEffect(MobEffects.WEAKNESS)) active.add("yebifs");
		if (villager.isInLava()) active.add("elryje");
		else if (villager.isOnFire()) active.add("etkxko");
		if (villager.isFullyFrozen()) active.add("igebly");
		if (villager.isInWall()) active.add("vnaodx");
		return active;
	}

	private static boolean canSpeakDuringCondition(Villager villager, String dialogue) {
		PendingConditionRelief relief = PENDING_CONDITION_RELIEF.get(villager.getUUID());
		List<String> active = activeConditionDialogues(villager);
		if (active.isEmpty()) return relief == null || relief.dialogueId.equals(dialogue);
		if (active.contains(dialogue)) return true;
		if (villager.isBaby()) return dialogue.equals("ahcvzd") || dialogue.equals("ecslqo");
		CastProfile profile = cast(villager);
		return profile != CastProfile.VILLAGER && (dialogue.equals(profile.hurt) || dialogue.equals(profile.attack));
	}

	private static void processConditionDialogues(Villager villager) {
		UUID id = villager.getUUID();
		List<String> active = activeConditionDialogues(villager);
		if (active.isEmpty()) {
			Set<String> history = CONDITION_HISTORY.remove(id);
			CONDITION_CURSORS.remove(id);
			if (history != null && !history.isEmpty()) queueConditionRelief(villager, history, ticks + 1L);
			return;
		}
		PENDING_CONDITION_RELIEF.remove(id);
		CONDITION_HISTORY.computeIfAbsent(id, ignored -> new HashSet<>()).addAll(active);
		PENDING_SPEECH.removeIf(pending -> pending.speakerId.equals(id) || id.equals(pending.targetId));
		List<String> reactions;
		if (villager.isBaby()) reactions = List.of("ecslqo");
		else {
			CastProfile profile = cast(villager);
			reactions = profile == CastProfile.VILLAGER ? active : List.of(profile.hurt);
		}
		ActiveSound sound = ACTIVE_SOUNDS.get(id);
		if (sound != null && sound.endTick > ticks && reactions.contains(sound.groupId)) return;
		if (isBusy(villager)) interrupt(villager);
		int cursor = Math.floorMod(CONDITION_CURSORS.getOrDefault(id, 0), reactions.size());
		for (int offset = 0; offset < reactions.size(); offset++) {
			int index = (cursor + offset) % reactions.size();
			String dialogue = reactions.get(index);
			String key = "condition:" + id + ":" + dialogue;
			if (playId(villager, dialogue, key, 1L)) {
				CONDITION_CURSORS.put(id, index + 1);
				break;
			}
		}
	}

	private static boolean queueConditionRelief(Villager villager, Set<String> conditions, long dueTick) {
		String dialogue;
		if (villager.isBaby()) dialogue = "wsxfok";
		else if (cast(villager) == CastProfile.VILLAGER) {
			dialogue = conditions.contains("vnaodx") ? "fxbysi" : "wbbxpo";
		} else return false;
		LAST_DANGER.remove(villager.getUUID());
		PENDING_CONDITION_RELIEF.put(villager.getUUID(),
			new PendingConditionRelief((ServerLevel) villager.level(), villager.getUUID(), dialogue, dueTick));
		return true;
	}

	private static void processPendingConditionRelief() {
		PENDING_CONDITION_RELIEF.entrySet().removeIf(entry -> {
			PendingConditionRelief pending = entry.getValue();
			if (pending.dueTick > ticks) return false;
			Entity entity = pending.level.getEntity(pending.villagerId);
			if (!(entity instanceof Villager villager) || !villager.isAlive()) return true;
			if (!activeConditionDialogues(villager).isEmpty()) return false;
			ActiveSound sound = ACTIVE_SOUNDS.get(villager.getUUID());
			if (sound != null && sound.endTick > ticks && DAMAGE_LOCK_DIALOGUES.contains(sound.groupId)) return false;
			if (isBusy(villager)) interrupt(villager);
			return playId(villager, pending.dialogueId,
				"condition_relief:" + villager.getUUID() + ":" + pending.dialogueId, 1L);
		});
	}

	private static boolean queueFreedSuffocationRelief(ServerLevel level, BlockPos pos) {
		AABB block = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
		boolean queued = false;
		for (Villager villager : nearbyVillagers(level, Vec3.atCenterOf(pos), 4.0)) {
			Set<String> history = CONDITION_HISTORY.get(villager.getUUID());
			VillagerSnapshot snapshot = VILLAGER_STATES.get(villager.getUUID());
			boolean wasSuffocating = history != null && history.contains("vnaodx")
				|| snapshot != null && snapshot.suffocating;
			if (!wasSuffocating || !villager.getBoundingBox().inflate(0.25).intersects(block)) continue;
			CONDITION_HISTORY.remove(villager.getUUID());
			CONDITION_CURSORS.remove(villager.getUUID());
			queued |= queueConditionRelief(villager, Set.of("vnaodx"), ticks + 1L);
		}
		return queued;
	}

	private static String profession(Villager villager) {
		return villager.getVillagerData().profession().unwrapKey()
			.map(key -> key.identifier().getPath()).orElse("none");
	}

	private static BlockPos findWorkstation(Villager villager) {
		String block = switch (profession(villager)) {
			case "armorer" -> "blast_furnace";
			case "butcher" -> "smoker";
			case "cartographer" -> "cartography_table";
			case "cleric" -> "brewing_stand";
			case "farmer" -> "composter";
			case "fisherman" -> "barrel";
			case "fletcher" -> "fletching_table";
			case "leatherworker" -> "cauldron";
			case "librarian" -> "lectern";
			case "mason" -> "stonecutter";
			case "shepherd" -> "loom";
			case "toolsmith" -> "smithing_table";
			case "weaponsmith" -> "grindstone";
			default -> null;
		};
		if (block == null) return null;
		BlockPos origin = villager.blockPosition();
		for (int x = -3; x <= 3; x++) for (int y = -2; y <= 2; y++) for (int z = -3; z <= 3; z++) {
			BlockPos pos = origin.offset(x, y, z);
			if (BuiltInRegistries.BLOCK.getKey(villager.level().getBlockState(pos).getBlock()).getPath().equals(block)) return pos;
		}
		return null;
	}

	private static boolean nearCrops(Level level, BlockPos origin, int range) {
		for (int x = -range; x <= range; x++) for (int y = -2; y <= 2; y++) for (int z = -range; z <= range; z++) {
			String path = BuiltInRegistries.BLOCK.getKey(level.getBlockState(origin.offset(x, y, z)).getBlock()).getPath();
			if (path.equals("wheat") || path.equals("carrots") || path.equals("potatoes") || path.equals("beetroots")
					|| path.equals("torchflower_crop") || path.equals("pitcher_crop")) return true;
		}
		return false;
	}

	private static String professionWorkDialogue(String profession) {
		return switch (profession) {
			case "armorer" -> "djpksc";
			case "butcher" -> "ueczyh";
			case "cartographer" -> "wuoloh";
			case "cleric" -> "hkowex";
			case "farmer" -> "umdvtb";
			case "fisherman" -> "tgggoh";
			case "fletcher" -> "fzjope";
			case "leatherworker" -> "ljewqf";
			case "librarian" -> "fezzjw";
			case "mason" -> "yldlzt";
			case "shepherd" -> "opxfuo";
			case "toolsmith" -> "ivktls";
			case "weaponsmith" -> "ccpvqj";
			default -> null;
		};
	}

	private static boolean playNearbyEntityContext(ServerLevel level, Villager speaker) {
		AABB area = speaker.getBoundingBox().inflate(NEARBY_SUBJECT_RANGE, 6.0, NEARBY_SUBJECT_RANGE);
		List<Entity> visibleEntities = level.getEntities(speaker, area, Entity::isAlive).stream()
			.filter(entity -> speaker.distanceToSqr(entity) <= NEARBY_SUBJECT_RANGE * NEARBY_SUBJECT_RANGE)
			.filter(speaker::hasLineOfSight)
			.sorted(Comparator.comparingDouble(speaker::distanceToSqr))
			.toList();
		for (Entity entity : visibleEntities) {
			String path = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
			String id = switch (path) {
				case "falling_block" -> "bodvsv";
				case "experience_orb" -> "cvltyw";
				case "tnt" -> "pmaqgq";
				case "firework_rocket" -> speaker.isBaby() ? "zeykfp" : null;
				default -> null;
			};
			if (id != null && playSharedId(speaker, id, "nearby_misc:" + speaker.getUUID() + ":" + entity.getUUID() + ":" + id, LONG_COOLDOWN, entity)) {
				return true;
			}
		}
		List<ItemEntity> droppedItems = level.getEntitiesOfClass(ItemEntity.class,
			AABB.ofSize(speaker.position(), 10, 10, 10), Entity::isAlive).stream()
			.filter(speaker::hasLineOfSight)
			.sorted(Comparator.comparingDouble(speaker::distanceToSqr))
			.toList();
		if (droppedItems.size() >= 5 && playSharedId(speaker, "zywcju", "item_pile:" + speaker.getUUID(), LONG_COOLDOWN,
				droppedItems.getFirst())) return true;
		return visibleEntities.stream()
			.filter(LivingEntity.class::isInstance)
			.map(LivingEntity.class::cast)
			.filter(entity -> entity != speaker && !(entity instanceof Player) && !(entity instanceof Villager))
			.anyMatch(entity -> {
				String path = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
				Entity target = entity;
				String dialogue = speaker.isBaby() && path.equals("iron_golem") ? "mqnapy" : null;
				if (dialogue == null && entity instanceof Sheep sheep && sheep.isSheared()) dialogue = "afxbav";
				if (dialogue == null && entity instanceof net.minecraft.world.entity.TamableAnimal tame && tame.isTame()) {
					dialogue = entity.isBaby() ? "sxikgq" : "aqxgxh";
				}
				if (dialogue == null && entity instanceof Mob mob && mob.getTarget() != null) {
					if (path.equals("bee")) dialogue = "bmimxe";
					else if (path.equals("iron_golem") && mob.getTarget() instanceof Player player) {
						dialogue = "qffeco";
						target = player;
					}
				}
				if (dialogue == null && (entity.isPassenger() || !entity.getPassengers().isEmpty())) dialogue = "dxeaal";
				if (dialogue == null && entity.isBaby()) dialogue = BABY_ENTITY_DIALOGUES.get(path);
				if (dialogue == null) dialogue = NEARBY_ENTITY_DIALOGUES.get(path);
				if (dialogue == null && !(entity instanceof WanderingTrader) && !(entity instanceof Sheep sheep && isWooly(sheep))) {
					dialogue = "gjtuqd";
				}
				return dialogue != null && speaker.hasLineOfSight(target)
					&& playSharedId(speaker, dialogue, "nearby:" + speaker.getUUID() + ":" + entity.getUUID() + ":" + dialogue, LONG_COOLDOWN, target);
			});
	}

	private static void onDamage(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source,
			float baseDamageTaken, float damageTaken, boolean blocked) {
		if (entity.entityTags().contains(DIALOGUE_TEST_TAG)) return;
		if (blocked || damageTaken <= 0.0F) return;
		playIronGolemAttackWitness(entity, source);
		playHurtWitness(entity);
		if (hasDamageLock(entity)) return;
		if (entity instanceof Sheep sheep && isWooly(sheep)) {
			Entity attacker = source.getEntity();
			String id = attacker instanceof Player ? "ncyeaw" : "eyiraw";
			playDamageDialogue(sheep, id, "hurt:" + sheep.getUUID(), source, attacker);
			return;
		}
		if (entity instanceof WanderingTrader trader) {
			Entity attacker = source.getEntity();
			String id = attacker instanceof Player ? "vevdkl" : "wyvzhk";
			playDamageDialogue(trader, id, "hurt:" + trader.getUUID(), source, attacker);
			return;
		}
		if (!(entity instanceof Villager villager)) return;
		LAST_DANGER.put(villager.getUUID(), ticks);
		if (villager.isBaby()) {
			String id = source.getEntity() instanceof Player player && source.getDirectEntity() == player ? "ahcvzd" : "ecslqo";
			playDamageDialogue(villager, id, "hurt:" + villager.getUUID(), source, source.getEntity());
			return;
		}
		CastProfile profile = cast(villager);
		if (profile != CastProfile.VILLAGER) {
			String id = source.getEntity() instanceof Player
				? profile.attack : profile.hurt;
			playDamageDialogue(villager, id, "hurt:" + villager.getUUID(), source, source.getEntity());
			return;
		}
		if (source.getEntity() instanceof Player player && homeMatches(villager, villager.blockPosition(), 4)) {
			if (isPlaying(villager, "lpuocy") || isPlaying(villager, "slbqfwbayahw")) return;
			interrupt(villager);
			playHomeAttackReaction(villager, player);
			return;
		}
		String dialogue = damageDialogue(source);
		if (playDamageDialogue(villager, dialogue, "hurt:" + villager.getUUID() + ":" + dialogue, source, source.getEntity())
				&& ready("panic:" + villager.getUUID(), SHORT_COOLDOWN) && villager.level() instanceof ServerLevel level) {
			COOLDOWNS.put("panic:" + villager.getUUID(), ticks);
			PENDING_SPEECH.add(new PendingSpeech(level, villager.getUUID(), "uzdxum",
				source.getEntity() == null ? null : source.getEntity().getUUID(), ticks + DialogueCatalog.byId(dialogue).durationTicks() + 2L));
		}
	}

	private static boolean playDamageDialogue(LivingEntity speaker, String id, String cooldownKey,
			net.minecraft.world.damagesource.DamageSource source, Entity target) {
		DialogueCatalog.DialogueGroup group = DialogueCatalog.byId(id);
		long cooldown = ONGOING_DAMAGE_DIALOGUES.contains(id) ? 1L : source.getEntity() == null ? SHORT_COOLDOWN
			: group == null ? 20L : group.durationTicks() + 20L;
		if (isPlaying(speaker, id) || !ready(cooldownKey, cooldown)) return false;
		interrupt(speaker);
		boolean sharedAdult = speaker instanceof WanderingTrader || speaker instanceof Villager villager
			&& cast(villager) == CastProfile.UNREACHABLE;
		return sharedAdult ? playSharedId(speaker, id, cooldownKey, cooldown, target)
			: playId(speaker, id, cooldownKey, cooldown, target);
	}

	private static void playHurtWitness(LivingEntity entity) {
		if (!(entity.level() instanceof ServerLevel level)) return;
		nearbyVillagers(level, entity.position(), OBSERVER_RANGE).stream()
			.filter(witness -> witness != entity && !witness.isBaby() && !witness.isSleeping()
				&& !isBusy(witness) && witness.hasLineOfSight(entity))
			.min(Comparator.comparingDouble(witness -> witness.distanceToSqr(entity)))
			.ifPresent(witness -> playSharedId(witness, "pkvhpv", "witness_hurt:" + witness.getUUID(), SHORT_COOLDOWN, entity));
	}

	private static void playIronGolemAttackWitness(LivingEntity entity,
			net.minecraft.world.damagesource.DamageSource source) {
		if (!(entity.level() instanceof ServerLevel level)) return;
		ServerPlayer player = null;
		String hurtType = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
		Entity attacker = source.getEntity();
		String attackerType = attacker == null ? "" : BuiltInRegistries.ENTITY_TYPE.getKey(attacker.getType()).getPath();
		if (hurtType.equals("iron_golem") && attacker instanceof ServerPlayer serverPlayer) player = serverPlayer;
		else if (entity instanceof ServerPlayer serverPlayer && attackerType.equals("iron_golem")) player = serverPlayer;
		if (player == null || player.isCreative()) return;
		ServerPlayer subject = player;
		nearbyVillagers(level, entity.position(), OBSERVER_RANGE).stream()
			.filter(witness -> !witness.isBaby() && !witness.isSleeping() && !isBusy(witness)
				&& witness.hasLineOfSight(subject))
			.min(Comparator.comparingDouble(witness -> witness.distanceToSqr(entity)))
			.ifPresent(witness -> playSharedId(witness, "qffeco", "golem_attack:" + witness.getUUID(),
				SHORT_COOLDOWN, subject));
	}

	private static String damageDialogue(net.minecraft.world.damagesource.DamageSource source) {
		String attacker = source.getEntity() == null ? "" : BuiltInRegistries.ENTITY_TYPE.getKey(source.getEntity().getType()).getPath();
		String direct = source.getDirectEntity() == null ? "" : BuiltInRegistries.ENTITY_TYPE.getKey(source.getDirectEntity().getType()).getPath();
		if (direct.equals("arrow")) return "huhcbd";
		if (direct.equals("snowball")) return "dlrxes";
		if (source.is(DamageTypes.FALLING_ANVIL)) return "yzqpvi";
		if (source.is(DamageTypes.STALAGMITE) || source.is(DamageTypes.FALLING_STALACTITE)) return "nsxmkr";
		if (direct.equals("firework_rocket") || source.is(DamageTypes.FIREWORKS)) return "gesjov";
		if (direct.equals("potion") || direct.equals("lingering_potion")) {
			return source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC) ? "gacgtq" : "hmadgp";
		}
		if (source.getEntity() instanceof Player player && source.getDirectEntity() == player) {
			return weaponAttackDialogue(player.getMainHandItem());
		}
		if (attacker.equals("evoker")) return "nkcoqb";
		if (attacker.equals("pillager")) return "qhpyaw";
		if (attacker.equals("ravager")) return "uveohs";
		if (attacker.equals("vex")) return "caykki";
		if (attacker.equals("vindicator")) return "swomdw";
		if (attacker.equals("witch")) return "rtikom";
		if (attacker.equals("zoglin")) return "hfmwvf";
		if (attacker.contains("zombie") || attacker.equals("drowned") || attacker.equals("husk")) return "mytmrk";
		if (source.is(DamageTypes.LIGHTNING_BOLT)) return "ikrwzy";
		if (source.is(DamageTypes.EXPLOSION) || source.is(DamageTypes.PLAYER_EXPLOSION)) return "fcbygh";
		if (source.is(DamageTypes.LAVA)) return "elryje";
		if (source.is(DamageTypes.IN_FIRE) || source.is(DamageTypes.ON_FIRE) || source.is(DamageTypes.CAMPFIRE)
				|| source.is(DamageTypes.HOT_FLOOR) || source.is(DamageTypes.SULFUR_CUBE_HOT)) return "etkxko";
		if (source.is(DamageTypes.CACTUS) || source.is(DamageTypes.SWEET_BERRY_BUSH)) return "rogpvp";
		if (source.is(DamageTypes.FREEZE)) return "igebly";
		if (source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING)) return "vnaodx";
		if (source.is(DamageTypes.FALL)) return "cifbit";
		return "wyvzhk";
	}

	private static void onDeath(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source) {
		if (entity.entityTags().contains(DIALOGUE_TEST_TAG)) return;
		interrupt(entity);
		if (!(entity.level() instanceof ServerLevel level)) return;
		if (entity.entityTags().contains(NATURAL_SPECIAL_TAG)) clearNaturalSpecial(level, entity);
		if (entity instanceof Villager villager) {
			BUSY_UNTIL.remove(villager.getUUID());
			if (villager.isBaby()) playId(villager, "ecslqo", "death:" + villager.getUUID(), 1L);
			else playSharedId(villager, "hivgme", "death:" + villager.getUUID(), 1L, null);
		}
		if (entity instanceof Villager || entity instanceof WanderingTrader
				|| entity instanceof Sheep sheep && isWooly(sheep)) {
			nearbyVillagers(level, entity.position(), OBSERVER_RANGE).stream()
				.filter(witness -> witness != entity && !witness.isBaby() && !witness.isSleeping())
				.min(Comparator.comparingDouble(witness -> witness.distanceToSqr(entity)))
				.ifPresent(witness -> playSharedId(witness, "pmqrpb", "witness_death:" + witness.getUUID(), SHORT_COOLDOWN, entity));
		} else if (entity instanceof ServerPlayer player) {
			int deaths = PLAYER_DEATHS.merge(player.getUUID(), 1, Integer::sum);
			String id = level.getLevelData().isHardcore() ? "elcjbb" : deaths > 1 ? "dxcjqn" : "hzjycq";
			nearbyVillagers(level, player.position(), OBSERVER_RANGE).stream()
				.filter(witness -> !witness.isBaby() && !witness.isSleeping() && witness.hasLineOfSight(player))
				.min(Comparator.comparingDouble(witness -> witness.distanceToSqr(player)))
				.ifPresent(witness -> playSharedId(witness, id, "player_death:" + witness.getUUID() + ":" + deaths, SHORT_COOLDOWN, player));
		}
	}

	private static InteractionResult onUseEntity(Player player, Entity entity, InteractionHand hand) {
		if (entity.entityTags().contains(DIALOGUE_TEST_TAG)) return InteractionResult.SUCCESS;
		if (entity instanceof Villager villager) {
			if (cast(villager) == CastProfile.UNREACHABLE) {
				repelPlayer(villager, player);
				return InteractionResult.SUCCESS;
			}
			ItemStack heldStack = player.getItemInHand(hand);
			String heldItem = BuiltInRegistries.ITEM.getKey(heldStack.getItem()).getPath();
			InteractionResult cosmeticResult = interactWithCosmetic(player, villager, heldStack);
			if (cosmeticResult != InteractionResult.PASS) return cosmeticResult;
			String gift = foodGiftDialogue(villager.isBaby(), heldItem);
			if (gift != null) {
				playId(villager, gift, "food_gift:" + villager.getUUID() + ":" + heldItem, SHORT_COOLDOWN, player);
				return InteractionResult.PASS;
			}
			int offeredSign = signType(heldStack);
			if (offeredSign >= 0 && !villager.isBaby()) {
				VillagerNewsData state = data(villager);
				int previousSign = state.vnap$signType();
				if (previousSign == offeredSign) return InteractionResult.SUCCESS;
				if (previousSign >= 0) villager.spawnAtLocation((ServerLevel) villager.level(), signItem(previousSign));
				state.vnap$setSignType(offeredSign);
				consume(player, heldStack);
				villager.level().playSound(null, villager.blockPosition(), SoundEvents.HORSE_STEP_WOOD, SoundSource.NEUTRAL, 1.0F, 1.0F);
				if (previousSign < 0) {
					state.vnap$setSignMessage(villager.getRandom().nextInt(87));
					playId(villager, "vqlrqf", "sign_gift:" + villager.getUUID(), SHORT_COOLDOWN, player);
				}
				return InteractionResult.SUCCESS;
			}
			if (villager.isSleeping()) {
				UUID id = villager.getUUID();
				WAKE_SOURCES.put(id, player.getUUID());
				INTERRUPTED_SLEEP.add(id);
				villager.stopSleeping();
				return InteractionResult.PASS;
			}
			ensureSpecialTrade(villager);
			if (!villager.isBaby() && hand == InteractionHand.MAIN_HAND && player.level() instanceof ServerLevel level) {
				ACTIVE_TRADES.put(player.getUUID(), new TradeSession(level, villager.getUUID(), ticks));
			}
			if (villager.isBaby()) playId(villager, "aezdiy", "baby_trade:" + villager.getUUID(), SHORT_COOLDOWN, player);
		} else if (entity instanceof WanderingTrader trader) {
			if (hand == InteractionHand.MAIN_HAND && player.level() instanceof ServerLevel level) {
				ACTIVE_TRADES.put(player.getUUID(), new TradeSession(level, trader.getUUID(), ticks));
			}
		} else if (entity instanceof Sheep sheep && isWooly(sheep)) {
			String held = BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).getPath();
			playId(sheep, held.equals("shears") ? "jqaekk" : "fskcce", "interact:" + sheep.getUUID(), SHORT_COOLDOWN, player);
		} else if (BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).getPath().equals("lead")
				&& player.level() instanceof ServerLevel level) {
			playObserved(level, player, entity.position(), "Use a Lead", SHORT_COOLDOWN);
		}
		if (entity instanceof Sheep && BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).getPath().equals("shears")
				&& player.level() instanceof ServerLevel level) {
			playObserved(level, player, entity.position(), "Shear a Sheep", SHORT_COOLDOWN);
		}
		return InteractionResult.PASS;
	}

	private static InteractionResult interactWithCosmetic(Player player, Villager villager, ItemStack stack) {
		VillagerNewsData state = data(villager);
		if (stack.getItem() == Items.SHEARS && state.vnap$signType() >= 0) {
			if (villager.level() instanceof ServerLevel level) {
				villager.spawnAtLocation(level, signItem(state.vnap$signType()));
				level.playSound(null, villager.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.NEUTRAL, 1.0F, 1.0F);
			}
			state.vnap$setSignMessage(-1);
			state.vnap$setSignType(-1);
			damageShears(player, stack);
			return InteractionResult.SUCCESS;
		}
		if (isAxe(stack) && state.vnap$signType() >= 0) {
			int direction = player.isShiftKeyDown() ? -1 : 1;
			int message = Math.floorMod(state.vnap$signMessage() + direction, 87);
			state.vnap$setSignMessage(message);
			if (villager.level() instanceof ServerLevel level) {
				level.playSound(null, villager.blockPosition(), SoundEvents.HORSE_STEP_WOOD, SoundSource.NEUTRAL, 1.0F, 1.0F);
			}
			if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendOverlayMessage(Component.literal("Sign message " + (message + 1) + " / 87"));
			}
			return InteractionResult.SUCCESS;
		}
		if (stack.getItem() == Items.SHEARS && !villager.isBaby()) {
			if (state.vnap$cosmetic() != 0) {
				if (villager.level() instanceof ServerLevel level) {
					net.minecraft.world.item.Item item = VillagerNewsItems.cosmeticItem(state.vnap$cosmetic());
					if (item != null) villager.spawnAtLocation(level, new ItemStack(item));
					level.playSound(null, villager.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.NEUTRAL, 1.0F, 1.0F);
				}
				state.vnap$setCosmetic(0);
				damageShears(player, stack);
				playId(villager, "ckjbyd", "remove_cosmetic:" + villager.getUUID(), 1L, player);
				return InteractionResult.SUCCESS;
			}
			if (state.vnap$hasNose()) {
				if (villager.level() instanceof ServerLevel level) {
					villager.spawnAtLocation(level, new ItemStack(VillagerNewsItems.VILLAGER_NOSE));
					level.playSound(null, villager.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.NEUTRAL, 1.0F, 1.0F);
				}
				state.vnap$setHasNose(false);
				damageShears(player, stack);
				playId(villager, "jktrnd", "shear_nose:" + villager.getUUID(), 1L, player);
				return InteractionResult.SUCCESS;
			}
		}
		if (stack.getItem() == VillagerNewsItems.VILLAGER_NOSE) {
			if (state.vnap$hasNose()) {
				playId(villager, "akfekx", "second_nose:" + villager.getUUID(), SHORT_COOLDOWN, player);
				return InteractionResult.SUCCESS;
			}
			consume(player, stack);
			state.vnap$setHasNose(true);
			playId(villager, "kxrhxt", "return_nose:" + villager.getUUID(), 1L, player);
			return InteractionResult.SUCCESS;
		}
		int cosmetic = VillagerNewsItems.cosmetic(stack.getItem());
		if (cosmetic == 0) return InteractionResult.PASS;
		if (state.vnap$cosmetic() != 0) return InteractionResult.SUCCESS;
		consume(player, stack);
		state.vnap$setCosmetic(cosmetic);
		playGivenCosmetic(villager, player, cosmetic);
		return InteractionResult.SUCCESS;
	}

	private static void playGivenCosmetic(Villager villager, Player player, int cosmetic) {
		String id;
		if (villager.isBaby()) {
			id = switch (cosmetic) {
				case 1 -> "svdjdk";
				case 2 -> "cxeziv";
				case 3 -> "riezum";
				case 4 -> "rlkdqd";
				default -> "orogba";
			};
		} else {
			String special = switch (cosmetic) {
				case 2 -> "wurmgu";
				case 3 -> "inirxg";
				case 4 -> "ozxzla";
				default -> null;
			};
			id = special != null && (cosmetic == 3 || ThreadLocalRandom.current().nextInt(3) != 0)
				? special : "orogba";
		}
		playId(villager, id, "give_cosmetic:" + villager.getUUID() + ":" + cosmetic, 1L, player);
	}

	private static void damageShears(Player player, ItemStack stack) {
		if (!player.isCreative() && player instanceof ServerPlayer serverPlayer) {
			stack.hurtAndBreak(1, serverPlayer.level(), serverPlayer, ignored -> {
			});
		}
	}

	private static void consume(Player player, ItemStack stack) {
		if (!player.isCreative()) stack.shrink(1);
	}

	private static boolean isStandingSign(ItemStack stack) {
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return path.endsWith("_sign") && !path.endsWith("_hanging_sign");
	}

	public static int signType(ItemStack stack) {
		if (!isStandingSign(stack)) return -1;
		return switch (BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath()) {
			case "oak_sign" -> 0;
			case "spruce_sign" -> 1;
			case "birch_sign" -> 2;
			case "jungle_sign" -> 3;
			case "acacia_sign" -> 4;
			case "dark_oak_sign" -> 5;
			case "mangrove_sign" -> 6;
			case "cherry_sign" -> 7;
			case "pale_oak_sign" -> 8;
			case "bamboo_sign" -> 9;
			case "crimson_sign" -> 10;
			case "warped_sign" -> 11;
			default -> -1;
		};
	}

	public static ItemStack signItem(int type) {
		return new ItemStack(switch (type) {
			case 1 -> Items.SPRUCE_SIGN;
			case 2 -> Items.BIRCH_SIGN;
			case 3 -> Items.JUNGLE_SIGN;
			case 4 -> Items.ACACIA_SIGN;
			case 5 -> Items.DARK_OAK_SIGN;
			case 6 -> Items.MANGROVE_SIGN;
			case 7 -> Items.CHERRY_SIGN;
			case 8 -> Items.PALE_OAK_SIGN;
			case 9 -> Items.BAMBOO_SIGN;
			case 10 -> Items.CRIMSON_SIGN;
			case 11 -> Items.WARPED_SIGN;
			default -> Items.OAK_SIGN;
		});
	}

	private static boolean isAxe(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("_axe");
	}

	private static String foodGiftDialogue(boolean baby, String item) {
		if (baby) return switch (item) {
			case "beetroot" -> "qrdzmt";
			case "bread" -> "hbalps";
			case "carrot" -> "hcdvqm";
			case "potato" -> "gotjxf";
			default -> null;
		};
		return switch (item) {
			case "beetroot" -> "rlfjux";
			case "bread" -> "bbjsik";
			case "carrot" -> "nqktml";
			case "potato" -> "ytydjc";
			case "wheat" -> "ebyrtk";
			default -> null;
		};
	}

	private static void onAttackEntity(Player player, Entity entity) {
		if (!(entity instanceof Villager villager) || !villager.isSleeping()) return;
		UUID id = villager.getUUID();
		WAKE_SOURCES.put(id, player.getUUID());
		INTERRUPTED_SLEEP.add(id);
		villager.stopSleeping();
	}

	private static void playHomeAttackReaction(Villager villager, Player player) {
		if (!(villager.level() instanceof ServerLevel level)) return;
		Villager witness = nearbyVillagers(level, villager.position(), 10.0).stream()
			.filter(other -> other != villager && !other.isBaby() && !other.isSleeping() && other.hasLineOfSight(villager))
			.min(Comparator.comparingDouble(other -> other.distanceToSqr(villager))).orElse(null);
		if (witness != null && playSharedId(witness, "slbqfwswxeva", "home_witness:" + witness.getUUID(), 20L, villager)) {
			long due = ticks + DialogueCatalog.byId("slbqfwswxeva").durationTicks() + 2L;
			PENDING_SPEECH.add(new PendingSpeech(level, villager.getUUID(), "slbqfwbayahw", witness.getUUID(), due));
		} else playId(villager, "lpuocy", "attacked_home:" + villager.getUUID(), 20L, player);
	}

	private static String weaponAttackDialogue(ItemStack stack) {
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		if (path.endsWith("_sword")) return "rueszy";
		if (path.endsWith("_axe")) return "yjctyw";
		if (path.endsWith("_hoe")) return "qqyjjg";
		if (path.endsWith("_shovel")) return "hpnsfu";
		return "vevdkl";
	}

	private static String selectBreakContext(BlockState state, PlayerObservation observation) {
		if (ticks - observation.lastBreakTick <= 30L) observation.breakStreak++;
		else observation.breakStreak = 1;
		observation.lastBreakTick = ticks;
		if (observation.breakStreak >= 4) return "Break Multiple Blocks";
		String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		if (path.equals("wheat") || path.equals("carrots") || path.equals("potatoes") || path.equals("beetroots")
			|| path.equals("torchflower_crop") || path.equals("pitcher_crop")) return "Harvest Crops";
		if (path.contains("flower") || path.contains("candle") || path.contains("coral") || path.contains("banner")
			|| path.contains("decorated_pot")) return "Break a Decorative Block";
		if (path.endsWith("_door")) return "Break a Door";
		if (path.endsWith("_bed")) return "Break a Bed";
		if (path.equals("bell")) return "Break a Bell";
		if (isWorkstation(path)) return "Break a Workstation";
		if (path.contains("log") || path.contains("wood") || path.contains("stem") || path.contains("hyphae")) return "Break Wood";
		if (path.contains("stone") || path.contains("deepslate") || path.contains("cobblestone")) return "Break Stone";
		return "Break a Block";
	}

	private static String selectPlaceContext(Block block, ServerLevel level, BlockPos position) {
		String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
		if (level.dimension() == Level.END) return "Place a Block from the End";
		if (level.dimension() == Level.NETHER) return "Place a Block from the Nether";
		String biome = level.getBiome(position).unwrapKey().map(key -> key.identifier().getPath()).orElse("");
		if (biome.contains("ocean")) return "Place a Block from the Ocean";
		if (path.equals("daylight_detector")) return "Place a Daylight Detector";
		if (path.equals("detector_rail")) return "Place a Detector Rail";
		if (path.equals("lightning_rod")) return "Place a Lightning Rod";
		if (path.equals("melon")) return "Place a Melon";
		if (path.equals("observer")) return "Place an Observer";
		if (path.endsWith("pressure_plate")) return "Place a Pressure Plate";
		if (path.equals("pumpkin")) return "Place a Pumpkin";
		if (path.equals("redstone_lamp")) return "Place a Redstone Lamp";
		if (path.equals("repeater")) return "Place a Redstone Repeater";
		if (path.equals("redstone_torch") || path.equals("redstone_wall_torch")) return "Place a Redstone Torch";
		if (path.contains("sculk_sensor")) return "Place a Sculk Sensor";
		if (path.equals("tripwire_hook")) return "Place a Tripwire Hook";
		if (path.equals("jack_o_lantern")) return "Place a Jack o'Lantern";
		if (path.equals("end_stone")) return "Place End Stone";
		if (path.contains("purpur")) return "Place Purpur";
		if (path.contains("copper")) return "Place a Copper Block";
		if (path.contains("brick")) return "Place Bricks";
		if (path.equals("powder_snow")) return "Place Powder Snow";
		if (path.equals("light")) return "Place a Light Block";
		if (path.equals("barrier") || path.contains("command_block") || path.equals("structure_block") || path.equals("jigsaw")) {
			return "Place a Creative-Only Block";
		}
		if (path.endsWith("sand") || path.endsWith("gravel") || path.equals("anvil")) return "Place a Gravity-Affected Block";
		if (path.equals("iron_block") || path.equals("gold_block") || path.equals("diamond_block")
			|| path.equals("emerald_block") || path.equals("netherite_block")) return "Place a Valuable Block";
		if (path.contains("redstone") || path.equals("lever") || path.endsWith("button") || path.endsWith("rail")) {
			return "Place a Redstone Component";
		}
		if (path.endsWith("_bed")) return "Place a Bed";
		if (path.equals("chest")) return "Place a Chest";
		if (path.equals("trapped_chest")) return "Place a Trapped Chest";
		if (path.equals("crafting_table")) return "Place a Crafting Table";
		if (path.equals("furnace")) return "Place a Furnace";
		if (path.equals("bookshelf")) return "Place a Bookshelf";
		if (path.equals("jukebox")) return "Place a Jukebox";
		if (path.equals("armor_stand")) return "Place an Armor Stand";
		if (path.equals("beacon")) return "Place a Beacon";
		if (isWorkstation(path)) return "Place a Workstation";
		if (path.endsWith("_log") || path.endsWith("_wood") || path.endsWith("_planks")) return "Place Wood";
		if (path.contains("dirt")) return "Place Dirt";
		if (path.contains("leaves") || path.contains("sapling") || path.contains("flower")) return "Place Leaves or Plants";
		if (path.contains("wool")) return "Place Wool";
		if (path.contains("glass")) return "Place Glass";
		if (path.contains("concrete_powder")) return "Place Concrete Powder";
		if (path.contains("concrete")) return "Place Concrete";
		if (path.contains("glazed_terracotta")) return "Place Glazed Terracotta";
		if (path.contains("terracotta")) return "Place Terracotta";
		if (path.equals("iron_block")) return "Place an Iron Block";
		if (path.equals("gold_block")) return "Place a Gold Block";
		if (path.equals("diamond_block")) return "Place a Diamond Block";
		if (path.equals("emerald_block")) return "Place an Emerald Block";
		if (path.equals("lapis_block")) return "Place a Lapis Block";
		if (path.contains("ice")) return "Place Ice";
		if (path.contains("snow")) return "Place Snow";
		if (path.endsWith("_button")) return "Place a Button";
		if (path.equals("lever")) return "Place a Lever";
		return "Place a Block";
	}

	private static String selectUseBlockContext(BlockState state) {
		String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		if (path.endsWith("_button")) return "Press a Button";
		if (path.equals("bell")) return "Hear a Bell Ring";
		if (path.equals("lever")) return "Flip a Lever";
		if (path.endsWith("_door")) return "Use a Door";
		if (path.endsWith("_fence_gate")) {
			return state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN)
				? "Close a Fence Gate" : "Open a Fence Gate";
		}
		if (path.equals("crafter")) return "Use a Crafter";
		if (path.equals("dispenser")) return "Use a Dispenser";
		if (path.equals("dropper")) return "Use a Dropper";
		if (path.equals("jukebox")) return "Use a Jukebox";
		if (path.equals("loom")) return "Use a Loom";
		if (path.contains("shulker_box")) return "Use a Shulker Box";
		if (path.equals("stonecutter")) return "Use a Stonecutter";
		if (path.equals("beacon")) return "Use a Beacon";
		if (path.contains("campfire")) return "Use a Campfire";
		if (path.equals("cartography_table")) return "Use a Cartography Table";
		if (path.equals("cauldron") || path.endsWith("_cauldron")) return "Use a Cauldron";
		if (path.equals("chiseled_bookshelf")) return "Use a Chiseled Bookshelf";
		if (path.equals("composter")) return "Use a Composter";
		if (path.equals("ender_chest")) return "Use an Ender Chest";
		if (path.contains("shelf")) return "Use Shelves";
		if (path.contains("chest")) return "Open a Chest";
		if (path.equals("crafting_table")) return "Use a Crafting Table";
		if (path.equals("furnace") || path.equals("blast_furnace") || path.equals("smoker")) return "Use a Furnace";
		if (path.equals("anvil") || path.endsWith("_anvil")) return "Use an Anvil";
		if (path.equals("enchanting_table")) return "Use an Enchanting Table";
		if (path.equals("brewing_stand")) return "Use a Brewing Stand";
		if (path.equals("grindstone")) return "Use a Grindstone";
		if (path.equals("smithing_table")) return "Use a Smithing Table";
		return null;
	}

	private static String selectHeldBlockContext(ItemStack stack, BlockState clicked) {
		String item = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		String block = BuiltInRegistries.BLOCK.getKey(clicked.getBlock()).getPath();
		if (item.equals("redstone")) return "Place Redstone Dust";
		if ((item.equals("flint_and_steel") || item.equals("fire_charge")) && block.equals("tnt")) return "Light TNT";
		if (block.equals("tnt")) return "See TNT";
		if ((item.equals("flint_and_steel") || item.equals("fire_charge")) && block.contains("campfire")) return "Light a Campfire";
		if ((item.equals("flint_and_steel") || item.equals("fire_charge")) && block.contains("candle")) return "Light a Candle";
		if (block.contains("campfire") && (item.contains("beef") || item.contains("porkchop") || item.contains("chicken")
			|| item.contains("mutton") || item.contains("rabbit") || item.equals("potato"))) return "Cook Food on a Campfire";
		if ((item.equals("water_bucket") || item.endsWith("_shovel")) && block.contains("campfire")) return "Extinguish a Campfire";
		if (item.equals("water_bucket") && block.contains("candle")) return "Extinguish a Candle";
		if (item.equals("shears") && block.equals("pumpkin")) return "Carve a Pumpkin";
		return null;
	}

	private static String selectUseItemContext(ItemStack stack) {
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		if (path.equals("firework_rocket")) return "Set Off a Firework";
		if (path.equals("ender_pearl")) return "Teleport with an Ender Pearl";
		if (path.equals("snowball")) return "Snowball";
		if (path.contains("apple") || path.contains("bread") || path.contains("carrot") || path.contains("potato")
			|| path.contains("beef") || path.contains("porkchop") || path.contains("chicken") || path.contains("mutton")
			|| path.contains("rabbit") || path.contains("stew") || path.contains("berries") || path.contains("melon")) {
			return "Eat Food";
		}
		return null;
	}

	private static boolean isWorkstation(String path) {
		return path.equals("composter") || path.equals("barrel") || path.equals("blast_furnace")
			|| path.equals("smoker") || path.equals("cartography_table") || path.equals("brewing_stand")
			|| path.equals("fletching_table") || path.equals("cauldron") || path.equals("lectern")
			|| path.equals("stonecutter") || path.equals("loom") || path.equals("smithing_table")
			|| path.equals("grindstone");
	}

	private static boolean playObserved(ServerLevel level, Player player, Vec3 eventPosition, String title, long cooldown) {
		Villager speaker = nearbyVillagers(level, eventPosition, OBSERVER_RANGE).stream()
			.filter(villager -> !villager.isBaby() && !villager.isSleeping())
			.filter(villager -> cast(villager) == CastProfile.VILLAGER)
			.filter(villager -> villager.hasLineOfSight(player))
			.min(Comparator.comparingDouble(villager -> villager.distanceToSqr(eventPosition)))
			.orElse(null);
		DialogueCatalog.DialogueGroup group = DialogueCatalog.byTitle(title, "villager");
		return speaker != null && group != null && play(speaker, group,
			"observed:" + speaker.getUUID() + ":" + title, cooldown, player, null, true);
	}

	private static List<Villager> nearbyVillagers(ServerLevel level, Vec3 position, double range) {
		AABB area = AABB.ofSize(position, range * 2.0, range, range * 2.0);
		return level.getEntitiesOfClass(Villager.class, area,
			entity -> entity.isAlive() && !entity.entityTags().contains(DIALOGUE_TEST_TAG));
	}

	private static boolean playTitle(LivingEntity speaker, String title, String cooldownKey, long cooldown) {
		DialogueCatalog.DialogueGroup group = DialogueCatalog.byTitle(title, speakerType(speaker));
		return group != null && play(speaker, group, cooldownKey, cooldown, null, null);
	}

	private static boolean playTitle(LivingEntity speaker, String title, String cooldownKey, long cooldown, Entity target) {
		DialogueCatalog.DialogueGroup group = DialogueCatalog.byTitle(title, speakerType(speaker));
		return group != null && play(speaker, group, cooldownKey, cooldown, target, null);
	}

	private static boolean playSharedTitle(LivingEntity speaker, String title, String cooldownKey, long cooldown, Entity target) {
		DialogueCatalog.DialogueGroup group = DialogueCatalog.byTitle(title, "villager");
		return group != null && play(speaker, group, cooldownKey, cooldown, target, null, true);
	}

	private static boolean playId(LivingEntity speaker, String id, String cooldownKey, long cooldown) {
		DialogueCatalog.DialogueGroup group = DialogueCatalog.byId(id);
		return group != null && play(speaker, group, cooldownKey, cooldown, null, null);
	}

	private static boolean playId(LivingEntity speaker, String id, String cooldownKey, long cooldown, Entity target) {
		DialogueCatalog.DialogueGroup group = DialogueCatalog.byId(id);
		return group != null && play(speaker, group, cooldownKey, cooldown, target, null);
	}

	private static boolean playSharedId(LivingEntity speaker, String id, String cooldownKey, long cooldown, Entity target) {
		DialogueCatalog.DialogueGroup group = DialogueCatalog.byId(id);
		return group != null && play(speaker, group, cooldownKey, cooldown, target, null, true);
	}

	private static boolean playId(LivingEntity speaker, String id, String cooldownKey, long cooldown, Vec3 target) {
		DialogueCatalog.DialogueGroup group = DialogueCatalog.byId(id);
		return group != null && play(speaker, group, cooldownKey, cooldown, null, target);
	}

	private static boolean play(LivingEntity speaker, DialogueCatalog.DialogueGroup group, String cooldownKey, long cooldown,
			Entity target, Vec3 targetPosition) {
		return play(speaker, group, cooldownKey, cooldown, target, targetPosition, false);
	}

	private static boolean play(LivingEntity speaker, DialogueCatalog.DialogueGroup group, String cooldownKey, long cooldown,
			Entity target, Vec3 targetPosition, boolean sharedAdult) {
		boolean sharedVillagerVoice = speaker instanceof Villager villager && !villager.isBaby()
			&& cast(villager) == CastProfile.VILLAGER
			|| speaker instanceof WanderingTrader;
		boolean validSpeaker = matchesSpeaker(speaker, group) || sharedAdult && sharedVillagerVoice
			&& group.speaker().equals("villager");
		boolean blockedByCondition = speaker instanceof Villager conditionVillager
			&& !canSpeakDuringCondition(conditionVillager, group.id());
		if (!(speaker.level() instanceof ServerLevel level) || !validSpeaker
				|| !level.getServer().tickRateManager().runsNormally()
				|| !VillagerNewsSettings.dialogueEnabled()
				|| blockedByCondition
				|| speaker instanceof Villager sleepingVillager && sleepingVillager.isSleeping() && !group.id().equals("asqzby")
				|| isBusy(speaker)
				|| !ready(cooldownKey, VillagerNewsSettings.scaleCooldown(cooldown))) return false;
		List<Integer> recentVariants = SHARED_RECENT_VARIANTS.getOrDefault(group.id(), List.of());
		DialogueCatalog.DialogueVariant variant = group.chooseVariant(VillagerNewsSettings.rareVoicelines(), Set.copyOf(recentVariants));
		if (variant == null) return false;
		DialogueAnimationNetwork.send(level, speaker, group.id(), variant.index(), (int) variant.durationTicks());
		ACTIVE_SOUNDS.put(speaker.getUUID(), new ActiveSound(group.id(), ticks + variant.durationTicks()));
		COOLDOWNS.put(cooldownKey, ticks);
		int maximumWeight = group.variants().stream().mapToInt(DialogueCatalog.DialogueVariant::weight).max().orElse(1);
		int eligibleVariants = VillagerNewsSettings.rareVoicelines() == 0
			? (int) group.variants().stream().filter(candidate -> candidate.weight() >= maximumWeight * 0.8).count()
			: group.variants().size();
		int historySize = Math.min(8, eligibleVariants - 1);
		if (historySize > 0) {
			List<Integer> updatedHistory = new ArrayList<>(recentVariants);
			updatedHistory.remove(Integer.valueOf(variant.index()));
			updatedHistory.add(variant.index());
			while (updatedHistory.size() > historySize) updatedHistory.removeFirst();
			SHARED_RECENT_VARIANTS.put(group.id(), updatedHistory);
		}
		markBusy(speaker, variant.durationTicks() + 10L);
		if (speaker instanceof Mob mob) {
			Vec3 position = target != null ? target.getEyePosition() : targetPosition;
			boolean lockMovement = !MOBILE_DIALOGUES.contains(group.id()) && !(speaker instanceof Villager villager
				&& cast(villager) == CastProfile.UNREACHABLE && group.id().equals("eltxge"));
			SPEECH_TARGETS.put(speaker.getUUID(), new SpeechTarget(target == null ? null : target.getUUID(), position,
				ticks + variant.durationTicks(), lockMovement));
			if (lockMovement) holdMob(mob, position);
			else faceMob(mob, position);
		}
		return true;
	}

	private static boolean matchesSpeaker(LivingEntity speaker, DialogueCatalog.DialogueGroup group) {
		if (speaker instanceof Villager villager) {
			if (villager.isBaby()) return BABY_DIALOGUES.contains(group.id());
			if (BABY_DIALOGUES.contains(group.id())) return false;
			if (COSMETIC_RECIPIENT_DIALOGUES.contains(group.id())) return true;
		}
		return group.speaker().equals(speakerType(speaker));
	}

	public static boolean requiresBabySpeaker(String groupId) {
		return BABY_DIALOGUES.contains(groupId);
	}

	public static long playTestDialogue(ServerLevel level, LivingEntity speaker, DialogueCatalog.DialogueGroup group,
			int variantIndex, Entity target) {
		interrupt(speaker);
		DialogueCatalog.DialogueVariant variant = group.variants().stream()
			.filter(candidate -> candidate.index() == variantIndex).findFirst().orElse(null);
		if (variant == null) return 0L;
		long duration = variant.durationTicks();
		DialogueAnimationNetwork.send(level, speaker, group.id(), variant.index(), (int) duration);
		ACTIVE_SOUNDS.put(speaker.getUUID(), new ActiveSound(group.id(), ticks + duration));
		markBusy(speaker, duration + 10L);
		if (speaker instanceof Mob mob) {
			Vec3 position = target == null ? null : target.getEyePosition();
			SPEECH_TARGETS.put(speaker.getUUID(), new SpeechTarget(target == null ? null : target.getUUID(), position,
				ticks + duration, true));
			holdMob(mob, position);
		}
		return duration;
	}

	public static void stopTestDialogue(LivingEntity speaker) {
		interrupt(speaker);
	}

	public static void onTradeCompleted(LivingEntity trader, Player player) {
		if (player != null) {
			TradeSession session = ACTIVE_TRADES.get(player.getUUID());
			if (session != null && session.traderId.equals(trader.getUUID())) session.completed = true;
		}
		String id = null;
		if (trader instanceof Villager villager && cast(villager) == CastProfile.VILLAGER) {
			id = "xmkwxd";
		} else if (trader instanceof WanderingTrader wanderingTrader) {
			id = "bvrbhy";
		}
		if (id != null && trader.level() instanceof ServerLevel level) {
			long due = Math.max(ticks + 1L, BUSY_UNTIL.getOrDefault(trader.getUUID(), ticks) + 1L);
			PENDING_SPEECH.add(new PendingSpeech(level, trader.getUUID(), id, player == null ? null : player.getUUID(), due));
		}
	}

	private static boolean ready(String key, long cooldown) {
		return ticks - COOLDOWNS.getOrDefault(key, Long.MIN_VALUE / 2) >= cooldown;
	}

	private static boolean isBusy(LivingEntity entity) {
		ActiveSound sound = ACTIVE_SOUNDS.get(entity.getUUID());
		return BUSY_UNTIL.getOrDefault(entity.getUUID(), 0L) > ticks || sound != null && sound.endTick > ticks;
	}

	private static boolean isPlaying(LivingEntity entity, String groupId) {
		ActiveSound sound = ACTIVE_SOUNDS.get(entity.getUUID());
		return sound != null && sound.endTick > ticks && sound.groupId.equals(groupId);
	}

	private static boolean hasDamageLock(LivingEntity entity) {
		ActiveSound sound = ACTIVE_SOUNDS.get(entity.getUUID());
		return sound != null && sound.endTick > ticks && DAMAGE_LOCK_DIALOGUES.contains(sound.groupId);
	}

	private static void markBusy(LivingEntity entity, long duration) {
		BUSY_UNTIL.put(entity.getUUID(), ticks + duration);
	}

	private static void holdListener(LivingEntity listener, Entity speaker, long duration) {
		markBusy(listener, duration);
		if (listener instanceof Mob mob) {
			Vec3 position = speaker.getEyePosition();
			SPEECH_TARGETS.put(listener.getUUID(), new SpeechTarget(speaker.getUUID(), position, ticks + duration, true));
			holdMob(mob, position);
		}
	}

	private static void holdMob(Mob mob, Vec3 position) {
		mob.getNavigation().stop();
		mob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		mob.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
		if (mob.onGround() && !mob.isPassenger()) {
			Vec3 movement = mob.getDeltaMovement();
			mob.setDeltaMovement(0.0, movement.y, 0.0);
		}
		faceMob(mob, position);
	}

	private static void faceMob(Mob mob, Vec3 position) {
		if (position == null) return;
		mob.getLookControl().setLookAt(position.x, position.y, position.z, 15.0F, 10.0F);
		double x = position.x - mob.getX();
		double y = position.y - mob.getEyeY();
		double z = position.z - mob.getZ();
		double horizontal = Math.sqrt(x * x + z * z);
		if (horizontal > 0.01) {
			float targetYaw = (float) (Mth.atan2(z, x) * 180.0 / Math.PI) - 90.0F;
			float headOffset = Mth.clamp(Mth.wrapDegrees(targetYaw - mob.yBodyRot), -65.0F, 65.0F);
			float bodyYaw = easedRotation(mob.yBodyRot, targetYaw - headOffset, 12.0F, 0.35F);
			float headYaw = easedRotation(mob.getYHeadRot(), targetYaw, 15.0F, 0.4F);
			mob.setYRot(bodyYaw);
			mob.setYBodyRot(bodyYaw);
			mob.setYHeadRot(headYaw);
		}
		float targetPitch = (float) (-(Mth.atan2(y, horizontal) * 180.0 / Math.PI));
		mob.setXRot(easedRotation(mob.getXRot(), Mth.clamp(targetPitch, -90.0F, 90.0F), 10.0F, 0.3F));
	}

	private static float easedRotation(float current, float target, float maximumStep, float proportion) {
		float distance = Math.abs(Mth.wrapDegrees(target - current));
		return Mth.approachDegrees(current, target, Mth.clamp(distance * proportion, 0.2F, maximumStep));
	}

	private static void interrupt(LivingEntity speaker) {
		if (!(speaker.level() instanceof ServerLevel level)) return;
		ACTIVE_SOUNDS.remove(speaker.getUUID());
		DialogueAnimationNetwork.stop(level, speaker);
		BUSY_UNTIL.remove(speaker.getUUID());
		SPEECH_TARGETS.remove(speaker.getUUID());
	}

	private static boolean isWooly(Sheep sheep) {
		String name = sheep.getName().getString().toLowerCase(Locale.ROOT);
		return name.equals("wooly") || name.equals("wooly the sheep");
	}

	private static void normalizeSpecialEntity(Entity entity) {
		Component customName = entity.getCustomName();
		if (customName == null) return;
		String name = customName.getString();
		String prefix = "{\"text\":\"";
		if (!name.startsWith(prefix) || !name.endsWith("\"}")) return;
		String decoded = name.substring(prefix.length(), name.length() - 2);
		if (SPECIAL_NAMES.contains(decoded)) entity.setCustomName(Component.literal(decoded));
	}

	private static VillagerNewsData data(Villager villager) {
		return (VillagerNewsData) villager;
	}

	private static void ensureSpecialTrade(Villager villager) {
		CastProfile profile = cast(villager);
		net.minecraft.world.item.Item result = switch (profile) {
			case MAYOR -> VillagerNewsItems.MAYOR_HAT;
			case TESTIFICATE_MAN -> VillagerNewsItems.TESTIFICATE_MAN_HELMET;
			case NUMBER_5 -> VillagerNewsItems.MOUSTACHE;
			case NUMBER_9 -> VillagerNewsItems.MICROPHONE;
			default -> null;
		};
		VillagerNewsData state = data(villager);
		if (result == null) {
			if (state.vnap$hasOriginalVillagerState()) state.vnap$restoreOriginalVillagerState();
			return;
		}
		state.vnap$captureOriginalVillagerState();
		if (!villager.getVillagerData().profession().is(VillagerProfession.NONE)) {
			villager.setVillagerData(villager.getVillagerData()
				.withProfession(villager.level().registryAccess(), VillagerProfession.NONE)
				.withLevel(1));
		}
		villager.getOffers().removeIf(offer -> offer.getResult().getItem() != result);
		if (villager.getOffers().stream().anyMatch(offer -> offer.getResult().getItem() == result)) return;
		int price = profile == CastProfile.MAYOR ? 24 : 16;
		villager.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD, price), new ItemStack(result), 16, 2, 0.1F));
	}

	public static boolean isSpecialTrader(Villager villager) {
		return switch (cast(villager)) {
			case MAYOR, TESTIFICATE_MAN, NUMBER_5, NUMBER_9 -> true;
			default -> false;
		};
	}

	private static boolean tryCreateNaturalSpecial(Villager villager, ServerLevel level) {
		if (!VillagerNewsSettings.spawnSpecialVillagers() || villager.spawnReason() != EntitySpawnReason.STRUCTURE) return false;
		BlockPos spawn = level.getRespawnData().pos();
		if (villager.distanceToSqr(Vec3.atCenterOf(spawn)) <= 1000.0 * 1000.0) return false;
		Scoreboard scoreboard = level.getServer().getScoreboard();
		Objective spawned = objective(scoreboard, SPECIAL_OBJECTIVE);
		Objective xPosition = objective(scoreboard, SPECIAL_X_OBJECTIVE);
		Objective zPosition = objective(scoreboard, SPECIAL_Z_OBJECTIVE);
		List<String> available = new ArrayList<>();
		for (String key : NATURAL_SPECIAL_NAMES.keySet()) {
			ScoreHolder holder = ScoreHolder.forNameOnly("$vnap_" + key);
			if (scoreboard.getOrCreatePlayerScore(holder, spawned).get() == 0) available.add(key);
			else {
				int x = scoreboard.getOrCreatePlayerScore(holder, xPosition).get();
				int z = scoreboard.getOrCreatePlayerScore(holder, zPosition).get();
				double dx = villager.getX() - x;
				double dz = villager.getZ() - z;
				if (dx * dx + dz * dz <= 150.0 * 150.0) return false;
			}
		}
		if (available.isEmpty()) return false;
		String key = available.get(ThreadLocalRandom.current().nextInt(available.size()));
		if (key.equals("wooly")) {
			Sheep sheep = EntityTypes.SHEEP.create(level, EntitySpawnReason.STRUCTURE);
			if (sheep == null) return false;
			sheep.copyPosition(villager);
			sheep.setCustomName(Component.literal(NATURAL_SPECIAL_NAMES.get(key)));
			sheep.setPersistenceRequired();
			sheep.setColor(DyeColor.WHITE);
			sheep.addTag(NATURAL_SPECIAL_TAG);
			if (!level.addFreshEntity(sheep)) return false;
			villager.discard();
		} else {
			villager.setCustomName(Component.literal(NATURAL_SPECIAL_NAMES.get(key)));
			villager.setPersistenceRequired();
			villager.addTag(NATURAL_SPECIAL_TAG);
		}
		ScoreHolder holder = ScoreHolder.forNameOnly("$vnap_" + key);
		scoreboard.getOrCreatePlayerScore(holder, spawned).set(1);
		scoreboard.getOrCreatePlayerScore(holder, xPosition).set(villager.blockPosition().getX());
		scoreboard.getOrCreatePlayerScore(holder, zPosition).set(villager.blockPosition().getZ());
		return key.equals("wooly");
	}

	private static void clearNaturalSpecial(ServerLevel level, Entity entity) {
		String key = naturalSpecialKey(entity);
		if (key == null) return;
		Scoreboard scoreboard = level.getServer().getScoreboard();
		ScoreHolder holder = ScoreHolder.forNameOnly("$vnap_" + key);
		scoreboard.getOrCreatePlayerScore(holder, objective(scoreboard, SPECIAL_OBJECTIVE)).set(0);
	}

	private static String naturalSpecialKey(Entity entity) {
		if (entity instanceof Sheep sheep && isWooly(sheep)) return "wooly";
		if (!(entity instanceof Villager villager)) return null;
		return switch (cast(villager)) {
			case MAYOR -> "mayor";
			case TESTIFICATE_MAN -> "testificate";
			case NUMBER_5 -> "number_5";
			case NUMBER_9 -> "number_9";
			case UNREACHABLE -> "unreachable";
			default -> null;
		};
	}

	private static Objective objective(Scoreboard scoreboard, String name) {
		Objective existing = scoreboard.getObjective(name);
		return existing == null ? scoreboard.addObjective(name, ObjectiveCriteria.DUMMY, Component.literal(name),
			ObjectiveCriteria.RenderType.INTEGER, false, null) : existing;
	}

	private static String orderedPair(UUID first, UUID second) {
		return first.compareTo(second) < 0 ? first + ":" + second : second + ":" + first;
	}

	private static String meetDialogue(CastProfile profile) {
		return switch (profile) {
			case MAYOR -> "lyatyf";
			case NUMBER_5 -> "kmvqxe";
			case NUMBER_9 -> "sifqsj";
			case TESTIFICATE_MAN -> "zvamyb";
			default -> null;
		};
	}

	private static CastProfile cast(Villager villager) {
		String name = villager.getName().getString().toLowerCase(Locale.ROOT);
		if (name.equals("mayor") || name.equals("the mayor") || name.equals("mayor villager")) return CastProfile.MAYOR;
		if (name.equals("testificate man")) return CastProfile.TESTIFICATE_MAN;
		if (name.equals("villager #5") || name.equals("villager number 5")) return CastProfile.NUMBER_5;
		if (name.equals("villager #9") || name.equals("villager number 9")) return CastProfile.NUMBER_9;
		if (name.equals("villager unreachable") || name.equals("can't catch me!")) return CastProfile.UNREACHABLE;
		return CastProfile.VILLAGER;
	}

	private static String speakerType(LivingEntity speaker) {
		if (speaker instanceof Villager villager) {
			return switch (cast(villager)) {
				case VILLAGER -> "villager";
				case MAYOR -> "mayor";
				case TESTIFICATE_MAN -> "testificate_man";
				case NUMBER_5 -> "number_5";
				case NUMBER_9 -> "number_9";
				case UNREACHABLE -> "unreachable";
			};
		}
		if (speaker instanceof WanderingTrader) return "wandering_trader";
		if (speaker instanceof Sheep sheep && isWooly(sheep)) return "wooly";
		return "";
	}

	private enum CastProfile {
		VILLAGER("xfpjxq", "lvigit", "clbjww", "wyvzhk", "vevdkl"),
		MAYOR("dpwhhs", "xxehbq", "njyapy", "ssbhiv", "ltdnvy"),
		TESTIFICATE_MAN("nmwmrz", "luoibc", "mpbnsm", "fzoqwd", "fzoqwd"),
		NUMBER_5("xccwah", "legnsy", "sclaoa", "behifz", "behifz"),
		NUMBER_9("kzogzi", "ezgbfw", "snnkrl", "wrbvvp", "asuufu"),
		UNREACHABLE("eltxge", "eltxge", "eltxge", "wyvzhk", "vevdkl");

		private final String approach;
		private final String idle;
		private final String trade;
		private final String hurt;
		private final String attack;

		CastProfile(String approach, String idle, String trade, String hurt, String attack) {
			this.approach = approach;
			this.idle = idle;
			this.trade = trade;
			this.hurt = hurt;
			this.attack = attack;
		}
	}

	private static final class PlayerObservation {
		private Vec3 lastPosition = Vec3.ZERO;
		private int stillTicks;
		private int stareTicks;
		private int breakStreak;
		private long lastBreakTick = Long.MIN_VALUE / 2;
		private String lastGameMode;
		private String lastPlayerContext;
		private BlockPos lastGroundPos = BlockPos.ZERO;
		private String lastGroundBlock = "";
	}

	private record VillagerSnapshot(boolean baby, String profession, int level, boolean working, String name,
			boolean poisoned, boolean slowed, boolean weakened, boolean suffocating) {
	}

	private record SpeechTarget(UUID targetId, Vec3 position, long untilTick, boolean lockMovement) {
	}

	private record PendingConditionRelief(ServerLevel level, UUID villagerId, String dialogueId, long dueTick) {
	}

	private record PendingWake(ServerLevel level, UUID villagerId, String dialogueId, UUID targetId, long dueTick) {
	}

	private record PendingBell(ServerLevel level, Vec3 position, long dueTick) {
	}

	private record PendingBellReaction(ServerLevel level, UUID villagerId, Vec3 position, long dueTick, long expireTick) {
	}
	private record PendingSpeech(ServerLevel level, UUID speakerId, String dialogueId, UUID targetId, long dueTick,
			boolean sharedAdult) {
		private PendingSpeech(ServerLevel level, UUID speakerId, String dialogueId, UUID targetId, long dueTick) {
			this(level, speakerId, dialogueId, targetId, dueTick, false);
		}
	}

	private static final class PendingSleep {
		private final ServerLevel level;
		private final BlockPos bedPos;
		private long dueTick;
		private boolean bedtimeStarted;

		private PendingSleep(ServerLevel level, BlockPos bedPos, long dueTick, boolean bedtimeStarted) {
			this.level = level;
			this.bedPos = bedPos;
			this.dueTick = dueTick;
			this.bedtimeStarted = bedtimeStarted;
		}
	}

	private record ActiveSound(String groupId, long endTick) {
	}

	private static final class TradeSession {
		private final ServerLevel level;
		private final UUID traderId;
		private final long createdTick;
		private boolean opened;
		private boolean completed;

		private TradeSession(ServerLevel level, UUID traderId, long createdTick) {
			this.level = level;
			this.traderId = traderId;
			this.createdTick = createdTick;
		}
	}

	private static final class UnreachableState {
		private long stateEnteredTick;
		private long nextPathTick;
		private boolean coolingDown;

		private UnreachableState(long tick) {
			this.stateEnteredTick = tick;
			this.nextPathTick = tick;
		}

		private void updateFleeing(long tick) {
			if (coolingDown && tick - stateEnteredTick > 240L) {
				coolingDown = false;
				stateEnteredTick = tick;
			}
		}

		private void stopFleeing(long tick) {
			if (coolingDown) {
				coolingDown = false;
				stateEnteredTick = tick;
			}
		}

		private boolean canTaunt(long tick) {
			return !coolingDown && tick - stateEnteredTick > 120L;
		}

		private void taunted(long tick) {
			coolingDown = true;
			stateEnteredTick = tick;
		}
	}
}
