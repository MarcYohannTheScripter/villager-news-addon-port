package com.vnap.dialogue;

import com.vnap.config.VillagerNewsSettings;
import com.vnap.entity.VillagerNewsData;
import com.vnap.item.VillagerNewsItems;
import com.vnap.network.DialogueAnimationNetwork;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
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
	private static final double OBSERVER_RANGE = 16.0;
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
	private static final Map<UUID, Map<String, Integer>> VILLAGER_INVENTORIES = new HashMap<>();
	private static final Map<UUID, SpeechTarget> SPEECH_TARGETS = new HashMap<>();
	private static final Map<UUID, Long> NO_WORKSTATION_SINCE = new HashMap<>();
	private static final Map<UUID, Long> LAST_DANGER = new HashMap<>();
	private static final Map<UUID, Long> NO_BELL_SINCE = new HashMap<>();
	private static final Map<UUID, TradeSession> ACTIVE_TRADES = new HashMap<>();
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
		Map.entry("allay", "rnlher"), Map.entry("bat", "ozmthf"), Map.entry("bee", "rbkjsr"),
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
			BUSY_UNTIL.remove(id);
			ACTIVE_SOUNDS.remove(id);
			SPEECH_TARGETS.remove(id);
			LAST_SLEEPING.remove(id);
			LAST_TRADER_INVISIBLE.remove(id);
			VILLAGER_STATES.remove(id);
			VILLAGER_INVENTORIES.remove(id);
			NO_WORKSTATION_SINCE.remove(id);
			LAST_DANGER.remove(id);
			NO_BELL_SINCE.remove(id);
		});

		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (level instanceof ServerLevel serverLevel) {
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
				String title = selectHeldBlockContext(held, clicked);
				if (title == null && held.getItem() instanceof BlockItem blockItem) {
					title = selectPlaceContext(blockItem.getBlock(), serverLevel, hitResult.getBlockPos());
				} else if (title == null) {
					title = selectUseBlockContext(clicked);
				}
				String clickedPath = BuiltInRegistries.BLOCK.getKey(clicked.getBlock()).getPath();
				if (clickedPath.endsWith("_door") && clicked.hasProperty(BlockStateProperties.OPEN)
						&& clicked.getValue(BlockStateProperties.OPEN)
						&& !nearbyVillagers(serverLevel, hitResult.getLocation(), 3.0).isEmpty()) title = "Close a Door in a Villager's Face";
				if (clickedPath.contains("chest") && nearBlock(serverLevel, hitResult.getBlockPos(), "bed", 6)) {
					title = "Open a Chest in a Villager's House";
				}
				String heldPath = BuiltInRegistries.ITEM.getKey(held.getItem()).getPath();
				if ((heldPath.equals("pumpkin") || heldPath.equals("carved_pumpkin"))
						&& nearBlock(serverLevel, hitResult.getBlockPos(), "iron_block", 3)) title = "Build an Iron Golem Frame";
				boolean played = title != null && playObserved(serverLevel, player, hitResult.getLocation(), title, SHORT_COOLDOWN);
				if (!played && BuiltInRegistries.BLOCK.getKey(clicked.getBlock()).getPath().equals("bell")) {
					nearbyVillagers(serverLevel, hitResult.getLocation(), OBSERVER_RANGE).stream().filter(Villager::isBaby)
						.min(Comparator.comparingDouble(villager -> villager.distanceToSqr(hitResult.getLocation())))
						.ifPresent(baby -> playId(baby, "nxalcz", "baby_bell:" + baby.getUUID(), SHORT_COOLDOWN, player));
				}
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
			if (level instanceof ServerLevel) onAttackEntity(player, entity);
			return InteractionResult.PASS;
		});

		EntitySleepEvents.STOP_SLEEPING.register((entity, sleepingPos) -> {
			if (entity instanceof ServerPlayer player) {
				boolean armored = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)
					.stream().anyMatch(slot -> !player.getItemBySlot(slot).isEmpty());
				playObserved(player.level(), player, player.position(), armored ? "Wake Up in Armor" : "Player Wakes Up", LONG_COOLDOWN);
			}
		});

		ServerLivingEntityEvents.AFTER_DAMAGE.register(ContextualDialogueController::onDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(ContextualDialogueController::onDeath);
	}

	private static void onEntityLoad(Entity entity, ServerLevel level) {
		if (!(entity instanceof Villager villager)) return;
		if (tryCreateNaturalSpecial(villager, level)) return;
		ensureSpecialTrade(villager);
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
				PENDING_SPEECH.add(new PendingSpeech(level, parent.getUUID(), "fbuabj", villager.getUUID(), ticks + 2L));
				PENDING_SPEECH.add(new PendingSpeech(level, villager.getUUID(), "lgjtnf", parent.getUUID(),
					ticks + DialogueCatalog.byId("fbuabj").durationTicks() + 4L));
			} else PENDING_SPEECH.add(new PendingSpeech(level, villager.getUUID(), "lgjtnf", null, ticks + 2L));
		} else if (reason == EntitySpawnReason.CONVERSION) {
			PENDING_SPEECH.add(new PendingSpeech(level, villager.getUUID(), villager.isBaby() ? "ggitzq" : "ivumgm", null, ticks + 2L));
		}
	}

	private static void processPendingSpeech() {
		PENDING_SPEECH.removeIf(pending -> {
			if (pending.dueTick > ticks) return false;
			Entity speaker = pending.level.getEntity(pending.speakerId);
			Entity target = pending.targetId == null ? null : pending.level.getEntity(pending.targetId);
			if (speaker instanceof LivingEntity living && living.isAlive()) {
				playId(living, pending.dialogueId, "queued:" + living.getUUID() + ":" + pending.dialogueId, 1L, target);
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
				if (target != null && target.isAlive()) mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
				else mob.getLookControl().setLookAt(speech.position.x, speech.position.y, speech.position.z, 30.0F, 30.0F);
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
		if (reputation <= -100) return "xduuwm";
		if (reputation <= -15) return "qmdvft";
		if (reputation >= 100) return "vlrsrn";
		if (reputation >= 15) return "kuhvdv";
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
			.min(Comparator.comparingDouble(villager -> villager.distanceToSqr(player))).orElse(null);
		if (speaker == null) return;
		boolean wasDay = Math.floorMod(before, 24000L) < 12000L;
		boolean isDay = Math.floorMod(now, 24000L) < 12000L;
		String id = wasDay == isDay ? (speaker.isBaby() ? "durjjd" : "uqwdqn")
			: isDay ? (speaker.isBaby() ? "wkwcrf" : "mgmzeh")
			: (speaker.isBaby() ? "msemoe" : "ohdwnz");
		playId(speaker, id, "time_skip:" + key, SHORT_COOLDOWN, player);
	}

	private static void processDifficultyChange(ServerLevel level) {
		String key = level.dimension().identifier().toString();
		Difficulty difficulty = level.getDifficulty();
		Difficulty previous = LAST_DIFFICULTY.put(key, difficulty);
		if (previous == null || previous == difficulty || level.players().isEmpty()) return;
		ServerPlayer player = level.players().getFirst();
		Villager speaker = nearbyVillagers(level, player.position(), 32.0).stream()
			.min(Comparator.comparingDouble(villager -> villager.distanceToSqr(player))).orElse(null);
		if (speaker == null) return;
		String id = difficulty == Difficulty.HARD ? "arzojk" : difficulty == Difficulty.PEACEFUL ? "xuyypm" : "ibcrvx";
		playId(speaker, id, "difficulty:" + key + ":" + difficulty.name(), SHORT_COOLDOWN, player);
	}

	private static void tick(MinecraftServer server) {
		if (!server.tickRateManager().runsNormally()) {
			stopActiveDialogue(server);
			return;
		}
		ticks++;
		ACTIVE_SOUNDS.entrySet().removeIf(entry -> entry.getValue().endTick <= ticks);
		maintainSpeechTargets(server);
		processPendingSpeech();
		processTradeSessions(server);
		if (ticks % 10L != 0L) return;

		for (ServerLevel level : server.getAllLevels()) {
			processTimeChange(level);
			processDifficultyChange(level);
			for (ServerPlayer player : level.players()) {
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
		Sheep wooly = level.getEntitiesOfClass(Sheep.class, area, sheep -> sheep.isAlive() && isWooly(sheep)).stream()
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

		List<Villager> nearby = nearbyVillagers(level, player.position(), OBSERVER_RANGE);
		Villager adult = nearby.stream()
			.filter(villager -> !villager.isBaby())
			.filter(villager -> villager.hasLineOfSight(player))
			.min(Comparator.comparingDouble(villager -> villager.distanceToSqr(player)))
			.orElse(null);
		if (adult == null) {
			Villager baby = nearby.stream().filter(Villager::isBaby).filter(villager -> villager.hasLineOfSight(player))
				.min(Comparator.comparingDouble(villager -> villager.distanceToSqr(player))).orElse(null);
			if (baby != null) {
				if (ticks % 40L == 0L && playNearbyEntityContext(level, baby)) return;
				String id = player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE) ? "fzyrfm"
					: baby.getPlayerReputation(player) < -15 ? "jfuftm" : "wtuguc";
				playId(baby, id, "baby_meet:" + baby.getUUID() + ":" + player.getUUID() + ":" + id, LONG_COOLDOWN, player);
			}
			return;
		}

		String pair = player.getUUID() + ":" + adult.getUUID();
		if (playCosmeticObservation(player, adult)) return;
		if (player.getBoundingBox().inflate(0.15).intersects(adult.getBoundingBox()) && movement.horizontalDistanceSqr() > 0.002) {
			String id = adult.getVehicle() != null && BuiltInRegistries.ENTITY_TYPE.getKey(adult.getVehicle().getType()).getPath().contains("boat")
				? "zvbnea" : "ajexrq";
			if (playId(adult, id, "nudge:" + adult.getUUID(), SHORT_COOLDOWN, player)) return;
		}
		if (changedGameMode && playId(adult, gameMode.equals("creative") ? "ohtblt" : "fhhqxg",
				"gamemode:" + player.getUUID() + ":" + gameMode, SHORT_COOLDOWN, player)) return;
		if (playId(adult, cast(adult).approach, "approach:" + pair, LONG_COOLDOWN, player)) {
			return;
		}

		Vec3 toVillager = adult.getEyePosition().subtract(player.getEyePosition()).normalize();
		double lookDot = player.getLookAngle().dot(toVillager);
		if (lookDot > 0.985) observation.stareTicks += 10;
		else observation.stareTicks = 0;

		if (observation.stareTicks >= 60 && playTitle(adult, "Stare at a Villager", "stare:" + pair, LONG_COOLDOWN, player)) {
			observation.stareTicks = 0;
			return;
		}
		if (observation.stillTicks >= 140 && playTitle(adult, "Stand Completely Still", "still:" + player.getUUID(), LONG_COOLDOWN, player)) {
			observation.stillTicks = 0;
			return;
		}

		String playerContext = playerContext(player, adult);
		if (playerContext != null && playTitle(adult, playerContext, "player:" + pair + ":" + playerContext, LONG_COOLDOWN, player)) return;
		long nearbyPlayers = level.players().stream().filter(other -> other.distanceToSqr(adult) <= 64.0).count();
		if (nearbyPlayers >= 2 && playId(adult, "cstyvg", "player_crowd:" + adult.getUUID(), LONG_COOLDOWN, player)) return;

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
		if (BuiltInRegistries.BLOCK.getKey(below.getBlock()).getPath().endsWith("_bed")) return "Stand on a Villager's Bed";
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
		int reputation = villager.getPlayerReputation(player);
		if (player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)) return "Hero of the Village";
		if (reputation <= -100 && BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).getPath().endsWith("_sword")) {
			return "Extremely Low Reputation with a Sword";
		}
		if (reputation <= -100) return "Extremely Low Reputation";
		if (reputation <= -15) return "Low Reputation";
		if (reputation >= 100) return "Extremely High Reputation";
		if (reputation >= 15) return "High Reputation";
		if (player.isFallFlying()) return "Glide with Elytra";
		if (player.getAbilities().flying) return "Fly in Creative Mode";
		if (player.isShiftKeyDown() && player.getDeltaMovement().horizontalDistanceSqr() > 0.002) return "Crouch-Walk";
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

	private static boolean playCosmeticObservation(ServerPlayer player, Villager villager) {
		ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
		ItemStack held = player.getMainHandItem();
		CastProfile profile = cast(villager);
		String id = null;
		if (head.getItem() == VillagerNewsItems.VILLAGER_NOSE && profile == CastProfile.VILLAGER) id = "kejscw";
		else if (head.getItem() == VillagerNewsItems.MAYOR_HAT && profile == CastProfile.VILLAGER) id = "cmkesu";
		else if (head.getItem() == VillagerNewsItems.TESTIFICATE_MAN_HELMET && profile == CastProfile.TESTIFICATE_MAN) id = "rooiup";
		else if (head.getItem() == VillagerNewsItems.MOUSTACHE && profile == CastProfile.NUMBER_5) id = "mjyhgw";
		else if (held.getItem() == VillagerNewsItems.MICROPHONE && profile == CastProfile.NUMBER_9) id = "adhvqz";
		return id != null && playId(villager, id,
			"player_cosmetic:" + villager.getUUID() + ":" + player.getUUID() + ":" + id, LONG_COOLDOWN, player);
	}

	private static void processConversations(ServerLevel level) {
		for (ServerPlayer player : level.players()) {
			for (Villager villager : nearbyVillagers(level, player.position(), 24)) processVillagerState(villager);
			List<Villager> villagers = nearbyVillagers(level, player.position(), 24).stream().filter(villager -> !villager.isBaby()).toList();
			for (Villager subject : villagers) {
				if (data(subject).vnap$cosmetic() == 0) continue;
				Villager witness = villagers.stream().filter(other -> other != subject && other.hasLineOfSight(subject))
					.min(Comparator.comparingDouble(other -> other.distanceToSqr(subject))).orElse(null);
				if (witness != null) {
					String id = cast(witness) == CastProfile.TESTIFICATE_MAN && data(subject).vnap$cosmetic() == 2 ? "pbbywc" : "anrhns";
					if (playId(witness, id, "cosmetic_witness:" + witness.getUUID() + ":" + subject.getUUID() + ":" + id,
						LONG_COOLDOWN, subject)) return;
				}
			}
			if (villagers.size() >= 8 && playId(villagers.getFirst(), "kzemrz", "villager_crowd:" + player.getUUID(), LONG_COOLDOWN, player)) return;
			if (villagers.size() >= 3 && playId(villagers.getFirst(), "ebfifz", "gathering:" + player.getUUID(), LONG_COOLDOWN, villagers.get(1))) return;
			for (int firstIndex = 0; firstIndex < villagers.size(); firstIndex++) {
				Villager first = villagers.get(firstIndex);
				for (int secondIndex = firstIndex + 1; secondIndex < villagers.size(); secondIndex++) {
					Villager second = villagers.get(secondIndex);
					if (first.distanceToSqr(second) > 25.0 || isBusy(first) || isBusy(second)) continue;
					String pair = orderedPair(first.getUUID(), second.getUUID());
					int togetherTicks = PAIR_TICKS.merge(pair, 100, Integer::sum);
					if (togetherTicks < 200) continue;
					boolean firstHasNose = data(first).vnap$hasNose();
					boolean secondHasNose = data(second).vnap$hasNose();
					if (!firstHasNose || !secondHasNose) {
						List<List<String>> choices = !firstHasNose && !secondHasNose
							? TWO_MISSING_NOSES_CONVERSATIONS : ONE_MISSING_NOSE_CONVERSATIONS;
						List<String> sequence = choices.get(Math.floorMod(pair.hashCode() + (int) (ticks / LONG_COOLDOWN), choices.size()));
						if (playId(first, sequence.getFirst(), "nose_conversation:" + pair + ":" + sequence.getFirst(), LONG_COOLDOWN, second)) {
							markBusy(second, DialogueCatalog.byId(sequence.getFirst()).durationTicks());
							queueConversation(level, first, second, sequence);
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
							markBusy(subject, 80L);
							PAIR_TICKS.put(pair, 0);
						}
						return;
					}
					boolean atCampfire = nearBlock(level, first.blockPosition(), "campfire", 4)
						&& nearBlock(level, second.blockPosition(), "campfire", 4);
					String conversation = atCampfire ? CAMPFIRE_CONVERSATION.getFirst()
						: first.getVehicle() != null && first.getVehicle() == second.getVehicle()
						? "zqfvby"
						: first.getDeltaMovement().horizontalDistanceSqr() + second.getDeltaMovement().horizontalDistanceSqr() < 0.0004
							? (Math.floorMod(pair.hashCode() + (int) (ticks / LONG_COOLDOWN), 2) == 0 ? "wrjbdd" : GOSSIP_CONVERSATION.getFirst())
							: wanderingConversation(pair).getFirst();
					if (playId(first, conversation, "conversation:" + pair + ":" + conversation, LONG_COOLDOWN, second)) {
						markBusy(second, DialogueCatalog.byId(conversation).durationTicks());
						if (conversation.startsWith("gmrypk")) queueWanderingConversation(level, first, second, wanderingConversation(pair));
						else if (atCampfire) queueConversation(level, first, second, CAMPFIRE_CONVERSATION);
						else if (conversation.equals(GOSSIP_CONVERSATION.getFirst())) queueConversation(level, first, second, GOSSIP_CONVERSATION);
						PAIR_TICKS.put(pair, 0);
						return;
					}
				}
			}

			Villager adult = villagers.stream().findFirst().orElse(null);
			List<Villager> babies = nearbyVillagers(level, player.position(), 24).stream().filter(Villager::isBaby).toList();
			Villager baby = babies.stream().findFirst().orElse(null);
			if (babies.size() >= 2 && babies.get(0).distanceToSqr(babies.get(1)) <= 64.0
					&& babies.get(0).getDeltaMovement().horizontalDistanceSqr() + babies.get(1).getDeltaMovement().horizontalDistanceSqr() > 0.01) {
				playId(babies.get(0), "rfnirh", "baby_chase:" + orderedPair(babies.get(0).getUUID(), babies.get(1).getUUID()), LONG_COOLDOWN, babies.get(1));
			}
			if (adult != null && baby != null && adult.distanceToSqr(baby) <= 64.0) {
				playId(adult, "pbmrxx", "see_baby:" + adult.getUUID() + ":" + baby.getUUID(), LONG_COOLDOWN, baby);
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

	private static boolean nearBlock(ServerLevel level, BlockPos origin, String pathPart, int range) {
		for (int x = -range; x <= range; x++) for (int y = -2; y <= 2; y++) for (int z = -range; z <= range; z++) {
			String path = BuiltInRegistries.BLOCK.getKey(level.getBlockState(origin.offset(x, y, z)).getBlock()).getPath();
			if (path.contains(pathPart)) return true;
		}
		return false;
	}

	private static void processVillagerState(Villager villager) {
		ensureSpecialTrade(villager);
		VillagerSnapshot previous = VILLAGER_STATES.get(villager.getUUID());
		BlockPos workstation = findWorkstation(villager);
		VillagerSnapshot current = snapshot(villager, workstation != null);
		Map<String, Integer> oldInventory = VILLAGER_INVENTORIES.get(villager.getUUID());
		ItemStack pickedUp = findNewItem(villager, oldInventory);
		if (pickedUp != null) {
			String path = BuiltInRegistries.ITEM.getKey(pickedUp.getItem()).getPath();
			boolean armor = path.endsWith("_helmet") || path.endsWith("_chestplate") || path.endsWith("_leggings") || path.endsWith("_boots");
			String id = armor ? (pickedUp.isEnchanted() ? "habfnx" : "zjwpzi") : "dxmmiu";
			boolean food = path.equals("beetroot") || path.equals("bread") || path.equals("carrot") || path.equals("potato");
			Villager donor = villager.level() instanceof ServerLevel level ? nearbyVillagers(level, villager.position(), 4.0).stream()
				.filter(other -> other != villager).min(Comparator.comparingDouble(other -> other.distanceToSqr(villager))).orElse(null) : null;
			ServerPlayer nearbyPlayer = villager.level() instanceof ServerLevel level ? nearestPlayer(level, villager.position(), 6.0) : null;
			if (donor != null && food && playId(donor, "locuih", "share_food:" + donor.getUUID(), SHORT_COOLDOWN, villager)) {
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
			} else if (current.poisoned && !previous.poisoned) {
				playId(villager, "onindz", "effect:poison:" + villager.getUUID(), SHORT_COOLDOWN);
			} else if (current.slowed && !previous.slowed) {
				playId(villager, "xemyaj", "effect:slowness:" + villager.getUUID(), SHORT_COOLDOWN);
			} else if (current.weakened && !previous.weakened) {
				playId(villager, "yebifs", "effect:weakness:" + villager.getUUID(), SHORT_COOLDOWN);
			} else if (previous.suffocating && !current.suffocating) {
				interrupt(villager);
				playId(villager, "fxbysi", "freed:" + villager.getUUID(), SHORT_COOLDOWN);
			}
			if (!previous.working && current.working) {
				playId(villager, "qawras", "work_start:" + villager.getUUID(), LONG_COOLDOWN, workstation == null ? null : Vec3.atCenterOf(workstation));
			} else if (current.working) {
				String work = ticks / LONG_COOLDOWN % 3L == 0L ? "sdhkke" : professionWorkDialogue(current.profession);
				if (work != null) playId(villager, work, "work:" + villager.getUUID() + ":" + work, LONG_COOLDOWN,
					workstation == null ? null : Vec3.atCenterOf(workstation));
			}
		}
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
		boolean sleeping = villager.isSleeping();
		boolean wasSleeping = LAST_SLEEPING.getOrDefault(villager.getUUID(), sleeping);
		if (sleeping && !wasSleeping) playId(villager, "ioxtmt", "bed:" + villager.getUUID(), LONG_COOLDOWN);
		else if (sleeping) playTitle(villager, "Sleeping", "sleeping:" + villager.getUUID(), LONG_COOLDOWN);
		else if (wasSleeping) playTitle(villager, "Wake Up Naturally", "wake:" + villager.getUUID(), LONG_COOLDOWN);
		if (villager.isBaby() && villager.getDeltaMovement().horizontalDistanceSqr() > 0.02) {
			boolean weekend = LocalDate.now().getDayOfWeek() == DayOfWeek.SATURDAY || LocalDate.now().getDayOfWeek() == DayOfWeek.SUNDAY;
			playId(villager, weekend ? "vbclem" : "vhwksn", "baby_sprint:" + villager.getUUID(), LONG_COOLDOWN);
		}
		LAST_SLEEPING.put(villager.getUUID(), sleeping);
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
		AABB area = AABB.ofSize(speaker.position(), 20, 12, 20);
		for (Entity entity : level.getEntities(speaker, area, entity -> entity.isAlive())) {
			String path = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
			String id = switch (path) {
				case "falling_block" -> "bodvsv";
				case "experience_orb" -> "cvltyw";
				case "tnt" -> "pmaqgq";
				case "firework_rocket" -> "zeykfp";
				default -> null;
			};
			if (id != null && speaker.hasLineOfSight(entity)
					&& playId(speaker, id, "nearby_misc:" + speaker.getUUID() + ":" + entity.getUUID() + ":" + id, LONG_COOLDOWN, entity)) {
				return true;
			}
		}
		List<ItemEntity> droppedItems = level.getEntitiesOfClass(ItemEntity.class,
			AABB.ofSize(speaker.position(), 10, 10, 10), Entity::isAlive);
		if (droppedItems.size() >= 5 && playId(speaker, "zywcju", "item_pile:" + speaker.getUUID(), LONG_COOLDOWN,
				droppedItems.getFirst())) return true;
		return level.getEntitiesOfClass(LivingEntity.class, area, entity -> entity.isAlive()
				&& entity != speaker && !(entity instanceof Player) && !(entity instanceof Villager))
			.stream()
			.sorted(Comparator.comparingDouble(speaker::distanceToSqr))
			.anyMatch(entity -> {
				String path = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
				String dialogue = speaker.isBaby() && path.equals("iron_golem") ? "mqnapy" : null;
				if (dialogue == null && entity instanceof Sheep sheep && sheep.isSheared()) dialogue = "afxbav";
				if (dialogue == null && entity instanceof net.minecraft.world.entity.TamableAnimal tame && tame.isTame()) {
					dialogue = entity.isBaby() ? "sxikgq" : "aqxgxh";
				}
				if (dialogue == null && entity instanceof Mob mob && mob.getTarget() != null) {
					if (path.equals("bee")) dialogue = "bmimxe";
					else if (path.equals("iron_golem") && mob.getTarget() instanceof Player) dialogue = "qffeco";
				}
				if (dialogue == null && (entity.isPassenger() || !entity.getPassengers().isEmpty())) dialogue = "dxeaal";
				if (dialogue == null && entity.isBaby()) dialogue = BABY_ENTITY_DIALOGUES.get(path);
				if (dialogue == null) dialogue = NEARBY_ENTITY_DIALOGUES.get(path);
				if (dialogue == null && !(entity instanceof WanderingTrader) && !(entity instanceof Sheep sheep && isWooly(sheep))) {
					dialogue = "gjtuqd";
				}
				return dialogue != null && speaker.hasLineOfSight(entity)
					&& playId(speaker, dialogue, "nearby:" + speaker.getUUID() + ":" + entity.getUUID() + ":" + dialogue, LONG_COOLDOWN, entity);
			});
	}

	private static void onDamage(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source,
			float baseDamageTaken, float damageTaken, boolean blocked) {
		if (blocked || damageTaken <= 0.0F) return;
		if (entity instanceof Sheep sheep && isWooly(sheep)) {
			interrupt(sheep);
			playId(sheep, "eyiraw", "hurt:" + sheep.getUUID(), 1L, source.getEntity());
			return;
		}
		if (!(entity instanceof Villager villager)) {
			if (entity.level() instanceof ServerLevel level) {
				nearbyVillagers(level, entity.position(), OBSERVER_RANGE).stream()
					.filter(witness -> !witness.isBaby() && witness.hasLineOfSight(entity))
					.min(Comparator.comparingDouble(witness -> witness.distanceToSqr(entity)))
					.ifPresent(witness -> playId(witness, "pkvhpv", "witness_hurt:" + witness.getUUID(), SHORT_COOLDOWN, entity));
			}
			return;
		}
		LAST_DANGER.put(villager.getUUID(), ticks);
		interrupt(villager);
		if (villager.isBaby()) {
			playId(villager, "ecslqo", "hurt:" + villager.getUUID(), 1L, source.getEntity());
			return;
		}
		CastProfile profile = cast(villager);
		if (profile != CastProfile.VILLAGER) {
			playId(villager, profile.hurt, "hurt:" + villager.getUUID(), 1L, source.getEntity());
			return;
		}
		String dialogue = damageDialogue(source);
		if (playId(villager, dialogue, "hurt:" + villager.getUUID() + ":" + dialogue, 1L, source.getEntity())
				&& ready("panic:" + villager.getUUID(), SHORT_COOLDOWN) && villager.level() instanceof ServerLevel level) {
			COOLDOWNS.put("panic:" + villager.getUUID(), ticks);
			PENDING_SPEECH.add(new PendingSpeech(level, villager.getUUID(), "uzdxum",
				source.getEntity() == null ? null : source.getEntity().getUUID(), ticks + DialogueCatalog.byId(dialogue).durationTicks() + 2L));
		}
	}

	private static String damageDialogue(net.minecraft.world.damagesource.DamageSource source) {
		String attacker = source.getEntity() == null ? "" : BuiltInRegistries.ENTITY_TYPE.getKey(source.getEntity().getType()).getPath();
		String direct = source.getDirectEntity() == null ? "" : BuiltInRegistries.ENTITY_TYPE.getKey(source.getDirectEntity().getType()).getPath();
		String damage = source.getMsgId().toLowerCase(Locale.ROOT);
		if (direct.equals("arrow")) return "huhcbd";
		if (direct.equals("firework_rocket")) return "gesjov";
		if (direct.equals("potion") || direct.equals("lingering_potion")) return damage.contains("magic") ? "gacgtq" : "hmadgp";
		if (attacker.equals("evoker")) return "nkcoqb";
		if (attacker.equals("pillager")) return "qhpyaw";
		if (attacker.equals("ravager")) return "uveohs";
		if (attacker.equals("vex")) return "caykki";
		if (attacker.equals("vindicator")) return "swomdw";
		if (attacker.equals("witch")) return "rtikom";
		if (attacker.equals("zoglin")) return "hfmwvf";
		if (attacker.contains("zombie") || attacker.equals("drowned") || attacker.equals("husk")) return "mytmrk";
		if (damage.contains("lightning")) return "ikrwzy";
		if (damage.contains("explosion")) return "fcbygh";
		if (damage.contains("fire")) return "etkxko";
		if (damage.contains("lava")) return "elryje";
		if (damage.contains("cactus")) return "rogpvp";
		if (damage.contains("freeze")) return "igebly";
		if (damage.contains("wall")) return "vnaodx";
		if (damage.contains("falling") && damage.contains("anvil")) return "yzqpvi";
		if (damage.contains("stalagmite")) return "nsxmkr";
		if (damage.contains("fall")) return "cifbit";
		return "wyvzhk";
	}

	private static void onDeath(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source) {
		interrupt(entity);
		if (!(entity.level() instanceof ServerLevel level)) return;
		if (entity.entityTags().contains(NATURAL_SPECIAL_TAG)) clearNaturalSpecial(level, entity);
		if (entity instanceof Villager villager) {
			BUSY_UNTIL.remove(villager.getUUID());
			playId(villager, "hivgme", "death:" + villager.getUUID(), 1L);
			nearbyVillagers(level, villager.position(), OBSERVER_RANGE).stream()
				.filter(witness -> witness != villager && !witness.isBaby())
				.min(Comparator.comparingDouble(witness -> witness.distanceToSqr(villager)))
				.ifPresent(witness -> playId(witness, "pmqrpb", "witness_death:" + witness.getUUID(), SHORT_COOLDOWN, villager));
		} else if (entity instanceof ServerPlayer player) {
			int deaths = PLAYER_DEATHS.merge(player.getUUID(), 1, Integer::sum);
			String id = level.getLevelData().isHardcore() ? "elcjbb" : deaths > 1 ? "dxcjqn" : "hzjycq";
			nearbyVillagers(level, player.position(), OBSERVER_RANGE).stream()
				.filter(witness -> !witness.isBaby() && witness.hasLineOfSight(player))
				.min(Comparator.comparingDouble(witness -> witness.distanceToSqr(player)))
				.ifPresent(witness -> playId(witness, id, "player_death:" + witness.getUUID() + ":" + deaths, SHORT_COOLDOWN, player));
		}
	}

	private static InteractionResult onUseEntity(Player player, Entity entity, InteractionHand hand) {
		if (entity instanceof Villager villager) {
			ItemStack heldStack = player.getItemInHand(hand);
			String heldItem = BuiltInRegistries.ITEM.getKey(heldStack.getItem()).getPath();
			InteractionResult cosmeticResult = interactWithCosmetic(player, villager, heldStack);
			if (cosmeticResult != InteractionResult.PASS) return cosmeticResult;
			String gift = foodGiftDialogue(villager.isBaby(), heldItem);
			if (gift != null) {
				playId(villager, gift, "food_gift:" + villager.getUUID() + ":" + heldItem, SHORT_COOLDOWN, player);
				return InteractionResult.PASS;
			}
			if (heldItem.endsWith("_sign")) {
				playId(villager, "vqlrqf", "sign_gift:" + villager.getUUID(), SHORT_COOLDOWN, player);
				return InteractionResult.PASS;
			}
			if (villager.isSleeping()) {
				playId(villager, "viwaal", "wake_interact:" + villager.getUUID(), SHORT_COOLDOWN, player);
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
		String targetId = villager.isBaby() && cosmetic == 1 ? "svdjdk" : "orogba";
		playId(villager, targetId, "give_cosmetic:" + villager.getUUID() + ":" + cosmetic, 1L, player);
		CastProfile expected = switch (cosmetic) {
			case 2 -> CastProfile.TESTIFICATE_MAN;
			case 3 -> CastProfile.NUMBER_9;
			case 4 -> CastProfile.NUMBER_5;
			default -> null;
		};
		if (expected == null || !(villager.level() instanceof ServerLevel level)) return;
		Villager speaker = nearbyVillagers(level, villager.position(), OBSERVER_RANGE).stream()
			.filter(other -> other != villager && cast(other) == expected && other.hasLineOfSight(villager))
			.min(Comparator.comparingDouble(other -> other.distanceToSqr(villager))).orElse(null);
		if (speaker == null) return;
		String id = switch (cosmetic) {
			case 2 -> villager.isBaby() ? "cxeziv" : "wurmgu";
			case 3 -> villager.isBaby() ? "riezum" : "inirxg";
			case 4 -> villager.isBaby() ? "rlkdqd" : "ozxzla";
			default -> null;
		};
		long due = BUSY_UNTIL.getOrDefault(villager.getUUID(), ticks) + 2L;
		PENDING_SPEECH.add(new PendingSpeech(level, speaker.getUUID(), id, villager.getUUID(), due));
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
		if (entity instanceof Villager villager) {
			if (villager.isSleeping() && villager.level() instanceof ServerLevel level) {
				Villager witness = nearbyVillagers(level, villager.position(), 10.0).stream()
					.filter(other -> other != villager && !other.isBaby() && other.hasLineOfSight(villager))
					.min(Comparator.comparingDouble(other -> other.distanceToSqr(villager))).orElse(null);
				if (witness != null && playId(witness, "slbqfwswxeva", "home_witness:" + witness.getUUID(), 20L, villager)) {
					long due = ticks + DialogueCatalog.byId("slbqfwswxeva").durationTicks() + 2L;
					PENDING_SPEECH.add(new PendingSpeech(level, villager.getUUID(), "slbqfwbayahw", witness.getUUID(), due));
				} else playId(villager, "lpuocy", "attacked_home:" + villager.getUUID(), 20L, player);
				return;
			}
			String id;
			if (villager.isBaby()) id = "ahcvzd";
			else {
				CastProfile profile = cast(villager);
				id = profile.attack;
				if (profile == CastProfile.VILLAGER) id = weaponAttackDialogue(player.getMainHandItem());
			}
			playId(villager, id, "attack:" + villager.getUUID(), 20L, player);
		} else if (entity instanceof Sheep sheep && isWooly(sheep)) {
			playId(sheep, "ncyeaw", "attack:" + sheep.getUUID(), 20L, player);
		}
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
			.filter(villager -> !villager.isBaby())
			.filter(villager -> villager.hasLineOfSight(player))
			.min(Comparator.comparingDouble(villager -> villager.distanceToSqr(eventPosition)))
			.orElse(null);
		return speaker != null && playTitle(speaker, title, "observed:" + speaker.getUUID() + ":" + title, cooldown, player);
	}

	private static List<Villager> nearbyVillagers(ServerLevel level, Vec3 position, double range) {
		AABB area = AABB.ofSize(position, range * 2.0, range, range * 2.0);
		return level.getEntitiesOfClass(Villager.class, area, Entity::isAlive);
	}

	private static boolean playTitle(LivingEntity speaker, String title, String cooldownKey, long cooldown) {
		DialogueCatalog.DialogueGroup group = DialogueCatalog.byTitle(title, speakerType(speaker));
		return group != null && play(speaker, group, cooldownKey, cooldown, null, null);
	}

	private static boolean playTitle(LivingEntity speaker, String title, String cooldownKey, long cooldown, Entity target) {
		DialogueCatalog.DialogueGroup group = DialogueCatalog.byTitle(title, speakerType(speaker));
		return group != null && play(speaker, group, cooldownKey, cooldown, target, null);
	}

	private static boolean playId(LivingEntity speaker, String id, String cooldownKey, long cooldown) {
		DialogueCatalog.DialogueGroup group = DialogueCatalog.byId(id);
		return group != null && play(speaker, group, cooldownKey, cooldown, null, null);
	}

	private static boolean playId(LivingEntity speaker, String id, String cooldownKey, long cooldown, Entity target) {
		DialogueCatalog.DialogueGroup group = DialogueCatalog.byId(id);
		return group != null && play(speaker, group, cooldownKey, cooldown, target, null);
	}

	private static boolean playId(LivingEntity speaker, String id, String cooldownKey, long cooldown, Vec3 target) {
		DialogueCatalog.DialogueGroup group = DialogueCatalog.byId(id);
		return group != null && play(speaker, group, cooldownKey, cooldown, null, target);
	}

	private static boolean play(LivingEntity speaker, DialogueCatalog.DialogueGroup group, String cooldownKey, long cooldown,
			Entity target, Vec3 targetPosition) {
		if (!(speaker.level() instanceof ServerLevel level) || !group.speaker().equals(speakerType(speaker))
				|| !level.getServer().tickRateManager().runsNormally()
				|| !VillagerNewsSettings.dialogueEnabled() || isBusy(speaker)
				|| !ready(cooldownKey, VillagerNewsSettings.scaleCooldown(cooldown))) return false;
		DialogueCatalog.DialogueVariant variant = group.chooseVariant(VillagerNewsSettings.rareVoicelines());
		if (variant == null) return false;
		level.playSound(null, speaker.getX(), speaker.getY(), speaker.getZ(), variant.sound(), SoundSource.NEUTRAL, 1.0F, 1.0F);
		DialogueAnimationNetwork.send(level, speaker, group.id(), variant.index(), (int) variant.durationTicks());
		ACTIVE_SOUNDS.put(speaker.getUUID(), new ActiveSound(variant.sound().location(), ticks + variant.durationTicks()));
		COOLDOWNS.put(cooldownKey, ticks);
		markBusy(speaker, variant.durationTicks() + 10L);
		if (speaker instanceof Mob) {
			if (target != null) {
				SPEECH_TARGETS.put(speaker.getUUID(), new SpeechTarget(target.getUUID(), target.getEyePosition(), ticks + variant.durationTicks()));
			} else if (targetPosition != null) {
				SPEECH_TARGETS.put(speaker.getUUID(), new SpeechTarget(null, targetPosition, ticks + variant.durationTicks()));
			}
		}
		return true;
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
		return BUSY_UNTIL.getOrDefault(entity.getUUID(), 0L) > ticks;
	}

	private static void markBusy(LivingEntity entity, long duration) {
		BUSY_UNTIL.put(entity.getUUID(), ticks + duration);
	}

	private static void interrupt(LivingEntity speaker) {
		if (!(speaker.level() instanceof ServerLevel level)) return;
		ActiveSound sound = ACTIVE_SOUNDS.remove(speaker.getUUID());
		if (sound != null) {
			ClientboundStopSoundPacket packet = new ClientboundStopSoundPacket(sound.id(), SoundSource.NEUTRAL);
			for (ServerPlayer player : level.players()) {
				if (player.distanceToSqr(speaker) <= 96.0 * 96.0) player.connection.send(packet);
			}
		}
		DialogueAnimationNetwork.stop(level, speaker);
		BUSY_UNTIL.remove(speaker.getUUID());
		SPEECH_TARGETS.remove(speaker.getUUID());
	}

	private static boolean isWooly(Sheep sheep) {
		String name = sheep.getName().getString().toLowerCase(Locale.ROOT);
		return name.equals("wooly") || name.equals("wooly the sheep");
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
		if (result == null || villager.getOffers().stream().anyMatch(offer -> offer.getResult().getItem() == result)) return;
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
			sheep.setColor(DyeColor.RED);
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
		TESTIFICATE_MAN("nmwmrz", "luoibc", "mpbnsm", "fzoqwd", "vevdkl"),
		NUMBER_5("xccwah", "legnsy", "sclaoa", "behifz", "vevdkl"),
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
		private BlockPos lastGroundPos = BlockPos.ZERO;
		private String lastGroundBlock = "";
	}

	private record VillagerSnapshot(boolean baby, String profession, int level, boolean working, String name,
			boolean poisoned, boolean slowed, boolean weakened, boolean suffocating) {
	}

	private record SpeechTarget(UUID targetId, Vec3 position, long untilTick) {
	}

	private record PendingSpeech(ServerLevel level, UUID speakerId, String dialogueId, UUID targetId, long dueTick) {
	}

	private record ActiveSound(Identifier id, long endTick) {
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
}
