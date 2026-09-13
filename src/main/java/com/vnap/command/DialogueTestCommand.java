package com.vnap.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.vnap.dialogue.ContextualDialogueController;
import com.vnap.dialogue.DialogueCatalog;
import com.vnap.entity.VillagerNewsData;
import com.vnap.item.VillagerNewsItems;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DialogueTestCommand {
	private static final Map<UUID, TestSession> SESSIONS = new LinkedHashMap<>();
	private static final Map<UUID, TestRun> TEST_RUNS = new LinkedHashMap<>();
	private static final long CLIENT_TRACKING_DELAY = 5L;
	private static final long CONTINUOUS_GAP = 20L;
	private static final Map<String, String> SUBJECT_TYPES = Map.ofEntries(
		Map.entry("Allay", "allay"),
		Map.entry("Angry Bee", "bee"),
		Map.entry("Baby Bee", "bee"),
		Map.entry("Baby Cat", "cat"),
		Map.entry("Baby Chicken", "chicken"),
		Map.entry("Baby Cow", "cow"),
		Map.entry("Baby Drowned", "drowned"),
		Map.entry("Baby Horse", "horse"),
		Map.entry("Baby Husk", "husk"),
		Map.entry("Baby Panda", "panda"),
		Map.entry("Baby Pig", "pig"),
		Map.entry("Baby Sheep", "sheep"),
		Map.entry("Baby Wolf", "wolf"),
		Map.entry("Baby Zombie", "zombie"),
		Map.entry("Baby Zombie Piglin", "zombified_piglin"),
		Map.entry("Baby Zombie Villager", "zombie_villager"),
		Map.entry("Bat", "bat"),
		Map.entry("Bee", "bee"),
		Map.entry("Bogged", "bogged"),
		Map.entry("Camel", "camel"),
		Map.entry("Cat", "cat"),
		Map.entry("Chicken", "chicken"),
		Map.entry("Copper Golem", "copper_golem"),
		Map.entry("Cow", "cow"),
		Map.entry("Creaking", "creaking"),
		Map.entry("Creeper", "creeper"),
		Map.entry("Dolphin", "dolphin"),
		Map.entry("Drowned", "drowned"),
		Map.entry("Ender Dragon", "ender_dragon"),
		Map.entry("Enderman", "enderman"),
		Map.entry("Evoker", "evoker"),
		Map.entry("Fish", "cod"),
		Map.entry("Frog", "frog"),
		Map.entry("Happy Ghast", "happy_ghast"),
		Map.entry("Horse", "horse"),
		Map.entry("Husk", "husk"),
		Map.entry("Iron Golem", "iron_golem"),
		Map.entry("Jockey", "chicken"),
		Map.entry("Llama", "llama"),
		Map.entry("Panda", "panda"),
		Map.entry("Parrot", "parrot"),
		Map.entry("Phantom", "phantom"),
		Map.entry("Pig", "pig"),
		Map.entry("Pillager", "pillager"),
		Map.entry("Polar Bear", "polar_bear"),
		Map.entry("Rabbit", "rabbit"),
		Map.entry("Ravager", "ravager"),
		Map.entry("Sheared Sheep", "sheep"),
		Map.entry("Sheep", "sheep"),
		Map.entry("Skeleton", "skeleton"),
		Map.entry("Slime", "slime"),
		Map.entry("Sniffer", "sniffer"),
		Map.entry("Snow Golem", "snow_golem"),
		Map.entry("Spider", "spider"),
		Map.entry("Stray", "stray"),
		Map.entry("Sulfur Cube", "magma_cube"),
		Map.entry("Tamed Baby Wolf", "wolf"),
		Map.entry("Tamed Wolf", "wolf"),
		Map.entry("Turtle", "turtle"),
		Map.entry("Vex", "vex"),
		Map.entry("Vindicator", "vindicator"),
		Map.entry("Warden", "warden"),
		Map.entry("Witch", "witch"),
		Map.entry("Wither", "wither"),
		Map.entry("Wolf", "wolf"),
		Map.entry("Zoglin", "zoglin"),
		Map.entry("Zombie", "zombie"),
		Map.entry("Zombie Piglin", "zombified_piglin"),
		Map.entry("Zombie Villager", "zombie_villager"),
		Map.entry("See an Iron Golem", "iron_golem"),
		Map.entry("Uses a Potion with One Llama", "trader_llama"),
		Map.entry("Uses a Potion with Two Llamas", "trader_llama")
	);
	private static long ticks;

	private DialogueTestCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> dispatcher.register(
			Commands.literal("dialoguetest")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("continuous")
					.executes(context -> runContinuous(context.getSource())))
				.then(Commands.argument("group", IntegerArgumentType.integer(1, DialogueCatalog.groups().size()))
					.executes(context -> runSingle(context.getSource(), IntegerArgumentType.getInteger(context, "group"))))
		));
		ServerTickEvents.END_SERVER_TICK.register(DialogueTestCommand::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
	}

	private static int runSingle(CommandSourceStack source, int number) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		cancel(player.getUUID());
		TEST_RUNS.put(player.getUUID(), new TestRun(number, 0, ticks + 1L, false));
		return 1;
	}

	private static int runContinuous(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		cancel(player.getUUID());
		TEST_RUNS.put(player.getUUID(), new TestRun(1, 0, ticks + 1L, true));
		player.sendSystemMessage(Component.literal("Starting continuous dialogue test: "
			+ DialogueCatalog.groups().size() + " groups"));
		return 1;
	}

	private static boolean startTest(ServerPlayer player, int number, int variantOffset, boolean continuous) {
		List<DialogueCatalog.DialogueGroup> groups = new ArrayList<>(DialogueCatalog.groups().values());
		if (number < 1 || number > groups.size()) {
			player.sendSystemMessage(Component.literal("Dialogue group must be between 1 and " + groups.size()));
			return false;
		}
		removeSession(player.getUUID());
		DialogueCatalog.DialogueGroup group = groups.get(number - 1);
		if (variantOffset < 0 || variantOffset >= group.variants().size()) {
			player.sendSystemMessage(Component.literal("Dialogue group " + number + " has no variant " + (variantOffset + 1)));
			return false;
		}
		DialogueCatalog.DialogueVariant variant = group.variants().get(variantOffset);
		ServerLevel level = player.level();
		String title = scenarioTitle(group);
		Vec3 forward = horizontalDirection(player);
		Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
		Vec3 center = player.position().add(forward.scale(2.5));
		Vec3 speakerPosition = center.add(side.scale(0.8));
		Vec3 subjectPosition = center.subtract(side.scale(0.8));
		List<Entity> spawned = new ArrayList<>();
		LivingEntity speaker = createSpeaker(level, group);
		if (speaker == null) {
			announceFailure(player, number, groups.size(), group, title, "could not create speaker");
			return false;
		}
		prepareEntity(speaker, speakerPosition);
		configureSpeaker(level, speaker, group, title);
		if (!level.addFreshEntity(speaker)) {
			announceFailure(player, number, groups.size(), group, title, "could not summon speaker");
			return false;
		}
		spawned.add(speaker);
		SubjectSpec subjectSpec = subjectSpec(group, title);
		Entity subject = createSubject(level, player, group, title, subjectSpec, subjectPosition, spawned);
		if (subjectSpec != null && subject == null) {
			for (Entity entity : spawned) if (!entity.isRemoved()) entity.discard();
			announceFailure(player, number, groups.size(), group, title, "could not summon subject");
			return false;
		}
		configureScene(level, speaker, subject, group, title, center, spawned);
		SESSIONS.put(player.getUUID(), new TestSession(speaker, subject == null ? player : subject, spawned,
			group, variant, ticks + CLIENT_TRACKING_DELAY, number, variantOffset, continuous));
		player.sendSystemMessage(Component.literal("[Dialogue " + number + "/" + groups.size() + ", variant "
			+ (variantOffset + 1) + "/" + group.variants().size() + "] " + group.id() + " - " + title));
		return true;
	}

	private static void tick(MinecraftServer server) {
		ticks++;
		List<UUID> finished = new ArrayList<>();
		for (Map.Entry<UUID, TestSession> entry : SESSIONS.entrySet()) {
			TestSession session = entry.getValue();
			if (!session.speaker.isAlive()) {
				finished.add(entry.getKey());
				continue;
			}
			if (!session.started && ticks >= session.startTick) {
				session.started = true;
				long duration = ContextualDialogueController.playTestDialogue(
					(ServerLevel) session.speaker.level(), session.speaker, session.group(), session.variant.index(), session.subject);
				session.endTick = ticks + Math.max(1L, duration);
			}
			if (session.started && ticks >= session.endTick) finished.add(entry.getKey());
		}
		for (UUID owner : finished) {
			TestSession session = SESSIONS.get(owner);
			removeSession(owner);
			if (session != null) scheduleNext(server, owner, session.number, session.variantOffset, ticks + CONTINUOUS_GAP);
		}
		List<ScheduledTest> due = new ArrayList<>();
		for (Map.Entry<UUID, TestRun> entry : TEST_RUNS.entrySet()) {
			TestRun run = entry.getValue();
			if (!SESSIONS.containsKey(entry.getKey()) && ticks >= run.nextTick) {
				due.add(new ScheduledTest(entry.getKey(), run.number, run.variantOffset));
				run.nextTick = Long.MAX_VALUE;
			}
		}
		for (ScheduledTest scheduled : due) {
			ServerPlayer player = server.getPlayerList().getPlayer(scheduled.owner());
			if (player == null) {
				TEST_RUNS.remove(scheduled.owner());
				continue;
			}
			TestRun run = TEST_RUNS.get(scheduled.owner());
			if (run != null && !startTest(player, scheduled.number(), scheduled.variantOffset(), run.continuous)) {
				scheduleNext(server, scheduled.owner(), scheduled.number(), scheduled.variantOffset(), ticks + CONTINUOUS_GAP);
			}
		}
	}

	private static void scheduleNext(MinecraftServer server, UUID owner, int number, int variantOffset, long nextTick) {
		TestRun run = TEST_RUNS.get(owner);
		if (run == null) return;
		List<DialogueCatalog.DialogueGroup> groups = new ArrayList<>(DialogueCatalog.groups().values());
		DialogueCatalog.DialogueGroup group = groups.get(number - 1);
		if (variantOffset + 1 < group.variants().size()) {
			run.number = number;
			run.variantOffset = variantOffset + 1;
			run.nextTick = nextTick;
			return;
		}
		if (!run.continuous || number >= groups.size()) {
			TEST_RUNS.remove(owner);
			ServerPlayer player = server.getPlayerList().getPlayer(owner);
			if (player != null) {
				String message = run.continuous ? "Continuous dialogue test complete: " + groups.size() + "/" + groups.size()
					: "Dialogue test complete: group " + number + ", " + group.variants().size() + " variants";
				player.sendSystemMessage(Component.literal(message));
			}
			return;
		}
		run.number = number + 1;
		run.variantOffset = 0;
		run.nextTick = nextTick;
	}

	private static void announceFailure(ServerPlayer player, int number, int total,
			DialogueCatalog.DialogueGroup group, String title, String reason) {
		player.sendSystemMessage(Component.literal("[Dialogue " + number + "/" + total + "] "
			+ group.id() + " - " + title + " (failed: " + reason + ")"));
	}

	private static LivingEntity createSpeaker(ServerLevel level, DialogueCatalog.DialogueGroup group) {
		if (isCosmeticRecipientDialogue(group.id())) return EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
		return switch (group.speaker()) {
			case "wooly" -> EntityTypes.SHEEP.create(level, EntitySpawnReason.COMMAND);
			case "wandering_trader" -> EntityTypes.WANDERING_TRADER.create(level, EntitySpawnReason.COMMAND);
			default -> EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
		};
	}

	private static void configureSpeaker(ServerLevel level, LivingEntity speaker, DialogueCatalog.DialogueGroup group,
			String title) {
		if (speaker instanceof Villager villager) {
			String name = isCosmeticRecipientDialogue(group.id()) ? null : switch (group.speaker()) {
				case "mayor" -> "The Mayor";
				case "testificate_man" -> "Testificate Man";
				case "number_5" -> "Villager #5";
				case "number_9" -> "Villager #9";
				case "unreachable" -> "Villager Unreachable";
				default -> null;
			};
			if (name != null) villager.setCustomName(Component.literal(name));
			if (group.id().equals("qmpcxi")) villager.setCustomName(Component.literal("Dinnerbone"));
			if (group.id().equals("armupg")) villager.setCustomName(Component.literal("Jeb"));
			if (group.id().equals("cmrqhw")) villager.setCustomName(Component.literal("Dragon"));
			if (ContextualDialogueController.requiresBabySpeaker(group.id())) villager.setBaby(true);
			VillagerNewsData data = (VillagerNewsData) villager;
			data.vnap$setHasNose(!speakerHasNoNose(group.id(), title));
			data.vnap$setCosmetic(speakerCosmetic(group.id(), title));
			data.vnap$setSignType(group.id().equals("vqlrqf") ? 0 : -1);
			data.vnap$setSignMessage(group.id().equals("vqlrqf") ? 0 : -1);
			setProfession(level, villager, group.speaker().equals("villager") ? title : "");
			if (group.id().equals("adhvqz")) villager.setItemSlot(EquipmentSlot.MAINHAND,
				new ItemStack(VillagerNewsItems.MICROPHONE));
		}
		if (speaker instanceof Sheep sheep) {
			sheep.setCustomName(Component.literal("Wooly The Sheep"));
			sheep.setColor(DyeColor.WHITE);
			sheep.setSheared(group.id().equals("jqaekk"));
		}
		if (speaker instanceof WanderingTrader trader && group.id().equals("dbzjqi")) {
			trader.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 12000, 0, false, false));
		}
		if (group.id().equals("onindz")) speaker.addEffect(new MobEffectInstance(MobEffects.POISON, 12000));
		if (group.id().equals("xemyaj")) speaker.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 12000));
		if (group.id().equals("yebifs")) speaker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 12000));
		if (group.id().equals("etkxko")) speaker.setRemainingFireTicks(12000);
		if (group.id().equals("igebly")) speaker.setTicksFrozen(speaker.getTicksRequiredToFreeze());
	}

	private static Entity createSubject(ServerLevel level, ServerPlayer player, DialogueCatalog.DialogueGroup group,
			String title, SubjectSpec spec, Vec3 position, List<Entity> spawned) {
		if (spec == null) return null;
		Entity entity = createEntity(level, spec.type());
		if (entity == null) return null;
		prepareEntity(entity, position);
		if (entity instanceof Mob mob && spec.baby()) mob.setBaby(true);
		if (entity instanceof TamableAnimal tamable && title.startsWith("Tamed ")) tamable.tame(player);
		if (entity instanceof Bee bee && title.equals("Angry Bee")) {
			bee.setTarget(player);
			bee.startPersistentAngerTimer();
		}
		if (entity instanceof Sheep sheep) {
			sheep.setColor(DyeColor.WHITE);
			sheep.setSheared(spec.sheared());
		}
		if (entity instanceof Villager villager) configureSubjectVillager(level, villager, group, title, spec);
		if (!level.addFreshEntity(entity)) return null;
		spawned.add(entity);
		return entity;
	}

	private static SubjectSpec subjectSpec(DialogueCatalog.DialogueGroup group, String title) {
		String id = group.id();
		if (isConversation(id) || title.equals("Share Food with Another Villager")
				|| title.equals("Receive Food from Another Villager") || title.equals("Have a Baby")
				|| title.equals("See a Baby Villager")
				|| title.equals("See Another Villager Die") || title.equals("See a Villager Wearing a Cosmetic")
				|| title.equals("Villager Wears Testificate Man's Helmet") || specialSubjectName(title) != null) {
			boolean baby = title.equals("Share Food with Another Villager") || title.equals("Have a Baby")
				|| title.equals("See a Baby Villager") || title.startsWith("Give a Baby ");
			return new SubjectSpec("villager", baby, false, specialSubjectName(title));
		}
		String type = SUBJECT_TYPES.get(title);
		if (type != null) return new SubjectSpec(type, title.startsWith("Baby ") || title.equals("Tamed Baby Wolf"),
			title.equals("Sheared Sheep"), null);
		if (id.equals("ckniqq")) return new SubjectSpec("armor_stand", false, false, null);
		if (id.equals("dfdkli") || id.equals("zeykfp")) return new SubjectSpec("firework_rocket", false, false, null);
		if (id.equals("ikrwzy")) return new SubjectSpec("lightning_bolt", false, false, null);
		if (id.equals("pmaqgq")) return new SubjectSpec("tnt", false, false, null);
		if (id.equals("cvltyw")) return new SubjectSpec("experience_orb", false, false, null);
		if (id.equals("bodvsv") || id.equals("yzqpvi")) return new SubjectSpec("falling_block", false, false, null);
		if (title.equals("See Another Entity Get Hurt")) return new SubjectSpec("cow", false, false, null);
		return null;
	}

	private static void configureSubjectVillager(ServerLevel level, Villager villager,
			DialogueCatalog.DialogueGroup group, String title, SubjectSpec spec) {
		if (spec.name() != null) villager.setCustomName(Component.literal(spec.name()));
		VillagerNewsData data = (VillagerNewsData) villager;
		boolean noNose = title.equals("Two Villagers Without Noses")
			|| title.equals("One Villager Is Missing a Nose")
				&& !group.id().equals("bygaxwbayahw") && !group.id().equals("bygaxwfobzlt");
		data.vnap$setHasNose(!noNose);
		int cosmetic = switch (group.id()) {
			case "inirxg", "riezum" -> 3;
			case "ozxzla", "rlkdqd" -> 4;
			case "wurmgu", "cxeziv", "pbbywc" -> 2;
			case "anrhns" -> 1;
			default -> 0;
		};
		data.vnap$setCosmetic(cosmetic);
		setProfession(level, villager, "");
	}

	private static void configureScene(ServerLevel level, LivingEntity speaker, Entity subject,
			DialogueCatalog.DialogueGroup group, String title, Vec3 center, List<Entity> spawned) {
		if (title.equals("Two Villagers in One Boat") || title.equals("Sit in a Boat")
				|| title.equals("Boat on Land") || title.equals("Boat on Water") || title.equals("Nudge a Villager in a Boat")) {
			Entity boat = createEntity(level, "oak_boat");
			if (boat != null) {
				prepareEntity(boat, center);
				if (level.addFreshEntity(boat)) {
					spawned.add(boat);
					speaker.startRiding(boat);
					if (title.equals("Two Villagers in One Boat") && subject != null) subject.startRiding(boat);
				}
			}
		}
		if (title.contains("Minecart")) {
			Entity minecart = createEntity(level, "minecart");
			if (minecart != null) {
				prepareEntity(minecart, center);
				if (level.addFreshEntity(minecart)) {
					spawned.add(minecart);
					speaker.startRiding(minecart);
				}
			}
		}
		if (group.id().equals("myajyt")) {
			Entity secondLlama = createEntity(level, "trader_llama");
			if (secondLlama != null) {
				prepareEntity(secondLlama, center.add(0.0, 0.0, 1.6));
				if (level.addFreshEntity(secondLlama)) spawned.add(secondLlama);
			}
		}
		if (group.id().equals("qffeco")) {
			Entity golem = createEntity(level, "iron_golem");
			if (golem != null) {
				prepareEntity(golem, center.add(0.0, 0.0, 1.6));
				if (level.addFreshEntity(golem)) spawned.add(golem);
			}
		}
		if (title.equals("Jockey") && subject != null) {
			Entity rider = createEntity(level, "zombie");
			if (rider instanceof Mob mob) mob.setBaby(true);
			if (rider != null) {
				prepareEntity(rider, center);
				if (level.addFreshEntity(rider)) {
					spawned.add(rider);
					rider.startRiding(subject);
				}
			}
		}
		if (group.id().equals("asqzby")) speaker.startSleeping(speaker.blockPosition());
	}

	private static void setProfession(ServerLevel level, Villager villager, String title) {
		var profession = switch (title) {
			case "Armorer at Work" -> VillagerProfession.ARMORER;
			case "Butcher at Work" -> VillagerProfession.BUTCHER;
			case "Cartographer at Work" -> VillagerProfession.CARTOGRAPHER;
			case "Cleric at Work" -> VillagerProfession.CLERIC;
			case "Farmer at Work", "Farming", "Harvest Crops Near a Farmer" -> VillagerProfession.FARMER;
			case "Fisherman at Work" -> VillagerProfession.FISHERMAN;
			case "Fletcher at Work" -> VillagerProfession.FLETCHER;
			case "Leatherworker at Work" -> VillagerProfession.LEATHERWORKER;
			case "Librarian at Work", "Inspect Bookshelves" -> VillagerProfession.LIBRARIAN;
			case "Mason at Work" -> VillagerProfession.MASON;
			case "Shepherd at Work" -> VillagerProfession.SHEPHERD;
			case "Toolsmith at Work" -> VillagerProfession.TOOLSMITH;
			case "Weaponsmith at Work" -> VillagerProfession.WEAPONSMITH;
			case "Nitwit Wandering", "Try to Trade with a Nitwit" -> VillagerProfession.NITWIT;
			default -> title.contains("Trad") || title.contains("Workstation") || title.equals("Level Up")
				|| title.equals("Reach Master Level") || title.equals("Start Work") || title.equals("Get a Job")
				? VillagerProfession.FARMER : VillagerProfession.NONE;
		};
		int levelNumber = title.equals("Reach Master Level") ? 5 : title.equals("Level Up") ? 2 : 1;
		villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(), profession)
			.withLevel(levelNumber));
	}

	private static boolean speakerHasNoNose(String id, String title) {
		return id.equals("jktrnd") || id.equals("dcvgnm") || id.equals("bygaxwbayahw")
			|| id.equals("bygaxwfobzlt") || title.equals("Two Villagers Without Noses");
	}

	private static int speakerCosmetic(String id, String title) {
		if (id.equals("svdjdk") || id.equals("orogba")) return 1;
		if (id.equals("wurmgu") || id.equals("cxeziv")) return 2;
		if (id.equals("inirxg") || id.equals("riezum")) return 3;
		if (id.equals("ozxzla") || id.equals("rlkdqd")) return 4;
		if (title.equals("Give a Villager a Sign")) return 0;
		return 0;
	}

	private static boolean isCosmeticRecipientDialogue(String id) {
		return id.equals("wurmgu") || id.equals("inirxg") || id.equals("ozxzla")
			|| id.equals("cxeziv") || id.equals("riezum") || id.equals("rlkdqd");
	}

	private static String specialSubjectName(String title) {
		return switch (title) {
			case "Meet Testificate Man" -> "Testificate Man";
			case "Meet the Mayor" -> "The Mayor";
			case "Meet Villager #5" -> "Villager #5";
			case "Meet Villager #9" -> "Villager #9";
			default -> null;
		};
	}

	private static boolean isConversation(String id) {
		return id.startsWith("gmrypk") || id.startsWith("bygaxw") || id.startsWith("loicsw")
			|| id.startsWith("wrjbdd") || id.startsWith("wrswgi") || id.startsWith("slbqfw")
			|| id.equals("zqfvby");
	}

	private static String scenarioTitle(DialogueCatalog.DialogueGroup group) {
		if (!group.title().isBlank()) return group.title();
		String id = group.id();
		if (id.startsWith("gmrypk")) return "Two Villagers Wander Together";
		if (id.startsWith("bygaxw")) return "One Villager Is Missing a Nose";
		if (id.startsWith("loicsw")) return "Two Villagers Without Noses";
		if (id.startsWith("wrjbdd")) return "Villagers Gossip";
		if (id.startsWith("wrswgi")) return "Two Villagers at a Campfire";
		if (id.startsWith("slbqfw")) return "Attack a Villager at Home with Witnesses";
		return group.id();
	}

	private static Entity createEntity(ServerLevel level, String path) {
		EntityType<?> type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
			.getOptional(net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft", path)).orElse(null);
		return type == null ? null : type.create(level, EntitySpawnReason.COMMAND);
	}

	private static void prepareEntity(Entity entity, Vec3 position) {
		entity.addTag(ContextualDialogueController.DIALOGUE_TEST_TAG);
		entity.setInvulnerable(true);
		entity.setSilent(true);
		entity.setNoGravity(!(entity instanceof LivingEntity));
		entity.snapTo(position);
		if (entity instanceof Mob mob) {
			mob.setNoAi(true);
			mob.setPersistenceRequired();
		}
		if (entity instanceof LightningBolt lightning) lightning.setVisualOnly(true);
		if (entity instanceof PrimedTnt tnt) tnt.setFuse(12000);
	}

	private static Vec3 horizontalDirection(ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
		return horizontal.lengthSqr() < 0.0001 ? new Vec3(0.0, 0.0, 1.0) : horizontal.normalize();
	}

	private static void removeSession(UUID owner) {
		TestSession session = SESSIONS.remove(owner);
		if (session == null) return;
		if (!session.speaker.isRemoved()) ContextualDialogueController.stopTestDialogue(session.speaker);
		for (Entity entity : session.spawned) if (!entity.isRemoved()) entity.discard();
	}

	private static void cancel(UUID owner) {
		removeSession(owner);
		TEST_RUNS.remove(owner);
	}

	private static void clear() {
		for (UUID owner : List.copyOf(SESSIONS.keySet())) removeSession(owner);
		TEST_RUNS.clear();
		ticks = 0L;
	}

	private record SubjectSpec(String type, boolean baby, boolean sheared, String name) {
	}

	private static final class TestSession {
		private final LivingEntity speaker;
		private final Entity subject;
		private final List<Entity> spawned;
		private final long startTick;
		private final DialogueCatalog.DialogueGroup dialogueGroup;
		private final DialogueCatalog.DialogueVariant variant;
		private final int number;
		private final int variantOffset;
		private final boolean continuous;
		private boolean started;
		private long endTick = Long.MAX_VALUE;

		private TestSession(LivingEntity speaker, Entity subject, List<Entity> spawned,
				DialogueCatalog.DialogueGroup dialogueGroup, DialogueCatalog.DialogueVariant variant,
				long startTick, int number, int variantOffset, boolean continuous) {
			this.speaker = speaker;
			this.subject = subject;
			this.spawned = List.copyOf(spawned);
			this.startTick = startTick;
			this.dialogueGroup = dialogueGroup;
			this.variant = variant;
			this.number = number;
			this.variantOffset = variantOffset;
			this.continuous = continuous;
		}

		private DialogueCatalog.DialogueGroup group() {
			return dialogueGroup;
		}
	}

	private static final class TestRun {
		private int number;
		private int variantOffset;
		private long nextTick;
		private final boolean continuous;

		private TestRun(int number, int variantOffset, long nextTick, boolean continuous) {
			this.number = number;
			this.variantOffset = variantOffset;
			this.nextTick = nextTick;
			this.continuous = continuous;
		}
	}

	private record ScheduledTest(UUID owner, int number, int variantOffset) {
	}
}
