import { existsSync, readFileSync, readdirSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const resources = join(root, "src", "main", "resources");
const modAssets = join(resources, "assets", "villager-news-addon-port");
const cem = join(resources, "assets", "minecraft", "optifine", "cem");
const catalog = JSON.parse(readFileSync(join(modAssets, "dialogues.json"), "utf8"));
const sounds = JSON.parse(readFileSync(join(modAssets, "sounds.json"), "utf8"));
const animations = JSON.parse(readFileSync(join(modAssets, "dialogue_animations.json"), "utf8"));
const handbook = JSON.parse(readFileSync(join(modAssets, "handbook.json"), "utf8"));
const behaviorSource = readFileSync(join(root, "src/main/java/com/vnap/dialogue/ContextualDialogueController.java"), "utf8");
const dialogueTestSource = readFileSync(join(root, "src/main/java/com/vnap/command/DialogueTestCommand.java"), "utf8");
const buildSettingsSource = readFileSync(join(root, "src/main/java/com/vnap/config/VillagerNewsBuildSettings.java"), "utf8");
const initializerSource = readFileSync(join(root, "src/main/java/com/vnap/VillagerNewsAddonPort.java"), "utf8");
const buildSource = readFileSync(join(root, "build.gradle"), "utf8");
const buildSettingsResource = readFileSync(join(resources, "villager-news-addon-port-build.properties"), "utf8");
const fabricMod = JSON.parse(readFileSync(join(resources, "fabric.mod.json"), "utf8"));
const itemSource = readFileSync(join(root, "src/main/java/com/vnap/item/VillagerNewsItems.java"), "utf8");
const handbookSource = readFileSync(join(root, "src/main/java/com/vnap/client/HandbookScreen.java"), "utf8");
const clientSource = readFileSync(join(root, "src/main/java/com/vnap/client/VillagerNewsAddonPortClient.java"), "utf8");
const signLayerSource = readFileSync(join(root, "src/main/java/com/vnap/client/VillagerNewsSignLayer.java"), "utf8");
const professionLayerSource = readFileSync(join(root, "src/main/java/com/vnap/mixin/client/VillagerProfessionLayerMixin.java"), "utf8");
const villagerRendererSource = readFileSync(join(root, "src/main/java/com/vnap/mixin/client/VillagerRendererMixin.java"), "utf8");
const villagerSoundSource = readFileSync(join(root, "src/main/java/com/vnap/mixin/VillagerSoundMixin.java"), "utf8");
const subtitleSource = readFileSync(join(root, "src/main/java/com/vnap/client/DialogueSubtitleState.java"), "utf8");
const soundStateSource = readFileSync(join(root, "src/main/java/com/vnap/client/DialogueSoundState.java"), "utf8");
const animationStateSource = readFileSync(join(root, "src/main/java/com/vnap/client/DialogueAnimationState.java"), "utf8");
const settingsSource = readFileSync(join(root, "src/main/java/com/vnap/config/VillagerNewsSettings.java"), "utf8");
const settingsStateSource = readFileSync(join(root, "src/main/java/com/vnap/client/VillagerNewsSettingsState.java"), "utf8");
const modMenuSource = readFileSync(join(root, "src/main/java/com/vnap/client/VillagerNewsModMenu.java"), "utf8");
const settingsNetworkSource = readFileSync(join(root, "src/main/java/com/vnap/network/VillagerNewsSettingsNetwork.java"), "utf8");
const settingsPayloadSource = readFileSync(join(root, "src/main/java/com/vnap/network/VillagerNewsSettingsPayload.java"), "utf8");
const abstractVillagerSource = readFileSync(join(root, "src/main/java/com/vnap/mixin/AbstractVillagerMixin.java"), "utf8");
const villagerDataSource = readFileSync(join(root, "src/main/java/com/vnap/mixin/VillagerDataMixin.java"), "utf8");
const mixinConfiguration = readFileSync(join(resources, "villager-news-addon-port.mixins.json"), "utf8");
const generatorSource = readFileSync(join(root, "tools/port-addon.mjs"), "utf8");
const villagerModelSource = readFileSync(join(cem, "villager.jem"), "utf8");
const gradleProperties = readFileSync(join(root, "gradle.properties"), "utf8");
const language = JSON.parse(readFileSync(join(modAssets, "lang", "en_us.json"), "utf8"));
const handbookHeldModel = JSON.parse(readFileSync(join(modAssets, "models", "item", "handbook_held.json"), "utf8"));
const microphoneHeldModel = JSON.parse(readFileSync(join(modAssets, "models", "item", "microphone_held.json"), "utf8"));

const ffmpeg = [
  process.env.FFMPEG_PATH,
  "C:\\Users\\marcy\\Downloads\\LiSA-win32-x64-2.1.0\\resources\\resources\\lisa\\_internal\\ffmpeg.exe",
].filter(Boolean).find(existsSync);

function check(condition, message) {
  if (!condition) throw new Error(message);
}

const groups = Object.entries(catalog.groups);
check(groups.length === 523, `Expected 523 dialogue groups, found ${groups.length}`);
let variantCount = 0;
let subtitleCount = 0;
for (const [id, group] of groups) {
  check(group.variants?.length, `Dialogue ${id} has no variants`);
  check(animations.groups[id]?.length === group.variants.length, `Dialogue ${id} has mismatched animation variants`);
  for (const variant of group.variants) {
    const event = sounds[`dialogue.${id}.${variant.index}`];
    check(event?.sounds?.length === 1, `Dialogue ${id}.${variant.index} must have one exact sound`);
    check(event.subtitle === undefined, `Dialogue ${id}.${variant.index} still uses the bottom-right subtitle overlay`);
    check(variant.subtitles?.length > 0, `Dialogue ${id}.${variant.index} has no original subtitle timeline`);
    check(variant.subtitles.every((entry, index) => typeof entry.key === "string"
      && typeof language[entry.key] === "string" && language[entry.key].length > 0
      && Number.isFinite(entry.time) && (index === 0 || entry.time >= variant.subtitles[index - 1].time)),
      `Dialogue ${id}.${variant.index} has an invalid subtitle timeline`);
		subtitleCount += variant.subtitles.length;
    variantCount++;
    const sound = event.sounds[0];
		check(typeof sound === "object" && sound.stream === true, `Dialogue ${id}.${variant.index} is not streamed`);
    const name = typeof sound === "string" ? sound : sound.name;
    const relative = name.replace("villager-news-addon-port:", "");
    check(existsSync(join(modAssets, "sounds", `${relative}.ogg`)), `Missing audio file for ${name}`);
  }
}
check(variantCount === 2212, `Expected 2212 synchronized variants, found ${variantCount}`);
check(subtitleCount === 3741, `Expected 3741 timed subtitles, found ${subtitleCount}`);
check(clientSource.includes("DialogueSubtitleState.start(payload)")
  && clientSource.includes("DialogueSubtitleState.register()")
  && clientSource.includes("DialogueSubtitleState.tick(client)"),
"The timed subtitle client is not registered");
check(subtitleSource.includes("VillagerNewsClientSettings.showSubtitles()")
  && subtitleSource.includes("HudElementRegistry.attachElementAfter")
  && subtitleSource.includes("MAX_LINES = 4")
  && subtitleSource.includes("subtitleScale")
	&& subtitleSource.includes("RANGE_SQUARED")
	&& subtitleSource.includes("y -= minecraft.font.lineHeight + 3.0F")
	&& !subtitleSource.includes("lineHeight + 3.0F) * scale"), "The stacked subtitle HUD behavior is incomplete");
check(animations.gestures.length === 46, `Expected 46 dialogue gestures, found ${animations.gestures.length}`);
check(animations.locomotion?.duration === 0.4375
  && Object.keys(animations.locomotion.tracks).length === 14
  && animations.locomotion.tracks.left_leg_rx
  && animations.locomotion.tracks.left_leg_ty
  && animations.locomotion.tracks.right_leg_rx
  && animations.locomotion.tracks.right_leg_ty,
"The original Bedrock walking animation is incomplete");
check(animations.runLocomotion?.duration === 0.4375
  && animations.runLocomotion.tracks.left_leg_rx
  && animations.runLocomotion.tracks.left_leg_ty
  && animations.runLocomotion.tracks.right_leg_rx
  && animations.runLocomotion.tracks.right_leg_ty,
"The original Bedrock running animation is incomplete");
check(animationStateSource.includes("RUN_ENTER_SPEED = 0.6F")
  && animationStateSource.includes("RUN_EXIT_SPEED = 0.3F")
  && animationStateSource.includes("locomotionState.update(age, speed, groundedMovement)"),
"The original Bedrock run transition thresholds are not applied");
check(JSON.stringify(handbookHeldModel.display.thirdperson_righthand.rotation) === "[-75,0,0]"
  && JSON.stringify(handbookHeldModel.display.thirdperson_lefthand.rotation) === "[-75,0,0]"
  && JSON.stringify(handbookHeldModel.display.firstperson_righthand.rotation) === "[90,0,180]"
  && JSON.stringify(handbookHeldModel.display.firstperson_lefthand.rotation) === "[90,0,180]"
  && JSON.stringify(microphoneHeldModel.display.thirdperson_righthand.rotation) === "[0,-90,-125]"
  && JSON.stringify(microphoneHeldModel.display.thirdperson_lefthand.rotation) === "[0,90,125]",
"The original Bedrock held-item orientations are not applied");
check(!animationStateSource.includes("poseWeightAt(active.elapsedSeconds())"),
"Dialogue gestures still suppress the locomotion leg tracks");
check(animations.idles?.length === 6 && animations.idles.every((idle) => idle.duration > 0
  && Object.keys(idle.tracks).length > 0), "The six original Bedrock idle animations are incomplete");
check(animations.continuousIdle === "animation.oreville_vn.fyqjnp"
  && animations.targetLook === "animation.oreville_vn.vqhynx", "The continuous idle and target-look layers are missing");
check(animations.turnLeft === "animation.oreville_vn.aiqbsm"
	&& animations.turnRight === "animation.oreville_vn.pypqgk"
	&& animationStateSource.includes("TURN_STATES")
	&& animationStateSource.includes("legRotation(time")
	&& animationStateSource.includes("legLift(time"),
"The original left-turn and right-turn animation controller is missing");
check(animationStateSource.includes("walkAnimation.position(partialTick)")
  && animationStateSource.includes("IDLE_STATES")
	&& animationStateSource.includes("horizontalDistanceSqr > 0.0001")
	&& animationStateSource.includes("startNext(tick, -1)")
	&& animationStateSource.includes("blendFromIndex")
	&& animationStateSource.includes("getGameTimeDeltaPartialTick(true)")
	&& animationStateSource.includes("IDLE_BLEND_SECONDS")
	&& animationStateSource.includes("idle.update(age, canIdle)")
	&& animationStateSource.includes("poseWeightAt(active.elapsedSeconds())")
	&& animationStateSource.includes("LOOK_STATES")
	&& animationStateSource.includes("Mth.wrapDegrees(targetYaw - yaw)")
	&& animationStateSource.includes("previous.poseSnapshot()")
	&& animationStateSource.includes("active.transition(variableName, result)")
	&& animationStateSource.includes("EMPTY_TIMELINE")
	&& animationStateSource.includes("if (active) advance(tick)")
	&& animationStateSource.includes("locomotion.valueAt")
	&& animationStateSource.includes("runLocomotion.valueAt")
	&& animationStateSource.includes("RUN_ENTER_SPEED")
	&& animationStateSource.includes("RUN_EXIT_SPEED"), "The client does not continuously and smoothly play locomotion and stationary idle tracks");
	check(generatorSource.includes("torad(vnap_look_pitch*0.5)")
		&& generatorSource.includes("torad(vnap_look_yaw*0.77)")
		&& generatorSource.includes("max(-0.45,min(0.45,vnap_look_yaw/60))*-1")
		&& generatorSource.includes("max(-0.45,min(0.45,vnap_look_pitch/60))")
		&& !generatorSource.includes("lookEyeScale")
		&& !generatorSource.includes('rotationTerms.push("torad(head_pitch*0.5)")')
		&& !generatorSource.includes('rotationTerms.push("torad(head_yaw*0.77)")'),
	"The villager rig still applies unsmoothed vanilla look rotations");
check(behaviorSource.includes("easedRotation(mob.yBodyRot")
	&& behaviorSource.includes("easedRotation(mob.getYHeadRot()")
	&& behaviorSource.includes("easedRotation(mob.getXRot()")
	&& behaviorSource.includes("distance * proportion, 0.2F, maximumStep"),
"Dialogue participants snap into vanilla-style subject-facing rotations");

const referencedGroups = groups.filter(([id, group]) => behaviorSource.includes(`"${id}"`)
  || (group.title && behaviorSource.includes(`"${group.title}"`)));
const unreferencedGroups = groups.filter((entry) => !referencedGroups.includes(entry));
check(referencedGroups.length === groups.length, `Expected all 523 server-triggered dialogue groups, found ${referencedGroups.length}`);
check(unreferencedGroups.length === 0, `Found ${unreferencedGroups.length} dialogue groups without Java triggers`);
check(/^dialogue_test_command=(true|false)$/m.test(gradleProperties)
  && buildSource.includes('filesMatching("villager-news-addon-port-build.properties")')
  && buildSettingsResource.includes("dialogue_test_command=${dialogue_test_command}")
  && buildSettingsSource.includes('getProperty("dialogue_test_command", "false")')
  && initializerSource.includes("if (VillagerNewsBuildSettings.dialogueTestCommand()) DialogueTestCommand.register()"),
"The dialogue test command is not guarded by the disabled-by-default build setting");
check(dialogueTestSource.includes('Commands.literal("dialoguetest")')
	&& dialogueTestSource.includes("IntegerArgumentType.integer(1, DialogueCatalog.groups().size())")
	&& dialogueTestSource.includes("new ArrayList<>(DialogueCatalog.groups().values())")
	&& dialogueTestSource.includes("CLIENT_TRACKING_DELAY")
	&& dialogueTestSource.includes("playTestDialogue")
	&& dialogueTestSource.includes("removeSession")
	&& dialogueTestSource.includes("entity.discard()"),
"The numbered dialogue test command lifecycle is incomplete");
check(dialogueTestSource.includes('Commands.literal("continuous")')
	&& dialogueTestSource.includes("CONTINUOUS_GAP = 20L")
	&& dialogueTestSource.includes("variantOffset + 1 < group.variants().size()")
	&& dialogueTestSource.includes("run.number = number + 1")
	&& dialogueTestSource.includes("session.variant.index()")
	&& dialogueTestSource.includes('Component.literal("[Dialogue "')
	&& dialogueTestSource.includes("Continuous dialogue test complete"),
"The continuous dialogue test sequence is incomplete");
check(soundStateSource.includes("PENDING_TIMEOUT_NANOS")
	&& soundStateSource.includes("tryStart(minecraft, payload)")
	&& soundStateSource.includes("PendingSound")
	&& soundStateSource.includes("!entity.isSilent()"),
"Dialogue audio is discarded before newly spawned test actors reach the client");
check(dialogueTestSource.includes('case "mayor" -> "The Mayor"')
  && dialogueTestSource.includes('case "testificate_man" -> "Testificate Man"')
  && dialogueTestSource.includes('case "number_5" -> "Villager #5"')
  && dialogueTestSource.includes('case "number_9" -> "Villager #9"')
  && dialogueTestSource.includes("requiresBabySpeaker")
	&& dialogueTestSource.includes("setProfession")
	&& dialogueTestSource.includes("createSubject"),
"Dialogue tests do not reproduce speaker and subject context");
check(dialogueTestSource.includes("isCosmeticRecipientDialogue(group.id())")
	&& dialogueTestSource.includes('id.equals("cxeziv")')
	&& dialogueTestSource.includes('id.equals("bygaxwbayahw")')
	&& dialogueTestSource.includes('id.equals("bygaxwfobzlt")')
	&& dialogueTestSource.includes('id.equals("ckniqq")')
	&& dialogueTestSource.includes("specialSubjectName(title) != null")
	&& !dialogueTestSource.includes('title.startsWith("Meet ")'),
"Dialogue tests confuse cosmetic or no-nose speakers with their subjects");
check(dialogueTestSource.includes('group.id().equals("qffeco")')
	&& dialogueTestSource.includes('createEntity(level, "iron_golem")')
	&& !dialogueTestSource.includes('Map.entry("Iron Golem Targets the Player", "iron_golem")'),
"The iron-golem attack test confuses the attacker with the spoken-to player");
check((behaviorSource.match(/entity\.entityTags\(\)\.contains\(DIALOGUE_TEST_TAG\)/g) ?? []).length >= 4
  && behaviorSource.includes("!entity.entityTags().contains(DIALOGUE_TEST_TAG)"),
"Dialogue test actors can be interrupted or enter normal dialogue selection");
check(behaviorSource.includes("EntitySpawnReason.SPAWN_ITEM_USE"), "Spawn-egg dialogue does not use the server spawn reason");
check(behaviorSource.includes("maintainSpeechTargets"), "Server-side subject facing is missing");
check(behaviorSource.includes("NEARBY_SUBJECT_RANGE = 8.0")
	&& behaviorSource.includes("speaker.distanceToSqr(entity) <= NEARBY_SUBJECT_RANGE * NEARBY_SUBJECT_RANGE")
	&& behaviorSource.includes("filter(speaker::hasLineOfSight)")
	&& behaviorSource.includes("if (speech.lockMovement) holdMob(mob, position)")
	&& behaviorSource.includes("else faceMob(mob, position)"),
"Nearby observations can select distant or hidden subjects, or mobile dialogue loses subject tracking");
check(behaviorSource.includes("mob.getNavigation().stop()")
	&& behaviorSource.includes("mob.setYBodyRot(bodyYaw)")
	&& behaviorSource.includes("mob.setYHeadRot(headYaw)")
	&& behaviorSource.includes("mob.setXRot(easedRotation")
  && behaviorSource.includes("holdListener"), "Bedrock speaking movement and mutual-facing locks are incomplete");
check(behaviorSource.includes("reputation < -225")
  && behaviorSource.includes("reputation < -75")
  && behaviorSource.includes("reputation >= 75")
  && behaviorSource.includes("reputation >= 25"), "Bedrock reputation tiers are not preserved");
check(behaviorSource.includes("negativeGossip")
  && behaviorSource.includes("isNegativeReputation(first, player)"), "Player-directed gossip is not gated by bad reputation");
check(behaviorSource.includes("RECENT_VARIANTS")
  && behaviorSource.includes("Set.copyOf(recentVariants)"), "Dialogue variants can immediately repeat");
check(behaviorSource.includes("playHurtWitness(entity)")
	&& behaviorSource.includes('playSharedId(witness, "pkvhpv"')
	&& behaviorSource.includes('playSharedId(witness, "pmqrpb"')
	&& behaviorSource.includes("entity instanceof WanderingTrader")
	&& behaviorSource.includes("entity instanceof Sheep sheep && isWooly(sheep)")
	&& !behaviorSource.includes("cast(witness) == CastProfile.VILLAGER"),
"Special and regular villagers cannot react when another villager-like entity is hurt or dies");
check(behaviorSource.includes("playDamageDialogue(villager, dialogue")
	&& behaviorSource.includes("isPlaying(speaker, id) || !ready(cooldownKey, cooldown)")
	&& behaviorSource.includes("source.getEntity() == null ? SHORT_COOLDOWN")
	&& behaviorSource.includes("new ActiveSound(group.id()")
	&& behaviorSource.includes("sound.groupId.equals(groupId)")
	&& behaviorSource.includes("DAMAGE_LOCK_DIALOGUES")
	&& behaviorSource.includes("hasDamageLock(entity)")
	&& ["elryje", "onindz", "rogpvp", "etkxko", "igebly", "vnaodx"]
		.every((id) => behaviorSource.includes(`"${id}"`)),
"Repeated damage interrupts and restarts the same active hurt dialogue");
check(behaviorSource.includes("COSMETIC_RECIPIENT_DIALOGUES")
	&& behaviorSource.includes('case 2 -> "cxeziv"')
	&& behaviorSource.includes('case 3 -> "riezum"')
	&& behaviorSource.includes('case 4 -> "rlkdqd"')
	&& !behaviorSource.includes("CastProfile expected = switch (cosmetic)"),
"Cosmetic recipient dialogue is assigned to the nearby special villager");
check(behaviorSource.includes('NUMBER_5("xccwah", "legnsy", "sclaoa", "behifz", "behifz")')
  && behaviorSource.includes('TESTIFICATE_MAN("nmwmrz", "luoibc", "mpbnsm", "fzoqwd", "fzoqwd")'),
"Special villagers use incompatible regular-villager attack dialogue");
check(behaviorSource.includes("MOBILE_DIALOGUES")
  && behaviorSource.includes("!MOBILE_DIALOGUES.contains(group.id())")
  && behaviorSource.includes('"uzdxum"') && behaviorSource.includes('"behifz"'),
"Damage dialogue still removes knockback or prevents fleeing");
check(catalog.groups.cmkesu?.speaker === "mayor"
	&& generatorSource.includes('"cmkesu"')
	&& behaviorSource.includes("profile == CastProfile.MAYOR) id = \"cmkesu\""),
"The Mayor-hat observation is assigned to the wrong speaker");
check(behaviorSource.includes('Map.entry("armor_stand", "ckniqq")')
	&& behaviorSource.includes('Map.entry("cave_spider", "gtmfpl")')
	&& behaviorSource.includes("playSharedId")
	&& behaviorSource.includes("sharedAdult && sharedVillagerVoice")
	&& behaviorSource.includes('DialogueCatalog.byTitle(title, "villager")')
	&& behaviorSource.includes('new PendingSpeech(level, parent.getUUID(), "fbuabj", villager.getUUID(), ticks + 2L, true)')
	&& behaviorSource.includes('playSharedId(villager, "dfdkli"')
	&& behaviorSource.includes('playSharedId(villager, "ikrwzy"')
	&& behaviorSource.includes('playSharedId(witness, "slbqfwswxeva"')
	&& behaviorSource.includes('playSharedId(speaker, id, "time_skip:"')
	&& behaviorSource.includes('playSharedId(speaker, id, "difficulty:"'),
"Nearby-entity contexts are incomplete or blocked for special adult villagers");
check(behaviorSource.includes("playIronGolemAttackWitness(entity, source)")
	&& behaviorSource.includes('hurtType.equals("iron_golem")')
	&& behaviorSource.includes('attackerType.equals("iron_golem")')
	&& behaviorSource.includes('playSharedId(witness, "qffeco"')
	&& behaviorSource.includes("target = player")
	&& behaviorSource.includes('direct.equals("snowball")')
	&& behaviorSource.includes('direct.equals("falling_block")'),
"Damage observers or projectile and falling-block reactions have incorrect subjects");
check(behaviorSource.includes("entity instanceof WanderingTrader trader")
	&& behaviorSource.includes('attacker instanceof Player ? "vevdkl" : "wyvzhk"')
	&& behaviorSource.includes("cast(villager) == CastProfile.UNREACHABLE")
	&& behaviorSource.includes("speaker instanceof WanderingTrader")
	&& behaviorSource.includes("sharedAdult ? playSharedId"),
"Wandering Trader or Villager Unreachable hurt dialogue is rejected by speaker validation");
check(behaviorSource.includes('playSharedId(witness, id, "player_death:"')
	&& behaviorSource.includes('playSharedTitle(adult, "Stare at a Villager"')
	&& behaviorSource.includes('playSharedTitle(adult, "Stand Completely Still"')
	&& behaviorSource.includes("playSharedTitle(adult, playerContext")
	&& behaviorSource.includes('return "Stand on a Villager\'s Bed";'),
"Shared player observers are blocked for named adult villagers or use the wrong actor");
check(behaviorSource.includes('playSharedId(villagers.getFirst(), "kzemrz"')
	&& behaviorSource.includes('playSharedId(adult, "pbmrxx"')
	&& behaviorSource.includes("Villager gatheringSpeaker = villagers.stream()")
	&& behaviorSource.includes("!villager.isBaby() && !villager.isSleeping()"),
"Named villagers block crowd, baby, or difficulty observations");
check(behaviorSource.includes("droppedItems.size() >= 5"), "Dropped-item pile dialogue does not require a real pile");
check(clientSource.includes("DialogueSoundState.start(payload)")
  && clientSource.includes("DialogueSoundState.tick(client)")
  && soundStateSource.includes("EntityBoundSoundInstance")
  && soundStateSource.includes("getSoundManager().stop(active.instance())")
  && !behaviorSource.includes("speaker.getX(), speaker.getY(), speaker.getZ(), variant.sound()"),
"Dialogue sounds are not bound to and stopped for their exact speaker");
check(soundStateSource.includes('!payload.groupId().equals("hivgme")')
  && soundStateSource.includes('!payload.groupId().equals("ecslqo")'), "Villager death dialogue still stops with its dying entity");
check(behaviorSource.includes("BABY_DIALOGUES")
  && behaviorSource.includes("matchesSpeaker(speaker, group)")
  && behaviorSource.includes('if (villager.isBaby()) playId(villager, "ecslqo"')
  && behaviorSource.includes('else playSharedId(villager, "hivgme"')
  && behaviorSource.includes('if (villager.isBaby()) return BABY_DIALOGUES.contains(group.id())')
  && behaviorSource.includes('"cxeziv", "riezum", "rlkdqd"'),
"Baby villagers can speak adult dialogue or use the adult death voice");
check(villagerSoundSource.includes("vnap$removeVanillaHurtSound")
  && villagerSoundSource.includes("cir.setReturnValue(SoundEvents.EMPTY)"), "Vanilla villager hurt sounds can overlap dialogue");
check(villagerSoundSource.includes("vnap$removeVanillaAmbientSound")
  && abstractVillagerSource.includes("vnap$removeVanillaTradeSound")
  && abstractVillagerSource.includes("vnap$removeVanillaTradeUpdatedSound")
  && abstractVillagerSource.includes("vnap$removeVanillaCelebrateSound"), "Vanilla villager voice sounds can leak through ESF");
check(animationStateSource.includes("ACTIVE.entrySet().removeIf")
  && soundStateSource.includes("ACTIVE.entrySet().iterator()"), "Expired client dialogue state is not cleaned up");
check((behaviorSource.match(/tickRateManager\(\)\.runsNormally\(\)/g) ?? []).length >= 2
  && behaviorSource.includes("stopActiveDialogue(server)"), "Dialogue is not paused and stopped by /tick freeze");
check(behaviorSource.includes("!VillagerNewsSettings.dialogueEnabled()")
  && settingsStateSource.includes("getConnection() != null"), "Muting dialogue is not handled safely");
check((behaviorSource.match(/!villager\.isSleeping\(\)/g) ?? []).length >= 5
	&& behaviorSource.includes("if (sleeping)")
	&& behaviorSource.includes('villager.isSleeping() && !group.id().equals("asqzby")'), "Sleeping villagers still react through normal observer paths");
check(behaviorSource.includes("delayVillagerSleep")
  && behaviorSource.includes("processPendingSleep")
  && behaviorSource.includes("PENDING_SLEEP.remove(villager.getUUID())")
  && existsSync(join(root, "src/main/java/com/vnap/mixin/VillagerSleepMixin.java"))
  && mixinConfiguration.includes("VillagerSleepMixin"), "Villager sleep dialogue timing and interruption are incomplete");
check(behaviorSource.includes("updatedVillagers.add(villager.getUUID())")
  && behaviorSource.includes("checkedPairs.add(pair)"), "Nearby multiplayer scans still repeat villager and pair work");
check(behaviorSource.includes("tryCreateNaturalSpecial"), "Natural special-character spawning is missing");
check(!behaviorSource.includes("InteractionResult.FAIL"), "Dialogue hooks still reject vanilla trading interactions");
check(existsSync(join(root, "src/main/java/com/vnap/mixin/AbstractVillagerMixin.java")), "Trade completion mixin is missing");
check(existsSync(join(root, "src/main/java/com/vnap/mixin/VillagerDataMixin.java")), "Villager cosmetic state mixin is missing");
check(itemSource.includes("FabricCreativeModeTab.builder()"), "Villager News creative tab is missing");
check(language["itemGroup.villager-news-addon-port.items"] === "Villager News", "Villager News creative tab name is missing");
check(handbook.categories.length === 12, `Expected 12 handbook trigger categories, found ${handbook.categories.length}`);
check(handbook.categories.flatMap((category) => category.sections).length === 62, "The handbook section hierarchy is incomplete");
check(Object.keys(handbook.contexts).length === 491, "The handbook is missing documented add-on contexts");
check(handbook.overview.length === 12 && handbook.specialVillagers.length === 6
  && handbook.cosmetics.length === 6 && handbook.generalInformation.length === 8,
"The handbook guide pages do not match the add-on");
check(handbook.categories.flatMap((category) => category.sections)
  .find((section) => section.title === "Real-World Days")?.entries.length === 3,
"The handbook is missing the original real-world day guide");
check(handbookSource.includes("Search Triggers") && handbookSource.includes("DialogueCatalog") === false,
  "The handbook's searchable trigger browser is missing or using a reduced catalog");
check(clientSource.includes("new HandbookScreen()"), "Using the handbook does not open its client screen");
check(clientSource.includes("if (!level.isClientSide()) return InteractionResult.PASS;"), "The handbook opener can run on the integrated server thread");
check(handbookSource.includes("VillagerNewsSettingsState.setChattiness")
  && handbookSource.includes("VillagerNewsSettingsState.setRareVoicelines")
  && handbookSource.includes("VillagerNewsSettingsState.setSpawnSpecialVillagers")
  && handbookSource.includes("showSubtitles().set"), "The handbook settings are not interactive");
check(settingsSource.includes("scaleCooldown") && settingsSource.includes("rareVoicelines")
  && settingsSource.includes("spawnSpecialVillagers"), "The Bedrock settings are not persisted on the server");
check(behaviorSource.includes("VillagerNewsSettings.scaleCooldown")
  && behaviorSource.includes("VillagerNewsSettings.rareVoicelines")
  && behaviorSource.includes("VillagerNewsSettings.spawnSpecialVillagers"), "The server behavior does not apply every supported setting");
check(settingsNetworkSource.includes("Permissions.COMMANDS_GAMEMASTER")
  && settingsNetworkSource.includes("if (!canEdit(context.player()))")
  && settingsPayloadSource.includes("boolean canEdit")
  && settingsStateSource.includes("if (!canEdit) return")
  && handbookSource.includes("require operator permission"), "Handbook server settings are not permission protected");
check(buildSource.includes('compileOnly "com.terraformersmc:modmenu:${project.modmenu_version}"')
  && /^modmenu_version=20\.0\.2$/m.test(gradleProperties)
  && fabricMod.entrypoints?.modmenu?.includes("com.vnap.client.VillagerNewsModMenu")
  && !fabricMod.depends?.modmenu
  && modMenuSource.includes("implements ModMenuApi")
  && modMenuSource.includes("HandbookScreen::settingsScreen")
  && handbookSource.includes("public static HandbookScreen settingsScreen(Screen parent)")
  && handbookSource.includes("if (settingsOnly)")
  && settingsStateSource.includes("prepareConfigScreen")
  && settingsStateSource.includes("VillagerNewsSettings.update"),
"Optional Mod Menu configuration does not preserve local and server settings behavior");
check(behaviorSource.includes("ServerLifecycleEvents.SERVER_STOPPING")
  && behaviorSource.includes("private static void clearState()")
  && clientSource.includes("ClientPlayConnectionEvents.DISCONNECT")
  && clientSource.includes("DialogueSoundState.clear(client)"), "World shutdown leaves dialogue state active");
check(villagerDataSource.includes("vnap$keepSpecialTradeOpen") && villagerDataSource.includes("isSpecialTrader"),
  "Special villagers still inherit the vanilla unemployed-villager trade closure");
check(villagerDataSource.includes("VillagerNewsSignMessage")
  && villagerDataSource.includes("VillagerNewsSignType")
  && behaviorSource.includes("state.vnap$setSignType(offeredSign)")
  && behaviorSource.includes("Math.floorMod(state.vnap$signMessage() + direction, 87)"),
"Villagers do not hold, remove, and cycle their Bedrock signs");
check(behaviorSource.includes('equals("firework_rocket")')
	&& behaviorSource.includes('playSharedId(villager, "dfdkli"')
	&& behaviorSource.includes('playId(villager, "zeykfp"'), "Firework spawn reactions are incomplete");
check(behaviorSource.includes("playHomeChestReaction")
  && behaviorSource.includes("MemoryModuleType.HOME")
  && behaviorSource.includes('playId(villager, "qfhrlh"'), "Villager home chest reactions are incomplete");
check(behaviorSource.includes("source.getDirectEntity() == player")
  && behaviorSource.includes("weaponAttackDialogue(player.getMainHandItem())"), "Player attacks can trigger competing dialogue paths");
check(villagerRendererSource.includes("StableVillagerData")
  && villagerRendererSource.includes("tick - pendingSince >= 2")
  && villagerRendererSource.includes("state.villagerData = stableData.resolve"), "Transient profession texture states are not filtered");
check(!villagerModelSource.includes("villager_news_sign_board_")
  && clientSource.includes("LivingEntityRenderLayerRegistrationCallback.EVENT.register")
  && signLayerSource.includes('"textures/block/" + wood + "_sign.png"')
  && !signLayerSource.includes("textures/entity/signs/")
  && signLayerSource.includes("getPositionerForAttachment(EMFAttachment.Type.VILLAGER)")
  && signLayerSource.includes("-5.75F / 16.0F")
  && villagerModelSource.includes('"villager_item"')
  && existsSync(join(root, "src/main/java/com/vnap/mixin/client/VillagerRendererMixin.java"))
  && existsSync(join(modAssets, "textures", "entity", "sign_text.png")),
"The original sign board or its 87-message text atlas is missing");
check(/"villager_item":\s*\[\s*0,\s*0,\s*0\s*\]/.test(villagerModelSource)
  && villagerModelSource.includes('.visible": "vnap_has_nose==1"'),
"Held items or sheared noses retain the wrong model visibility transform");
check(mixinConfiguration.includes("VillagerSoundMixin")
  && existsSync(join(root, "src/main/java/com/vnap/mixin/VillagerSoundMixin.java")),
"Vanilla villager death sounds are not deterministically suppressed");
check(professionLayerSource.includes("vnap$alignAdultClothingWithEmfModel")
  && professionLayerSource.includes("return layer.getParentModel()"),
"Villager profession clothing is not aligned with the EMF model");
const professionTextures = {
  none: "din",
  armorer: "djv",
  butcher: "djw",
  cartographer: "djx",
  cleric: "djy",
  farmer: "djz",
  fisherman: "dka",
  fletcher: "dkb",
  leatherworker: "dkc",
  librarian: "dkd",
  mason: "dkg",
  nitwit: "dkh",
  shepherd: "dke",
  toolsmith: "djg",
  weaponsmith: "dkf",
};
for (const [profession, texture] of Object.entries(professionTextures)) {
  check(generatorSource.includes(`"profession/${profession}.png": "${texture}"`),
    `${profession} does not use its original Bedrock profession texture`);
  check(existsSync(join(resources, "assets", "minecraft", "textures", "entity", "villager", "profession", `${profession}.png`)),
    `${profession} profession texture was not generated`);
}
check(/^version=1\.3\.2$/m.test(gradleProperties), "The project version is not 1.3.2");
check(language["guide.villager-news-addon-port.header"] === "Villager News 1.3.2", "The handbook version is not 1.3.2");
const merchantCheck = behaviorSource.indexOf("player.containerMenu instanceof MerchantMenu");
const openingDialogue = behaviorSource.indexOf("trade_open:");
check(merchantCheck >= 0 && openingDialogue > merchantCheck, "Trade opening dialogue still runs before the merchant menu opens");

for (const item of ["handbook", "mayor_hat", "microphone", "moustache", "testificate_man_helmet", "villager_nose"]) {
  check(existsSync(join(modAssets, "items", `${item}.json`)), `Missing client item definition for ${item}`);
  check(existsSync(join(modAssets, "models", "item", `${item}.json`)), `Missing item model for ${item}`);
  check(existsSync(join(modAssets, "textures", "item", `${item}.png`)), `Missing item texture for ${item}`);
}
for (const item of ["mayor_villager_spawn_egg", "testificate_man_spawn_egg", "villager_5_spawn_egg",
  "villager_9_spawn_egg", "untouchable_villager_spawn_egg", "wooly_spawn_egg"]) {
  check(itemSource.includes(item.toUpperCase()), `Missing registered spawn egg ${item}`);
  check(existsSync(join(modAssets, "items", `${item}.json`)), `Missing client item definition for ${item}`);
  check(existsSync(join(modAssets, "models", "item", `${item}.json`)), `Missing item model for ${item}`);
  check(existsSync(join(modAssets, "textures", "item", `${item}.png`)), `Missing original texture for ${item}`);
  check(typeof language[`item.villager-news-addon-port.${item}`] === "string", `Missing item name for ${item}`);
}
check(itemSource.includes("ComponentSerialization.CODEC.encodeStart(NbtOps.INSTANCE")
  && !itemSource.includes('{\\"text\\":\\"')
  && !itemSource.includes('putByte("Color"'), "Spawn eggs still write malformed names or dye Wooly red");
check(behaviorSource.includes("normalizeSpecialEntity(entity)")
  && behaviorSource.includes("sheep.setColor(DyeColor.WHITE)")
  && !behaviorSource.includes("sheep.setColor(DyeColor.RED)"), "Existing special entities are not repaired on load");
for (const file of readdirSync(join(modAssets, "sounds", "voice")).filter((name) => name.endsWith(".ogg"))) {
  const data = readFileSync(join(modAssets, "sounds", "voice", file));
  let offset = 0;
  while (offset + 27 <= data.length && data.toString("ascii", offset, offset + 4) === "OggS") {
    const segmentCount = data[offset + 26];
    let bodySize = 0;
    for (let index = 0; index < segmentCount; index++) bodySize += data[offset + 27 + index];
    const granule = data.readBigUInt64LE(offset + 6);
    check(!(granule > 0xffffffffn && granule < 0x200000000n), `${file} has a malformed Bedrock OGG granule timestamp`);
    offset += 27 + segmentCount + bodySize;
  }
}
const heldGeometry = {
  handbook: { elementCount: 12, textureSize: [19, 12], geometry: "geometry.oreville_vn.-1897072036", texture: "eaz" },
  microphone: { elementCount: 2, textureSize: [16, 16], geometry: "geometry.oreville_vn.96833500", texture: "ebb" },
};
const heldContexts = new Set([
  "thirdperson_righthand",
  "thirdperson_lefthand",
  "firstperson_righthand",
  "firstperson_lefthand",
]);
for (const [item, expected] of Object.entries(heldGeometry)) {
  const definition = JSON.parse(readFileSync(join(modAssets, "items", item + ".json"), "utf8"));
  const held = JSON.parse(readFileSync(join(modAssets, "models", "item", item + "_held.json"), "utf8"));
  const textureFile = join(modAssets, "textures", "item", "held", item + ".png");
  const contexts = new Set(definition.model?.cases?.map((entry) => entry.when));
  check(definition.model?.type === "minecraft:select"
    && definition.model?.property === "minecraft:display_context"
    && [...heldContexts].every((context) => contexts.has(context)),
  item + " does not use its original 3D model in every hand context");
  check(definition.model?.fallback?.model === "villager-news-addon-port:item/" + item,
    item + " does not preserve its inventory model");
  check(held.elements?.length === expected.elementCount
    && held.elements.every((element) => element.from?.length === 3 && element.to?.length === 3
      && Object.keys(element.faces ?? {}).length > 0),
  item + " has incomplete held geometry");
  check(existsSync(textureFile), item + " is missing its original held texture");
  const texture = readFileSync(textureFile);
  check(texture.readUInt32BE(16) === expected.textureSize[0]
    && texture.readUInt32BE(20) === expected.textureSize[1], item + " held texture has the wrong dimensions");
  check(generatorSource.includes(expected.geometry) && generatorSource.includes('texture: "' + expected.texture + '"'),
    item + " held model is not reproducible from the original attachable");
}

const wearableGeometry = {
  mayor_hat: { elementCount: 8, from: [2.4, 14.4, 2.4], to: [13.6, 16, 13.6], textureSize: [32, 32] },
  moustache: { elementCount: 1, from: [4.8, 4, 0.4], to: [11.2, 5.6, 0.8], textureSize: [16, 16] },
  testificate_man_helmet: { elementCount: 3, from: [0.72, 2.32, 0.72], to: [15.28, 20.08, 15.28], textureSize: [16, 32] },
  villager_nose: { elementCount: 1, from: [6.4, 0, -1.6], to: [9.6, 6.4, 1.6], textureSize: [64, 64] },
};
for (const [item, expected] of Object.entries(wearableGeometry)) {
  const definition = JSON.parse(readFileSync(join(modAssets, "items", `${item}.json`), "utf8"));
  const worn = JSON.parse(readFileSync(join(modAssets, "models", "item", `${item}_worn.json`), "utf8"));
  const textureFile = join(modAssets, "textures", "item", "worn", `${item}.png`);
  const headCase = definition.model?.cases?.find((entry) => entry.when === "head");
  check(definition.model?.type === "minecraft:select"
    && definition.model?.property === "minecraft:display_context"
    && headCase?.model?.model === `villager-news-addon-port:item/${item}_worn`,
  `${item} does not use its worn model on a player head`);
  check(definition.model?.fallback?.model === `villager-news-addon-port:item/${item}`,
    `${item} does not preserve its inventory model`);
  check(worn.elements?.length === expected.elementCount, `${item} has incomplete wearable geometry`);
  check(worn.elements.every((element) => element.from?.length === 3 && element.to?.length === 3
    && Object.keys(element.faces ?? {}).length > 0), `${item} has malformed wearable cubes`);
  check(JSON.stringify(worn.elements[0].from) === JSON.stringify(expected.from)
    && JSON.stringify(worn.elements[0].to) === JSON.stringify(expected.to),
  `${item} is not anchored to the original Bedrock player-head coordinates`);
  check(existsSync(textureFile), `${item} is missing its original wearable texture`);
  const texture = readFileSync(textureFile);
  check(texture.readUInt32BE(16) === expected.textureSize[0]
    && texture.readUInt32BE(20) === expected.textureSize[1], `${item} has an unsafe atlas texture size`);
  const rgba = execFileSync(ffmpeg, [
    "-v", "error", "-i", textureFile, "-f", "rawvideo", "-pix_fmt", "rgba", "-",
  ]);
  let transparentPixels = 0;
  let opaquePixels = 0;
  for (let index = 3; index < rgba.length; index += 4) {
    if (rgba[index] === 0) transparentPixels++;
    if (rgba[index] === 255) opaquePixels++;
  }
  check(transparentPixels > 0 && opaquePixels > 0, `${item} wearable texture lost its alpha channel`);
}
check(existsSync(join(resources, "data", "villager-news-addon-port", "recipe", "handbook.json")), "Handbook recipe is missing");

for (const { file, localScale, armsRest } of [
  { file: "villager.jem", localScale: 1, armsRest: "-0.74997+vnap_arms_rx" },
  { file: "villager_baby.jem", localScale: 3, armsRest: "-1.0472+vnap_arms_rx" },
  { file: "villager2.jem", localScale: 3, armsRest: "-1.0472+vnap_arms_rx" },
  { file: "villager3.jem", localScale: 1, armsRest: "-0.74997+vnap_arms_rx" },
  { file: "villager4.jem", localScale: 1, armsRest: "-0.74997+vnap_arms_rx" },
  { file: "villager5.jem", localScale: 1, armsRest: "-0.74997+vnap_arms_rx" },
  { file: "villager6.jem", localScale: 1, armsRest: "-0.74997+vnap_arms_rx" },
  { file: "wandering_trader.jem", localScale: 1, armsRest: "-0.74997+vnap_arms_rx" },
]) {
  const model = JSON.parse(readFileSync(join(cem, file), "utf8"));
  const flatten = (entries) => entries.flatMap((entry) => [entry, ...flatten(entry.submodels ?? [])]);
  const all = flatten(model.models);
  const base = (bone) => all.find((entry) => entry.id?.endsWith(`_base_${bone}`));
  const rootModel = base("root");
  check(rootModel?.part === "root" && rootModel.attach === true, `${file} does not attach the Bedrock root rig`);
  check(JSON.stringify(rootModel.translate) === "[0,-24,0]", `${file} has the wrong Bedrock-to-Java root offset`);
  for (const part of ["head", "nose", "headwear", "headwear2", "body", "bodywear", "arms", "right_leg", "left_leg"]) {
    const suppressor = model.models.find((entry) => entry.part === part);
    check(suppressor?.attach === false && !suppressor.boxes?.length, `${file} does not suppress the vanilla ${part}`);
  }

  const face = all.filter((entry) => /_(egfg3jgo|egml9|l66l9|d67l_6q6|ja89l_6q6)$/.test(entry.id ?? ""));
  check(face.length >= 5, `${file} is missing animated facial bones`);
  check(face.every((entry) => Math.abs(entry.translate?.[1] ?? 0) < 6 * localScale), `${file} contains a non-local facial pivot`);
  check(face.flatMap((entry) => entry.boxes ?? []).every((box) => Math.abs(box.coordinates?.[1] ?? 0) < 8 * localScale), `${file} contains a world-space facial cube`);

  const topLevel = new Set(model.models);
  check(all.filter((entry) => !topLevel.has(entry)).every((entry) => !entry.animations?.length), `${file} contains nested animations that EMF will not collect`);
  const animationText = JSON.stringify(rootModel.animations ?? []);
  check(animationText.includes("vnap_root_rx"), `${file} root motion is not kept on its authored pivot`);
  check(animationText.includes("vnap_waist_rx"), `${file} waist motion is not kept on its authored pivot`);
  check(animationText.includes("vnap_body_rx"), `${file} body motion is not kept on its authored pivot`);
  check(animationText.includes("vnap_head_rx"), `${file} head motion is not kept on its authored pivot`);
  check(animationText.includes("vnap_head_inner_rx"), `${file} inner-head motion is not kept on its authored pivot`);
  check(animationText.includes(armsRest), `${file} has malformed crossed-arm motion`);
  check(animationText.includes("_egml9.sx") && animationText.includes("vnap_mouth_open"), `${file} mouth animation was not hoisted`);
  check(animationText.includes('_base_egml9.sz":"1"'), `${file} does not show the neutral mouth line at rest`);
  check(animationText.includes('_l66l9lgh.sx":"(0.75+vnap_mouth_width*0.25-vnap_mouth_closed)*vnap_speaking"')
    && animationText.includes('_l66l93gllge.sx":"(0.75+vnap_mouth_width*0.25-vnap_mouth_closed)*vnap_speaking"'),
  `${file} does not resize both rendered teeth strips directly`);
  const toothTravel = localScale === 3 ? "1.5" : "0.75";
  check(animationText.includes('_l66l9lgh.ty":"') && animationText.includes(`vnap_mouth_open*-${toothTravel}`)
    && animationText.includes('_l66l93gllge.ty":"') && animationText.includes(`vnap_mouth_open*${toothTravel}`),
  `${file} does not separate its upper and lower teeth while speaking`);
  check(!animationText.includes('_l66l9.sx"'), `${file} still applies tooth scaling to the empty parent bone`);
  check(animationText.includes("_egfg3jgo.ty") && animationText.includes("vnap_brow_ty"), `${file} brow animation was not hoisted`);
  check(animationText.includes("6q6da5kmhh6j.sy") && animationText.includes("6q6da5kdgo6j.sy") && animationText.includes("2.02"), `${file} does not animate both eyelid halves`);
	check(animationText.includes("vnap_left_leg_rx") && animationText.includes("vnap_right_leg_rx"), `${file} does not animate both upper-leg pivots`);
	check(!animationText.includes("sin(limb_swing"), `${file} still uses the simplified walk instead of the original Bedrock track`);
  check(!animationText.includes("limb_speed*(1-vnap_speaking)"), `${file} freezes its legs while dialogue is playing`);
  check(!animationText.includes("_jggl_leftleg.rx\":\"sin(limb_swing") && !animationText.includes("_jggl_rightleg.rx\":\"sin(limb_swing"), `${file} still walks from the foot pivots`);
  if (file !== "wandering_trader.jem") {
    check(animationText.includes("vnap_has_nose"), `${file} does not respond to synchronized nose state`);
  }
  if (file === "villager.jem" || file === "villager_baby.jem") {
    for (const cosmetic of ["mayor_hat", "helmet", "microphone", "moustache"]) {
      check(JSON.stringify(model).includes(`vnap_cosmetic_${cosmetic}`), `${file} is missing the ${cosmetic} cosmetic`);
    }
    const wearablePrefix = file === "villager.jem" ? "villager_news" : "villager_news_baby";
    const helmetBelt = all.find((entry) => entry.id === `${wearablePrefix}_extra_1_l6kla7a42l636dl`);
    check(helmetBelt?.boxes?.[0]?.sizeAdd === 0.0625, `${file} has a belt coplanar with the villager robe`);
  }

  const head = base("headjgl2l6");
  const nose = base("fgk6");
  const arms = base("2jek");
  const bodywear = base("jg36");
  if (file === "villager3.jem") {
    const testificateBelt = all.find((entry) => entry.id === "testificate_extra_0_l6kla7a42l636dl");
    check(rootModel.texture === "villager-news-addon-port:textures/entity/testificate_man.png",
      "Testificate Man lost his original character texture");
    check(testificateBelt?.boxes?.[0]?.sizeAdd === undefined,
      "The named Testificate Man model was changed with the wearable belt adjustment");
  }
  if (file === "villager2.jem") {
    const mayorExtra = all.find((entry) => entry.id === "mayor_extra_0_lghhat");
    check(head?.boxes?.some((box) => box.coordinates?.slice(3).includes(24)), "Mayor is not using the large baby base rig");
    check(mayorExtra?.boxes?.some((box) => box.coordinates?.slice(3).includes(18)), "Mayor hat geometry is missing");
    check(animationText.includes("0.33333*vnap_root_sx"), "Mayor base rig is not scaled to its Bedrock entity size");
    const extraRoot = model.models.find((entry) => entry.id === "mayor_extra_0_root");
    check(JSON.stringify(extraRoot?.animations ?? []).includes("0.33333*vnap_root_sx"), "Mayor hat does not share the base rig scale");
	  } else if (file === "villager.jem") {
	    const mayorHat = all.find((entry) => entry.id === "villager_news_extra_0_lghhat");
	    const mayorMonocle = all.find((entry) => entry.id === "villager_news_extra_0_egfg4d6");
	    const mayorAnimations = JSON.stringify(model.models.find((entry) => entry.id === "villager_news_extra_0_root")?.animations ?? []);
	    check(animationText.includes('"villager_news_base_hat.visible":"vnap_cosmetic_mayor_hat==0&&vnap_cosmetic_helmet==0"'),
	      "The ordinary villager headwear visibility is not a boolean EMF expression");
	    check(mayorHat?.boxes?.some((box) => box.coordinates?.slice(3).includes(8)), "The villager Mayor hat is using the oversized special-character geometry");
	    check(mayorAnimations.includes('"this.sx":"vnap_cosmetic_mayor_hat"')
	      && mayorAnimations.includes('"villager_news_extra_0_lghhat.sx":0.9')
	      && mayorAnimations.includes('"villager_news_extra_0_lghhat.sz":0.9'),
	    "The wearable Mayor hat is not reduced around its own pivot");
	    check(JSON.stringify(mayorHat?.translate) === "[0,7.01998,0]"
	      && JSON.stringify(mayorHat?.boxes?.map((box) => box.coordinates[1])) === "[4.53002,3.28602]",
	    "The wearable Mayor hat cubes are not lowered onto the head");
	    check(JSON.stringify(mayorMonocle?.translate) === "[-3.27,4.805,-3.526]", "The wearable Mayor monocle is floating in front of the face");
  } else if (file === "villager_baby.jem") {
    check(head?.boxes?.some((box) => box.coordinates?.slice(3).includes(24)), "Baby villager is not using the add-on's large-head rig");
    check(animationText.includes("0.33333*vnap_root_sx") && animationText.includes("0.33333*vnap_root_sy")
      && animationText.includes("0.33333*vnap_root_sz"), "Baby villager does not apply its authored one-third rig scale");
  } else {
    check(JSON.stringify(head?.boxes?.[0]?.coordinates) === "[-4,0,-4,8,10,8]", `${file} has malformed local head geometry`);
    check(JSON.stringify(head?.boxes?.[0]?.uvSouth) === "[24,8,32,18]", `${file} has unconverted Bedrock face UVs`);
    check(JSON.stringify(nose?.translate) === "[0,2.5,-4]", `${file} has a displaced local nose pivot`);
    check(JSON.stringify(nose?.boxes?.[0]?.coordinates) === "[-1,-3.5,-2,2,4,2]", `${file} has malformed local nose geometry`);
    check(JSON.stringify(arms?.translate) === "[0,-3,-1]", `${file} has a displaced local arm pivot`);
    check(JSON.stringify(arms?.boxes?.[0]?.coordinates) === "[-4,-6,-2,8,4,4]", `${file} has malformed local crossed-arm geometry`);
    check(bodywear?.boxes?.[0]?.sizeAdd === 0.5, `${file} does not preserve the authored robe shell size`);
  }
}

check(existsSync(join(resources, "assets", "minecraft", "textures", "entity", "villager", "villager_baby.png")),
  "Baby villager base texture is missing");
check(readFileSync(join(resources, "assets", "minecraft", "textures", "entity", "villager", "villager_baby.png"))
  .equals(readFileSync(join(modAssets, "textures", "entity", "dkn.png"))),
"Baby villager is not using the original add-on's dedicated baby face texture");

for (const event of ["ambient", "hurt", "death", "trade", "no"]) {
  const properties = readFileSync(join(resources, "assets", "minecraft", "esf", "entity", "villager", `${event}.properties`), "utf8");
  check(properties.includes("sounds.1=2") && !properties.includes("baby.1=false"),
    `Baby villagers are not covered by the ${event} vanilla-sound replacement`);
}
for (const event of ["ambient", "hurt", "death", "trade", "no", "yes"]) {
  const eventRoot = join(resources, "assets", "minecraft", "esf", "entity", "wandering_trader");
  const properties = readFileSync(join(eventRoot, `${event}.properties`), "utf8");
  const replacement = JSON.parse(readFileSync(join(eventRoot, `${event}2.json`), "utf8"));
  check(properties.includes("sounds.1=2") && replacement.sounds?.[0]?.name === "villager-news-addon-port:silence",
    `The Wandering Trader's ${event} vanilla sound is not replaced`);
}
for (const event of ["ambient", "hurt", "death"]) {
  const properties = readFileSync(join(resources, "assets", "minecraft", "esf", "entity", "sheep", `${event}.properties`), "utf8");
  check(properties.includes("sounds.1=2") && properties.includes("name.1=iregex:(Wooly|Wooly The Sheep)"),
    `Wooly's ${event} vanilla sound is not selectively replaced`);
}

{
  const model = JSON.parse(readFileSync(join(cem, "sheep2.jem"), "utf8"));
  const flatten = (entries) => entries.flatMap((entry) => [entry, ...flatten(entry.submodels ?? [])]);
  const all = flatten(model.models);
  const woolyBone = (bone) => all.find((entry) => entry.id === `wooly_base_${bone}`);
  const rootModel = model.models.find((entry) => entry.id === "wooly_base_root");
  check(rootModel?.part === "root" && rootModel.attach === true, "Wooly does not attach its Bedrock root rig");
  check(JSON.stringify(rootModel.translate) === "[0,-24,0]", "Wooly has the wrong Bedrock-to-Java root offset");
  for (const bone of [
    "root", "body", "46fljga5", "oggd_46fljga5", "k966h_head", "oggd_head", "3dafc", "7246gn6jd2q",
    "l66l9", "l66l9lgh", "l66l93gllge", "egml9", "root_d680", "d0_7dggj", "d680", "oggd_d680",
    "root_d681", "d1_7dggj", "d681", "oggd_d681", "root_d682", "d2_7dggj", "d682", "oggd_d682",
    "root_d683", "d3_7dggj", "d683", "oggd_d683",
  ]) check(woolyBone(bone), `Wooly is missing source bone ${bone}`);
  check(all.filter((entry) => entry.id?.startsWith("wooly_base_")).flatMap((entry) => entry.boxes ?? []).length === 22,
    "Wooly does not preserve all 22 source cubes");
  check(JSON.stringify(woolyBone("body")?.translate) === "[0,14.25,0]", "Wooly's body pivot is malformed");
  check(JSON.stringify(woolyBone("46fljga5")?.rotate) === "[-90,0,0]", "Wooly's body cube has the wrong rotation");
  check(JSON.stringify(woolyBone("k966h_head")?.translate) === "[0,3.75,-8]", "Wooly's head pivot is malformed");
  check(JSON.stringify(woolyBone("k966h_head")?.boxes?.[0]?.uvNorth) === "[8,8,14,14]", "Wooly's neutral face backing is missing");
  check(JSON.stringify(woolyBone("7246gn6jd2q")?.translate) === "[0,0,-0.025]", "Wooly's expression plane is not separated from its head");
  check(woolyBone("7246gn6jd2q")?.boxes?.every((box) => box.coordinates?.[5] === 0.05),
    "Wooly's expression geometry still contains zero-depth planes");
  for (const leg of ["d680", "d681", "d682", "d683"]) {
    check(JSON.stringify(woolyBone(leg)?.translate) === "[0,11.5,0]", `Wooly's ${leg} hip pivot is malformed`);
    const base = woolyBone(leg);
    const box = base?.boxes?.[0] ?? base?.submodels?.find((child) => child.id === `wooly_base_${leg}_cube_0`)?.boxes?.[0];
    check(JSON.stringify(box?.textureOffset) === "[0,16]", `Wooly's ${leg} source hoof UV is malformed`);
  }
  for (const part of ["head", "body", "leg1", "leg2", "leg3", "leg4"]) {
    const suppressor = model.models.find((entry) => entry.part === part);
    check(suppressor?.attach === false && !suppressor.boxes?.length, `Wooly does not suppress the vanilla ${part}`);
  }
  const animationText = JSON.stringify(rootModel.animations ?? []);
  check(animationText.includes("wooly_base_k966h_head.rx") && animationText.includes("head_pitch"), "Wooly's head look animation is missing");
  for (const leg of ["d680", "d681", "d682", "d683"]) {
    check(animationText.includes(`wooly_base_${leg}.rx`) && animationText.includes("limb_swing"), `Wooly's ${leg} leg animation is missing`);
  }
  check(!animationText.includes("wooly_base_root_d680.rx") && !animationText.includes("wooly_base_root_d681.rx")
    && !animationText.includes("wooly_base_root_d682.rx") && !animationText.includes("wooly_base_root_d683.rx"),
  "Wooly still walks from the feet-level controller pivots");
  check(animationText.includes("wooly_base_46fljga5.rx") && animationText.includes("wooly_base_k966h_head.ty"), "Wooly's walking body and head animation is missing");
  check(!animationText.includes("time*pi") && !animationText.includes("time*0.7854"), "Wooly still has tick-rate idle shaking");
  check(animationText.includes("wooly_base_3dafc.sx") && animationText.includes("36.6666"), "Wooly's blink animation is missing");
  for (const fleece of ["oggd_46fljga5", "oggd_head", "oggd_d680", "oggd_d681", "oggd_d682", "oggd_d683"]) {
    check(animationText.includes(`wooly_base_${fleece}.visible`) && animationText.includes("!nbt(Sheared,1)"), `Wooly's ${fleece} does not hide when sheared`);
  }
  check(animationText.includes("wooly_base_egml9.sx") && animationText.includes("vnap_mouth_open"), "Wooly's dialogue mouth animation is missing");
  check(animationText.includes('wooly_base_l66l9.sz\":\"(1-vnap_mouth_closed)*vnap_speaking'), "Wooly's neutral mouth does not hide the closed-mouth layer");
  check(animationText.includes('wooly_base_egml9.sx\":\"(0.5+vnap_mouth_width*0.5)*vnap_speaking+(1-vnap_speaking)'),
    "Wooly's pink mouth is not visible in the resting pose");
  const mouth = woolyBone("egml9");
  const closedMouth = woolyBone("l66l9");
  const upperLip = woolyBone("l66l9lgh");
  const lowerLip = woolyBone("l66l93gllge");
  check(JSON.stringify(mouth?.translate) === "[0,-1.7,-6.35]", "Wooly's pink mouth is not separated from the face");
  check(JSON.stringify(closedMouth?.translate) === "[0,-1,-6.875]", "Wooly's mouth strips are not separated from the pink mouth");
  for (const lip of [upperLip, lowerLip]) {
    check(lip?.boxes?.every((box) => JSON.stringify(box.uvNorth) === "[12,10,13,11]"),
      "Wooly's point-sampled mouth strip UV was not expanded for EMF");
  }
  const faceDepth = woolyBone("7246gn6jd2q").translate[2] + woolyBone("7246gn6jd2q").boxes[0].coordinates[2];
  const mouthDepth = mouth.translate[2] + mouth.boxes[0].coordinates[2];
  const lipDepth = closedMouth.translate[2] + upperLip.translate[2] + upperLip.boxes[0].coordinates[2];
  check(lipDepth < mouthDepth && mouthDepth < faceDepth,
    `Wooly's mouth layers have an unstable depth order: lip=${lipDepth}, mouth=${mouthDepth}, face=${faceDepth}`);

  const shearedModel = JSON.parse(readFileSync(join(cem, "sheep3.jem"), "utf8"));
  const shearedAll = flatten(shearedModel.models);
  check(shearedAll.filter((entry) => entry.id?.startsWith("wooly_base_")).flatMap((entry) => entry.boxes ?? []).length === 16,
    "Wooly's sheared model does not contain exactly the 16 skin and face cubes");
  for (const fleece of ["oggd_46fljga5", "oggd_head", "oggd_d680", "oggd_d681", "oggd_d682", "oggd_d683"]) {
    const fleeceModel = shearedAll.find((entry) => entry.id === `wooly_base_${fleece}`);
    check(fleeceModel && !(fleeceModel.boxes?.length), `Wooly's sheared ${fleece} still contains fleece geometry`);
  }
  const shearedRoot = shearedModel.models.find((entry) => entry.id === "wooly_base_root");
  check(JSON.stringify(shearedRoot?.animations ?? []) === JSON.stringify(rootModel?.animations ?? []),
    "Wooly's sheared variant does not preserve the working animations");
  const sheepProperties = readFileSync(join(cem, "sheep.properties"), "utf8");
  check(sheepProperties.includes("models.1=3") && sheepProperties.includes("nbt.1.Sheared=1")
    && sheepProperties.includes("models.2=2"), "Wooly's sheared model selector is missing");

  for (const layer of ["sheep_wool_undercoat", "sheep_wool"]) {
    const woolLayer = JSON.parse(readFileSync(join(cem, `${layer}2.jem`), "utf8"));
    check(woolLayer.models.length === 6 && woolLayer.models.every((entry) => entry.attach === false && !entry.boxes?.length), `Wooly's ${layer} layer is not suppressed`);
    const woolProperties = readFileSync(join(cem, `${layer}.properties`), "utf8");
    check(woolProperties.includes("models.1=2") && woolProperties.includes("Wooly The Sheep"), `Wooly's ${layer} selector is missing`);
  }

  check(ffmpeg, "FFmpeg is required to verify Wooly's Bedrock alpha-mask conversion");
  const woolyTexture = join(modAssets, "textures", "entity", "diw.png");
  const rgba = execFileSync(ffmpeg, [
    "-hide_banner", "-loglevel", "error", "-i", woolyTexture,
    "-f", "rawvideo", "-pix_fmt", "rgba", "-frames:v", "1", "pipe:1",
  ]);
  const alphaValues = new Set();
  for (let offset = 3; offset < rgba.length; offset += 4) alphaValues.add(rgba[offset]);
  check(alphaValues.has(0) && alphaValues.has(255) && alphaValues.size === 2,
    `Wooly's texture alpha was not converted from Bedrock's mask semantics: ${[...alphaValues].sort((a, b) => a - b)}`);
}

for (const [gesture, companion] of Object.entries({
  phmycx: "clnzxd", qcjrlv: "xmtqdi", srtjvb: "aahqsf", hlofgw: "cytfsh",
  tlrowv: "pbfspv", hmnopd: "qswzxh", ypxycs: "kkagqa",
})) {
  const baked = animations.gestures.find((entry) => entry.name === gesture);
  check(baked?.layers?.includes(companion), `Gesture ${gesture} is missing companion layer ${companion}`);
}

const trackNames = new Set(animations.gestures.flatMap((gesture) => Object.keys(gesture.tracks)));
for (const target of ["root", "waist", "body", "head", "head_inner", "arms", "left_leg_root", "left_leg", "right_leg_root", "right_leg", "brow", "eye_group", "lower_face", "pupil_left", "pupil_right", "eye_left", "eye_right", "nose"]) {
  check([...trackNames].some((name) => name.startsWith(`${target}_`)), `No gesture animates the ${target} bone`);
}

for (const file of readdirSync(cem).filter((name) => name.endsWith(".jem"))) {
  JSON.parse(readFileSync(join(cem, file), "utf8"));
}

check(gradleProperties.includes("version=1.3.2"), "Mod version is not 1.3.2");

console.log(JSON.stringify({
  dialogueGroups: groups.length,
  soundEvents: Object.keys(sounds).length,
  synchronizedVariants: variantCount,
  dialogueGestures: animations.gestures.length,
  voiceFiles: readdirSync(join(modAssets, "sounds", "voice")).filter((name) => name.endsWith(".ogg")).length,
  cemModels: readdirSync(cem).filter((name) => name.endsWith(".jem")).length,
  serverTriggeredDialogueGroups: referencedGroups.length,
  unreferencedDialogueGroups: unreferencedGroups.length,
  handbookContexts: Object.keys(handbook.contexts).length,
  handbookCategories: handbook.categories.length,
}, null, 2));
