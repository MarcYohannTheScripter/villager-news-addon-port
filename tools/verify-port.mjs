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
const behaviorSource = readFileSync(join(root, "src/main/java/com/vnap/dialogue/ContextualDialogueController.java"), "utf8");

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
for (const [id, group] of groups) {
  check(group.variants?.length, `Dialogue ${id} has no variants`);
  check(animations.groups[id]?.length === group.variants.length, `Dialogue ${id} has mismatched animation variants`);
  for (const variant of group.variants) {
    const event = sounds[`dialogue.${id}.${variant.index}`];
    check(event?.sounds?.length === 1, `Dialogue ${id}.${variant.index} must have one exact sound`);
    variantCount++;
    const sound = event.sounds[0];
    const name = typeof sound === "string" ? sound : sound.name;
    const relative = name.replace("villager-news-addon-port:", "");
    check(existsSync(join(modAssets, "sounds", `${relative}.ogg`)), `Missing audio file for ${name}`);
  }
}
check(variantCount === 2212, `Expected 2212 synchronized variants, found ${variantCount}`);
check(animations.gestures.length === 46, `Expected 46 dialogue gestures, found ${animations.gestures.length}`);

const referencedGroups = groups.filter(([id, group]) => behaviorSource.includes(`"${id}"`)
  || (group.title && behaviorSource.includes(`"${group.title}"`)));
const unreferencedGroups = groups.filter((entry) => !referencedGroups.includes(entry));
check(referencedGroups.length >= 490, `Expected at least 490 server-triggered dialogue groups, found ${referencedGroups.length}`);
check(unreferencedGroups.every(([, group]) => !group.title
  || /nose|cosmetic|hat|microphone|moustache|helmet/i.test(group.title)), "An ordinary gameplay dialogue still has no server trigger");
check(behaviorSource.includes("EntitySpawnReason.SPAWN_ITEM_USE"), "Spawn-egg dialogue does not use the server spawn reason");
check(behaviorSource.includes("maintainSpeechTargets"), "Server-side subject facing is missing");
check(existsSync(join(root, "src/main/java/com/vnap/mixin/AbstractVillagerMixin.java")), "Trade completion mixin is missing");

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
  check(animationText.includes("_leftleg.rx\":\"sin(limb_swing") && animationText.includes("_rightleg.rx\":\"sin(limb_swing"), `${file} does not walk from the upper-leg pivots`);
  check(!animationText.includes("limb_speed*(1-vnap_speaking)"), `${file} freezes its legs while dialogue is playing`);
  check(!animationText.includes("_jggl_leftleg.rx\":\"sin(limb_swing") && !animationText.includes("_jggl_rightleg.rx\":\"sin(limb_swing"), `${file} still walks from the foot pivots`);

  const head = base("headjgl2l6");
  const nose = base("fgk6");
  const arms = base("2jek");
  const bodywear = base("jg36");
  if (file === "villager2.jem") {
    const mayorExtra = all.find((entry) => entry.id === "mayor_extra_0_lghhat");
    check(head?.boxes?.some((box) => box.coordinates?.slice(3).includes(24)), "Mayor is not using the large baby base rig");
    check(mayorExtra?.boxes?.some((box) => box.coordinates?.slice(3).includes(18)), "Mayor hat geometry is missing");
    check(animationText.includes("0.33333*vnap_root_sx"), "Mayor base rig is not scaled to its Bedrock entity size");
    const extraRoot = model.models.find((entry) => entry.id === "mayor_extra_0_root");
    check(JSON.stringify(extraRoot?.animations ?? []).includes("0.33333*vnap_root_sx"), "Mayor hat does not share the base rig scale");
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

console.log(JSON.stringify({
  dialogueGroups: groups.length,
  soundEvents: Object.keys(sounds).length,
  synchronizedVariants: variantCount,
  dialogueGestures: animations.gestures.length,
  voiceFiles: readdirSync(join(modAssets, "sounds", "voice")).filter((name) => name.endsWith(".ogg")).length,
  cemModels: readdirSync(cem).filter((name) => name.endsWith(".jem")).length,
  serverTriggeredDialogueGroups: referencedGroups.length,
  cosmeticOnlyDialogueGroups: unreferencedGroups.length,
}, null, 2));
