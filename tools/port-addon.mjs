import { execFileSync } from "node:child_process";
import {
  copyFileSync,
  existsSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  rmSync,
  statSync,
  writeFileSync,
} from "node:fs";
import { dirname, join, relative, resolve, sep } from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";

const require = createRequire(import.meta.url);
const ts = require("typescript");

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const sourceRoot = join(projectRoot, "build", "bedrock-source");
const behaviorRoot = join(sourceRoot, "Villager News 1.0 Add-On BP");
const resourceRoot = join(sourceRoot, "Villager News 1.0 Add-On RP");
const outputRoot = join(projectRoot, "src", "main", "resources", "assets");
const modNamespace = "villager-news-addon-port";
const modAssets = join(outputRoot, modNamespace);
const minecraftAssets = join(outputRoot, "minecraft");

if (!outputRoot.startsWith(`${projectRoot}${sep}`)) {
  throw new Error(`Refusing to write outside the project: ${outputRoot}`);
}
if (!existsSync(resourceRoot) || !existsSync(behaviorRoot)) {
  throw new Error("Extract the Bedrock add-on into build/bedrock-source before running this script.");
}

function readJson(file) {
  return JSON.parse(readFileSync(file, "utf8"));
}

function writeJson(file, value) {
  mkdirSync(dirname(file), { recursive: true });
  writeFileSync(file, `${JSON.stringify(value, null, 2)}\n`);
}

function writeText(file, value) {
	mkdirSync(dirname(file), { recursive: true });
	writeFileSync(file, value);
}

const oggCrcTable = Array.from({ length: 256 }, (_, value) => {
  let remainder = value << 24;
  for (let bit = 0; bit < 8; bit++) {
    remainder = remainder & 0x80000000 ? (remainder << 1) ^ 0x04c11db7 : remainder << 1;
  }
  return remainder >>> 0;
});

function repairOggGranules(file) {
  const data = readFileSync(file);
  let offset = 0;
  let changed = false;
  while (offset + 27 <= data.length && data.toString("ascii", offset, offset + 4) === "OggS") {
    const segments = data[offset + 26];
    const headerSize = 27 + segments;
    let bodySize = 0;
    for (let index = 0; index < segments; index++) bodySize += data[offset + 27 + index];
    const pageSize = headerSize + bodySize;
    if (offset + pageSize > data.length) throw new Error(`Malformed OGG page in ${file}`);
    const granule = data.readBigUInt64LE(offset + 6);
    if (granule > 0xffffffffn && granule < 0x200000000n) {
      data.writeBigUInt64LE(granule & 0xffffffffn, offset + 6);
      data.writeUInt32LE(0, offset + 22);
      let crc = 0;
      for (let index = offset; index < offset + pageSize; index++) {
        crc = ((crc << 8) ^ oggCrcTable[((crc >>> 24) ^ data[index]) & 0xff]) >>> 0;
      }
      data.writeUInt32LE(crc, offset + 22);
      changed = true;
    }
    offset += pageSize;
  }
  if (changed) writeFileSync(file, data);
}

function readLang(file) {
  const entries = new Map();
  for (const sourceLine of readFileSync(file, "utf8").split(/\r?\n/)) {
    const line = sourceLine.replace(/^\uFEFF/, "");
    const separator = line.indexOf("=");
    if (separator < 1) continue;
    const key = line.slice(0, separator);
    let value = line.slice(separator + 1);
    const marker = value.lastIndexOf("\t#");
    if (marker >= 0) value = value.slice(0, marker);
    entries.set(key, value.trim());
  }
  return entries;
}

function walkFiles(root) {
  const files = [];
  for (const name of readdirSync(root)) {
    const file = join(root, name);
    if (statSync(file).isDirectory()) files.push(...walkFiles(file));
    else files.push(file);
  }
  return files;
}

function cleanGeneratedDirectory(directory) {
  const resolved = resolve(directory);
  if (!resolved.startsWith(`${projectRoot}${sep}`)) {
    throw new Error(`Refusing to remove a directory outside the project: ${resolved}`);
  }
  rmSync(resolved, { recursive: true, force: true });
  mkdirSync(resolved, { recursive: true });
}

const preservedSilence = existsSync(join(modAssets, "sounds", "silence.ogg"))
  ? readFileSync(join(modAssets, "sounds", "silence.ogg")) : undefined;

for (const generated of [
  join(modAssets, "textures", "entity"),
  join(modAssets, "sounds"),
  join(minecraftAssets, "optifine", "cem"),
  join(minecraftAssets, "esf"),
  join(minecraftAssets, "textures", "entity", "villager"),
]) {
  cleanGeneratedDirectory(generated);
}

const geometryById = new Map();
for (const file of walkFiles(join(resourceRoot, "models", "entity")).filter((path) => path.endsWith(".json"))) {
  const json = readJson(file);
  for (const geometry of json["minecraft:geometry"] ?? []) {
    geometryById.set(geometry.description.identifier, geometry);
  }
}

const animationById = new Map();
for (const file of walkFiles(join(resourceRoot, "animations")).filter((path) => path.endsWith(".json"))) {
  const json = readJson(file);
  for (const [animationId, animation] of Object.entries(json.animations ?? {})) {
    animationById.set(animationId, animation);
  }
}

const faceNames = {
  north: "uvNorth",
  south: "uvSouth",
  east: "uvEast",
  west: "uvWest",
  up: "uvUp",
  down: "uvDown",
};
const axes = ["x", "y", "z"];

function cleanNumber(value) {
  if (Math.abs(value) < 0.000001) return 0;
  return Number(value.toFixed(5));
}

function vector(value = [0, 0, 0]) {
  return value.map(cleanNumber);
}

function subtract(left, right) {
  return vector(left.map((value, index) => value - right[index]));
}

function importedPivot(pivot = [0, 0, 0]) {
  return vector([-pivot[0], pivot[1], pivot[2]]);
}

function importedRotation(rotation = [0, 0, 0]) {
  return vector([-rotation[0], -rotation[1], rotation[2]]);
}

function importedCubeFrom(cube) {
  return vector([-(cube.origin[0] + cube.size[0]), cube.origin[1], cube.origin[2]]);
}

function importedChildOffset(child, parent) {
  return vector([
    child[0] - parent[0],
    child[1] - parent[1],
    child[2] - parent[2],
  ]);
}

function safeId(value) {
  return value.toLowerCase().replace(/[^a-z0-9_]/g, "_");
}

function convertUv(cube, box) {
  if (Array.isArray(cube.uv)) {
    box.textureOffset = vector(cube.uv);
    return;
  }
  for (const [face, emfName] of Object.entries(faceNames)) {
    const sourceFace = cube.uv?.[face];
    if (sourceFace?.uv && sourceFace?.uv_size) {
      const [u, v] = sourceFace.uv;
      const [width, height] = sourceFace.uv_size;
      box[emfName] = vector([u, v, u + width, v + height]);
    }
  }
}

function makeBox(cube, localOrigin, boneName) {
  const box = {
    coordinates: vector([localOrigin[0], localOrigin[1], localOrigin[2], ...cube.size]),
  };
  if (cube.inflate) box.sizeAdd = cleanNumber(cube.inflate);
  convertUv(cube, box);
  return box;
}

function convertBone(bone, bonesByParent, prefix, parentOrigin, inheritedMirror = false, depth = 0) {
  const origin = importedPivot(bone.pivot);
  const translate = depth <= 1 ? origin : importedChildOffset(origin, parentOrigin);
  const model = {
    id: `${prefix}_${safeId(bone.name)}`,
    invertAxis: "xy",
    translate,
  };
  const rotation = importedRotation(bone.rotation);
  if (rotation.some(Boolean)) model.rotate = rotation;

  const boneMirror = bone.mirror ?? inheritedMirror;
  if (boneMirror) model.mirrorTexture = "u";

  for (const [cubeIndex, cube] of (bone.cubes ?? []).entries()) {
    if (!cube.origin || !cube.size) continue;
    const cubeFrom = importedCubeFrom(cube);
    const cubeMirror = cube.mirror ?? boneMirror;
    if (cube.rotation || cube.pivot || cubeMirror !== boneMirror) {
      const cubeOrigin = importedPivot(cube.pivot ?? bone.pivot);
      const cubeTranslate = importedChildOffset(cubeOrigin, origin);
      const cubeModel = {
        id: `${prefix}_${safeId(bone.name)}_cube_${cubeIndex}`,
        invertAxis: "xy",
        translate: cubeTranslate,
        boxes: [makeBox(cube, subtract(cubeFrom, cubeOrigin), bone.name)],
      };
      const cubeRotation = importedRotation(cube.rotation);
      if (cubeRotation.some(Boolean)) cubeModel.rotate = cubeRotation;
      if (cubeMirror) cubeModel.mirrorTexture = "u";
      (model.submodels ??= []).push(cubeModel);
    } else {
      const boxOrigin = subtract(cubeFrom, origin);
      (model.boxes ??= []).push(makeBox(cube, boxOrigin, bone.name));
    }
  }

  for (const child of bonesByParent.get(bone.name) ?? []) {
    (model.submodels ??= []).push(convertBone(child, bonesByParent, prefix, origin, boneMirror, depth + 1));
  }
  return model;
}

function animationsFor(bones, prefix) {
  const names = new Set(bones.map((bone) => bone.name));
  const animations = [];
  const add = (name, axis, expression) => {
    if (names.has(name)) animations.push({ [`${prefix}_${safeId(name)}.${axis}`]: expression });
  };

  for (const head of ["head", "k966h_head"]) {
    add(head, "rx", "torad(head_pitch)");
    add(head, "ry", "torad(head_yaw)");
  }
  add("jggl_leftleg", "rx", "sin(limb_swing * 0.6662) * 1.4 * limb_speed");
  add("jggl_rightleg", "rx", "sin(limb_swing * 0.6662 + pi) * 1.4 * limb_speed");
  add("root_d680", "rx", "sin(limb_swing * 0.6662) * 1.4 * limb_speed");
  add("root_d681", "rx", "sin(limb_swing * 0.6662 + pi) * 1.4 * limb_speed");
  add("root_d682", "rx", "sin(limb_swing * 0.6662 + pi) * 1.4 * limb_speed");
  add("root_d683", "rx", "sin(limb_swing * 0.6662) * 1.4 * limb_speed");
  return animations;
}

function convertGeometry(geometryId, { prefix, texture, attach, includeVanillaAnimations = true }) {
  const geometry = geometryById.get(geometryId);
  if (!geometry) throw new Error(`Missing geometry ${geometryId}`);
  const bones = geometry.bones ?? [];
  const bonesByParent = new Map();
  for (const bone of bones) {
    if (bone.parent) {
      const children = bonesByParent.get(bone.parent) ?? [];
      children.push(bone);
      bonesByParent.set(bone.parent, children);
    }
  }
  const roots = bones.filter((bone) => !bone.parent);
  return roots.map((root, rootIndex) => {
    const origin = importedPivot(root.pivot);
    const model = convertBone(root, bonesByParent, prefix, vector([0, 0, 0]));
    model.part = "root";
    model.attach = attach || rootIndex > 0;
    model.translate = vector(origin.map((value) => -value));
    model.textureSize = [geometry.description.texture_width ?? 64, geometry.description.texture_height ?? 64];
    if (texture) model.texture = texture.includes(":") ? texture : `${modNamespace}:textures/entity/${texture}.png`;
    if (includeVanillaAnimations) {
      const animations = animationsFor(bones, prefix);
      if (animations.length) model.animations = animations;
    }
    return model;
  });
}

function makeJem(layers) {
  const models = [];
  for (const [index, layer] of layers.entries()) {
    models.push(...convertGeometry(layer.geometry, {
      prefix: `${layer.prefix}_${index}`,
      texture: layer.texture,
      attach: index > 0,
    }));
  }
  return { textureSize: [64, 64], models };
}

const commonVillager = "geometry.oreville_vn.-754165646";
function geometryParts(geometryId) {
  const geometry = geometryById.get(geometryId);
  if (!geometry) throw new Error(`Missing geometry ${geometryId}`);
  const bones = geometry.bones ?? [];
  const bonesByName = new Map(bones.map((bone) => [bone.name, bone]));
  const bonesByParent = new Map();
  for (const bone of bones) {
    if (!bone.parent) continue;
    const children = bonesByParent.get(bone.parent) ?? [];
    children.push(bone);
    bonesByParent.set(bone.parent, children);
  }
  return { geometry, bonesByName, bonesByParent };
}

function addPartCube(model, bone, cube, prefix, cubeIndex, ignoreCubeRotation = false) {
  if (!cube.origin || !cube.size) return;
  const cubeFrom = importedCubeFrom(cube);
  const boneMirror = bone.mirror ?? false;
  const cubeMirror = cube.mirror ?? boneMirror;
  if (!ignoreCubeRotation && (cube.rotation || cube.pivot)) {
    const cubeOrigin = importedPivot(cube.pivot ?? bone.pivot);
    const cubeModel = {
      id: `${prefix}_${safeId(bone.name)}_cube_${cubeIndex}`,
      invertAxis: "xy",
      translate: cubeOrigin,
      boxes: [makeBox(cube, subtract(cubeFrom, cubeOrigin))],
    };
    const cubeRotation = importedRotation(cube.rotation);
    if (cubeRotation.some(Boolean)) cubeModel.rotate = cubeRotation;
    if (cubeMirror) cubeModel.mirrorTexture = "u";
    (model.submodels ??= []).push(cubeModel);
  } else if (cubeMirror !== boneMirror) {
    let mirrorModel = model.submodels?.find((submodel) => submodel.id === `${prefix}_mirrored`);
    if (!mirrorModel) {
      mirrorModel = { id: `${prefix}_mirrored`, invertAxis: "xy", translate: [0, 0, 0], boxes: [] };
      if (cubeMirror) mirrorModel.mirrorTexture = "u";
      (model.submodels ??= []).unshift(mirrorModel);
    }
    mirrorModel.boxes.push(makeBox(cube, cubeFrom));
  } else {
    (model.boxes ??= []).push(makeBox(cube, cubeFrom));
  }
}

function partFromBone(geometryId, {
  boneName,
  part,
  prefix,
  texture,
  translate,
  attach = false,
  includeOwnCubes = true,
  childNames,
  excludeChildren = [],
  ignoreCubeRotation = false,
  animations,
}) {
  const { geometry, bonesByName, bonesByParent } = geometryParts(geometryId);
  const bone = bonesByName.get(boneName);
  if (!bone) throw new Error(`Missing bone ${boneName} in ${geometryId}`);
  const origin = importedPivot(bone.pivot);
  const model = {
    part,
    id: `${prefix}_${safeId(part)}`,
    invertAxis: "xy",
    translate: translate ?? vector(origin.map((value) => -value)),
    attach,
    textureSize: [geometry.description.texture_width ?? 64, geometry.description.texture_height ?? 64],
  };
  if (texture) model.texture = `${modNamespace}:textures/entity/${texture}.png`;
  const rotation = importedRotation(bone.rotation);
  if (rotation.some(Boolean)) model.rotate = rotation;
  if (bone.mirror) model.mirrorTexture = "u";
  if (includeOwnCubes) {
    for (const [cubeIndex, cube] of (bone.cubes ?? []).entries()) {
      addPartCube(model, bone, cube, prefix, cubeIndex, ignoreCubeRotation);
    }
  }
  const excluded = new Set(excludeChildren);
  const selected = childNames ? new Set(childNames) : null;
  for (const child of bonesByParent.get(bone.name) ?? []) {
    if (excluded.has(child.name) || selected && !selected.has(child.name)) continue;
    (model.submodels ??= []).push(convertBone(child, bonesByParent, prefix, origin, bone.mirror ?? false, 1));
  }
  if (animations?.length) model.animations = animations;
  return model;
}

function partFromCube(geometryId, { boneName, cubeIndex, part, prefix, texture, translate, attach = false, resetX = false }) {
  const { geometry, bonesByName } = geometryParts(geometryId);
  const bone = bonesByName.get(boneName);
  const cube = bone?.cubes?.[cubeIndex];
  if (!bone || !cube) throw new Error(`Missing cube ${boneName}[${cubeIndex}] in ${geometryId}`);
  const model = {
    part,
    id: `${prefix}_${safeId(part)}`,
    invertAxis: "xy",
    translate,
    attach,
    textureSize: [geometry.description.texture_width ?? 64, geometry.description.texture_height ?? 64],
    boxes: [makeBox(cube, importedCubeFrom(cube))],
  };
  if (texture) model.texture = `${modNamespace}:textures/entity/${texture}.png`;
  const rotation = importedRotation(cube.rotation);
  if (rotation.some(Boolean)) model.rotate = rotation;
  if (cube.mirror ?? bone.mirror) model.mirrorTexture = "u";
  if (resetX) model.animations = [{ "this.rx": "0" }];
  return model;
}

function findModelById(models, id) {
  for (const model of models) {
    if (model.id === id) return model;
    const nested = findModelById(model.submodels ?? [], id);
    if (nested) return nested;
  }
  return undefined;
}

function appendAnimation(model, expressions) {
  (model.animations ??= []).push(expressions);
}

function numberExpression(base, variable) {
  if (!base) return variable;
  if (typeof base === "string") return `${base}+${variable}`;
  return `${cleanNumber(base)}+${variable}`;
}

function preparedTranslate(translate = [0, 0, 0]) {
  return vector([-translate[0], -translate[1], translate[2]]);
}

function preparedRotation(rotation = [0, 0, 0]) {
  return vector([-rotation[0], -rotation[1], rotation[2]]);
}

function animationExpression(target, inheritedTargets, kind, suffix) {
  const targets = [...(inheritedTargets ?? []), target];
  const operator = kind === "s" ? "*" : "+";
  return targets.map((name) => `vnap_${name}_${kind}${suffix}`).join(operator);
}

function animateAnchor(model, target, inheritedTargets = []) {
  const translate = preparedTranslate(model.translate);
  const expressions = {};
  for (let axis = 0; axis < 3; axis++) {
    const suffix = axes[axis];
    const rotationAnimation = animationExpression(target, inheritedTargets, "r", suffix);
    const translationAnimation = animationExpression(target, inheritedTargets, "t", suffix);
    const scaleAnimation = animationExpression(target, inheritedTargets, "s", suffix);
    expressions[`this.r${suffix}`] = rotationAnimation;
    expressions[`this.t${suffix}`] = numberExpression(translate[axis], translationAnimation);
    expressions[`this.s${suffix}`] = scaleAnimation;
  }
  appendAnimation(model, expressions);
}

function animatedAnchor(model, target, inheritedTargets = []) {
  animateAnchor(model, target, inheritedTargets);
  return model;
}

function animateNestedBone(models, prefix, boneName, target) {
  const model = findModelById(models, `${prefix}_${safeId(boneName)}`);
  if (!model) return;
  const translate = preparedTranslate(model.translate);
  const rotate = preparedRotation(model.rotate).map((degrees) => degrees * Math.PI / 180);
  const expressions = {};
  for (let axis = 0; axis < 3; axis++) {
    const suffix = axes[axis];
    expressions[`this.r${suffix}`] = numberExpression(rotate[axis], `vnap_${target}_r${suffix}`);
    expressions[`this.t${suffix}`] = numberExpression(translate[axis], `vnap_${target}_t${suffix}`);
    expressions[`this.s${suffix}`] = `vnap_${target}_s${suffix}`;
  }
  appendAnimation(model, expressions);
}

function addVillagerAnimations(models, prefix) {
  for (const [part, target, inheritedTargets] of [
    ["head", "head", ["body"]],
    ["nose", "nose", ["body", "head"]],
    ["headwear", "head", ["body"]],
    ["headwear2", "head", ["body"]],
    ["body", "body"],
    ["bodywear", "body"],
    ["arms", "arms", ["body"]],
    ["left_leg", "left_leg"],
    ["right_leg", "right_leg"],
  ]) {
    const model = findModelById(models, `${prefix}_${part}`);
    if (model) animateAnchor(model, target, inheritedTargets);
  }

  for (const [boneName, target] of [
    ["egfg3jgo", "brow"],
    ["6q6da5kmhh6j", "eye_group"],
    ["6q6da5kdgo6j", "lower_face"],
    ["ja89l_6q6", "pupil_left"],
    ["d67l_6q6", "pupil_right"],
    ["6q6da5kmhh6jja89l", "eye_left"],
    ["6q6da5kmhh6jd67l", "eye_right"],
    ["fgk6", "nose"],
  ]) animateNestedBone(models, prefix, boneName, target);

  const mouth = findModelById(models, `${prefix}_egml9`);
  if (mouth) appendAnimation(mouth, {
    "this.sx": "(0.75+vnap_mouth_width*0.25)*vnap_speaking+(1-vnap_speaking)",
    "this.sy": "(1+vnap_mouth_open*1.5)*vnap_speaking+(1-vnap_speaking)",
  });
  for (const [boneName, direction] of [["l66l9lgh", -1], ["l66l93gllge", 1]]) {
    const lip = findModelById(models, `${prefix}_${boneName}`);
    if (!lip) continue;
    const baseY = preparedTranslate(lip.translate)[1];
    appendAnimation(lip, {
      "this.ty": `${cleanNumber(baseY)}+vnap_speaking*vnap_mouth_open*${direction * 0.75}`,
      "this.sx": "(0.75+vnap_mouth_width*0.25-vnap_mouth_closed)*vnap_speaking",
      "this.sy": "(1-vnap_mouth_closed)*vnap_speaking",
      "this.sz": "(1-vnap_mouth_closed)*vnap_speaking",
    });
  }

  const blink = "if(fmod(time+id*0.37,95+fmod(id,37))>90,0.08,1)";
  for (const boneName of ["6q6da5kmhh6jja89l", "6q6da5kmhh6jd67l", "ja89l_6q6", "d67l_6q6"]) {
    const eye = findModelById(models, `${prefix}_${boneName}`);
    if (eye) appendAnimation(eye, { "this.sy": `${blink}*vnap_${boneName === "ja89l_6q6" ? "pupil_left" : boneName === "d67l_6q6" ? "pupil_right" : boneName.endsWith("ja89l") ? "eye_left" : "eye_right"}_sy` });
  }
}

function commonVillagerModels(prefix, texture) {
  const options = (values) => ({ ...values, prefix, texture });
  const models = [
    partFromBone(commonVillager, options({
      boneName: "headjgl2l6", part: "head", translate: [0, -24, 0], excludeChildren: ["fgk6", "hat"],
    })),
    partFromBone(commonVillager, options({
      boneName: "fgk6", part: "nose", translate: [0, -26, 0],
    })),
    partFromCube(commonVillager, options({
      boneName: "hat", cubeIndex: 0, part: "headwear", translate: [0, -24, 0],
    })),
    partFromCube(commonVillager, options({
      boneName: "hat", cubeIndex: 1, part: "headwear2", translate: [0, -24, 0], resetX: true,
    })),
    partFromBone(commonVillager, options({
      boneName: "body", part: "body", translate: [0, -24, 0], childNames: [],
    })),
    partFromBone(commonVillager, options({
      boneName: "jg36", part: "bodywear", translate: [0, -24, 0],
    })),
    partFromBone(commonVillager, options({
      boneName: "2jek", part: "arms", translate: [0, -21, 1],
    })),
    partFromBone(commonVillager, options({
      boneName: "rightleg", part: "right_leg", translate: [-2, -12, 0], childNames: [], ignoreCubeRotation: true,
    })),
    partFromBone(commonVillager, options({
      boneName: "leftleg", part: "left_leg", translate: [2, -12, 0], childNames: [], ignoreCubeRotation: true,
    })),
  ];
  addVillagerAnimations(models, prefix);
  return models;
}

const villagerRigTargets = {
  root: ["root"],
  waist: ["waist"],
  body: ["body"],
  head: ["head"],
  head_inner: ["headjgl2l6"],
  arms: ["2jek"],
  left_leg_root: ["jggl_leftleg"],
  left_leg: ["leftleg"],
  right_leg_root: ["jggl_rightleg"],
  right_leg: ["rightleg"],
  brow: ["egfg3jgo"],
  eye_group: ["6q6da5kmhh6j"],
  lower_face: ["6q6da5kdgo6j"],
  pupil_left: ["ja89l_6q6"],
  pupil_right: ["d67l_6q6"],
  eye_left: ["6q6da5kmhh6jja89l"],
  eye_right: ["6q6da5kmhh6jd67l"],
  nose: ["fgk6"],
};

function addRigBoneAnimation(models, prefix, boneName, target, rigScale = 1) {
  const model = findModelById(models, `${prefix}_${safeId(boneName)}`);
  if (!model) return;
  const translate = preparedTranslate(model.translate);
  const rotate = preparedRotation(model.rotate).map((degrees) => degrees * Math.PI / 180);
  const expressions = {};
  for (let axis = 0; axis < 3; axis++) {
    const suffix = axes[axis];
    const rotationTerms = [];
		const translationTerms = [`vnap_${target}_t${suffix}`];
		if (boneName === "head" && axis === 0) rotationTerms.push("torad(head_pitch*0.5)");
		if (boneName === "head" && axis === 1) rotationTerms.push("torad(head_yaw*0.77)");
		if (boneName === "waist" && axis === 0) rotationTerms.push("torad(head_pitch*0.17)");
		if (boneName === "waist" && axis === 1) rotationTerms.push("torad(head_yaw*0.3)");
		if (target === "pupil_left" && axis === 0) translationTerms.push("if(head_yaw>30,-1,0)");
		if (target === "pupil_right" && axis === 0) translationTerms.push("if(head_yaw<-30,1,0)");
		if ((target === "pupil_left" || target === "pupil_right") && axis === 1) translationTerms.push("if(head_pitch<-30,-1,0)");
		if (target === "brow" && axis === 1) translationTerms.push("if(head_pitch<-30,-1,if(head_pitch>30,0,if(head_yaw>30,-0.25,if(head_yaw<-30,-0.25,0))))");
		rotationTerms.push(`vnap_${target}_r${suffix}`);
    expressions[`this.r${suffix}`] = numberExpression(rotate[axis], rotationTerms.join("+"));
		expressions[`this.t${suffix}`] = numberExpression(translate[axis], translationTerms.join("+"));
    const scaleExpression = `vnap_${target}_s${suffix}`;
    expressions[`this.s${suffix}`] = boneName === "root" && rigScale !== 1
      ? `${cleanNumber(rigScale)}*${scaleExpression}`
      : scaleExpression;
  }
  appendAnimation(model, expressions);
}

function addRootVillagerAnimations(models, prefix, rigScale = 1) {
  for (const [target, sourceBones] of Object.entries(villagerRigTargets)) {
    for (const boneName of sourceBones) addRigBoneAnimation(models, prefix, boneName, target, rigScale);
  }

  const mouth = findModelById(models, `${prefix}_egml9`);
  if (mouth) appendAnimation(mouth, {
    "this.sx": "(0.75+vnap_mouth_width*0.25)*vnap_speaking+(1-vnap_speaking)",
    "this.sy": "(1+vnap_mouth_open*1.5)*vnap_speaking+(1-vnap_speaking)",
    "this.sz": "1",
  });
  const toothTravel = rigScale === 1 ? 0.75 : 1.5;
  for (const [boneName, direction] of [["l66l9lgh", -1], ["l66l93gllge", 1]]) {
    const lip = findModelById(models, `${prefix}_${boneName}`);
    if (!lip) continue;
    const baseY = preparedTranslate(lip.translate)[1];
    appendAnimation(lip, {
      "this.ty": `${cleanNumber(baseY)}+vnap_speaking*vnap_mouth_open*${direction * toothTravel}`,
      "this.sx": "(0.75+vnap_mouth_width*0.25-vnap_mouth_closed)*vnap_speaking",
      "this.sy": "(1-vnap_mouth_closed)*vnap_speaking",
      "this.sz": "(1-vnap_mouth_closed)*vnap_speaking",
    });
  }

  const blinkTime = "fmod(time+id*0.37,1.25+fmod(id,3)*0.5)";
	const blink = `if(${blinkTime}<0.08,1+12.75*${blinkTime},if(${blinkTime}<0.18,2.02,if(${blinkTime}<0.26,2.02-12.75*(${blinkTime}-0.18),1)))`;
	const lookEyeScale = "if(head_pitch<-30,0,if(head_yaw>30,0.75,if(head_yaw<-30,0.75,1)))";
  for (const [boneName, target] of [
    ["6q6da5kmhh6j", "eye_group"],
    ["6q6da5kdgo6j", "lower_face"],
  ]) {
		const eye = findModelById(models, `${prefix}_${boneName}`);
		if (eye) appendAnimation(eye, { "this.sy": `${blink}*${target === "eye_group" ? `${lookEyeScale}*` : ""}vnap_${target}_sy` });
  }
}

function villagerLayer(geometry, prefix, texture, attach, rigScale = 1) {
  const models = convertGeometry(geometry, {
    prefix,
    texture,
    attach,
    includeVanillaAnimations: false,
  });
  for (const model of models) model.translate = [0, -24, 0];
  addRootVillagerAnimations(models, prefix, rigScale);
  return models;
}

function hoistNestedAnimations(rootModel) {
  const hoisted = [...(rootModel.animations ?? [])];
  const visit = (models) => {
    for (const model of models ?? []) {
      for (const animation of model.animations ?? []) {
        const targeted = {};
        for (const [variable, expression] of Object.entries(animation)) {
          targeted[variable.startsWith("this.") ? `${model.id}.${variable.slice(5)}` : variable] = expression;
        }
        hoisted.push(targeted);
      }
      delete model.animations;
      visit(model.submodels);
    }
  };
  visit(rootModel.submodels);
  rootModel.animations = hoisted;
}

function vanillaVillagerSuppressors(prefix) {
  return ["head", "nose", "headwear", "headwear2", "body", "bodywear", "arms", "right_leg", "left_leg"]
    .map((part) => ({ part, id: `${prefix}_hide_${part}`, attach: false }));
}

function rootVillagerModels(prefix, texture, extras = [], {
  baseGeometry = commonVillager,
  rigScale = 1,
} = {}) {
  const models = [...villagerLayer(baseGeometry, `${prefix}_base`, texture, false, rigScale)];
  const arms = findModelById(models, `${prefix}_base_2jek`);
  if (!arms) throw new Error(`${prefix} animated arms bone is missing`);
  arms.attachments = { villager_item: [0, -5.75, -1.75] };
  for (const [index, extra] of extras.entries()) {
    const layer = villagerLayer(extra.geometry, `${prefix}_extra_${index}`, extra.texture, true, rigScale);
    if (extra.visibility) {
      const scale = rigScale === 1 ? extra.visibility : `${cleanNumber(rigScale)}*${extra.visibility}`;
      for (const model of layer) appendAnimation(model, {
        "this.sx": scale,
        "this.sy": scale,
        "this.sz": scale,
      });
    }
    if (extra.scaleXZ !== undefined) {
      const scaledBone = findModelById(layer, `${prefix}_extra_${index}_lghhat`);
      if (!scaledBone) throw new Error(`${prefix} cosmetic hat bone is missing`);
      if (extra.drop !== undefined) {
        for (const box of scaledBone.boxes ?? []) {
          box.coordinates[1] = cleanNumber(box.coordinates[1] - extra.drop);
        }
      }
      appendAnimation(scaledBone, {
        "this.sx": cleanNumber(extra.scaleXZ),
        "this.sz": cleanNumber(extra.scaleXZ),
      });
    }
    if (extra.monocleInset !== undefined) {
      const monocle = findModelById(layer, `${prefix}_extra_${index}_egfg4d6`);
      if (!monocle) throw new Error(`${prefix} cosmetic monocle bone is missing`);
      monocle.translate = vector(monocle.translate);
      monocle.translate[2] = cleanNumber(monocle.translate[2] + extra.monocleInset);
    }
    if (extra.beltInflate !== undefined) {
      const belt = findModelById(layer, `${prefix}_extra_${index}_l6kla7a42l636dl`);
      if (!belt?.boxes?.length) throw new Error(`${prefix} cosmetic belt bone is missing`);
      belt.boxes[0].sizeAdd = cleanNumber((belt.boxes[0].sizeAdd ?? 0) + extra.beltInflate);
    }
    models.push(...layer);
  }
  if (prefix === "villager_news") {
    const headwear = findModelById(models, `${prefix}_base_hat`);
    if (!headwear) throw new Error("Villager headwear bone is missing");
    appendAnimation(headwear, {
      "this.visible": "vnap_cosmetic_mayor_hat==0&&vnap_cosmetic_helmet==0",
    });
  }
  if (prefix !== "wandering_trader_news") {
    const nose = findModelById(models, `${prefix}_base_fgk6`);
    if (nose) appendAnimation(nose, {
      "this.sx": "vnap_nose_sx*vnap_has_nose",
      "this.sy": "vnap_nose_sy*vnap_has_nose",
      "this.sz": "vnap_nose_sz*vnap_has_nose",
    });
  }
  for (const model of models) {
    model.attach = true;
    hoistNestedAnimations(model);
  }
  return [...models, ...vanillaVillagerSuppressors(prefix)];
}

function vanillaSheepSuppressors(prefix) {
  return ["head", "body", "leg1", "leg2", "leg3", "leg4"]
    .map((part) => ({ part, id: `${prefix}_hide_${part}`, attach: false }));
}

function addWoolyAnimations(models, prefix) {
  const root = models.find((model) => model.id === `${prefix}_root`);
  if (!root) throw new Error("Wooly's converted root is missing");
  const find = (bone) => findModelById(models, `${prefix}_${safeId(bone)}`);
  const target = (bone, property) => `${prefix}_${safeId(bone)}.${property}`;

  const head = find("k966h_head");
  const body = find("body");
  const bodyMesh = find("46fljga5");
  if (!head || !body || !bodyMesh) throw new Error("Wooly's head or body bones are missing");

  const headRest = preparedTranslate(head.translate);
  const bodyMeshRest = preparedTranslate(bodyMesh.translate);
  const bodyMeshRotation = preparedRotation(bodyMesh.rotate).map((degrees) => degrees * Math.PI / 180);
  const phase = "limb_swing*0.6662";

  for (const [bone, phaseOffset] of [
    ["d680", ""], ["d681", "+pi"], ["d682", "+pi"], ["d683", ""],
  ]) {
    const leg = find(bone);
    if (!leg) throw new Error(`Wooly's ${bone} leg bone is missing`);
    appendAnimation(root, {
      [target(bone, "rx")]: `sin(${phase}${phaseOffset})*1.4*limb_speed`,
    });
  }

  appendAnimation(root, {
    [target("46fljga5", "rx")]: `${cleanNumber(bodyMeshRotation[0])}+torad(cos(${phase})*6.2)*limb_speed`,
    [target("46fljga5", "ty")]: `${cleanNumber(bodyMeshRest[1])}+(1.5-sin(${phase})*1.125)*limb_speed`,
    [target("k966h_head", "rx")]: `torad(head_pitch)+torad((sin(${phase}+torad(22))*6.2+1)*limb_speed)`,
    [target("k966h_head", "ry")]: "torad(head_yaw)",
    [target("k966h_head", "ty")]: `${cleanNumber(headRest[1])}+(1.5+cos(${phase}+torad(30))*1.125)*limb_speed`,
  });

  const blink = "if(fmod(time+id*3.46,36.6666)>33.334,1,0)";
  appendAnimation(root, {
    [target("3dafc", "sx")]: blink,
    [target("3dafc", "sy")]: blink,
    [target("3dafc", "sz")]: blink,
  });

  const showFleece = "!nbt(Sheared,1)";
  for (const bone of ["oggd_46fljga5", "oggd_head", "oggd_d680", "oggd_d681", "oggd_d682", "oggd_d683"]) {
    if (!find(bone)) throw new Error(`Wooly's ${bone} fleece bone is missing`);
    appendAnimation(root, { [target(bone, "visible")]: showFleece });
  }

  const openMouth = find("egml9");
  const closedMouth = find("l66l9");
  const upperLip = find("l66l9lgh");
  const lowerLip = find("l66l93gllge");
  if (!openMouth || !closedMouth || !upperLip || !lowerLip) {
    throw new Error("Wooly's mouth bones are missing");
  }
  openMouth.translate = vector(openMouth.translate);
  openMouth.translate[2] = cleanNumber(openMouth.translate[2] - 0.075);
  closedMouth.translate = vector(closedMouth.translate);
  closedMouth.translate[2] = cleanNumber(closedMouth.translate[2] - 0.1);

  const mouthStripUv = [12, 10, 13, 11];
  for (const lip of [upperLip, lowerLip]) {
    for (const box of lip.boxes ?? []) {
      for (const emfName of Object.values(faceNames)) box[emfName] = mouthStripUv;
    }
  }
  const openRest = preparedTranslate(openMouth.translate);
  const closedRest = preparedTranslate(closedMouth.translate);
  const upperRest = preparedTranslate(upperLip.translate);
  const lowerRest = preparedTranslate(lowerLip.translate);
  appendAnimation(root, {
    [target("egml9", "ty")]: `${cleanNumber(openRest[1])}+vnap_speaking*vnap_mouth_open*0.5`,
    [target("egml9", "sx")]: `(0.5+vnap_mouth_width*0.5)*vnap_speaking+(1-vnap_speaking)`,
    [target("egml9", "sy")]: `(1+vnap_mouth_open)*vnap_speaking+(1-vnap_speaking)`,
    [target("l66l9", "ty")]: `${cleanNumber(closedRest[1])}+vnap_speaking*vnap_mouth_open*0.5`,
    [target("l66l9", "sx")]: `(0.5+vnap_mouth_width*0.5-vnap_mouth_closed)*vnap_speaking`,
    [target("l66l9", "sy")]: `(1-vnap_mouth_closed)*vnap_speaking`,
    [target("l66l9", "sz")]: `(1-vnap_mouth_closed)*vnap_speaking`,
    [target("l66l9lgh", "ty")]: `${cleanNumber(upperRest[1])}-vnap_speaking*vnap_mouth_open*0.29`,
    [target("l66l93gllge", "ty")]: `${cleanNumber(lowerRest[1])}+vnap_speaking*vnap_mouth_open*0.29`,
  });
}

function rootSheepModels(prefix, texture, sheared = false) {
  const models = convertGeometry("geometry.oreville_vn.-650401518", {
    prefix: `${prefix}_base`,
    texture,
    attach: true,
    includeVanillaAnimations: false,
  });
  for (const model of models) {
    model.translate = [0, -24, 0];
    model.attach = true;
  }

  const woolyPrefix = `${prefix}_base`;
  const head = findModelById(models, `${woolyPrefix}_k966h_head`);
  const face = findModelById(models, `${woolyPrefix}_7246gn6jd2q`);
  const headBox = head?.boxes?.[0];
  if (!headBox || !face) throw new Error("Wooly's head or face geometry is missing");
  headBox.uvNorth = [8, 8, 14, 14];
  face.translate = preparedTranslate(face.translate);
  face.translate[2] -= 0.025;
  for (const box of face.boxes ?? []) {
    if (box.coordinates?.[5] === 0) {
      box.coordinates[2] = cleanNumber(box.coordinates[2] - 0.025);
      box.coordinates[5] = 0.05;
    }
  }

  addWoolyAnimations(models, woolyPrefix);
  if (sheared) {
    const clearFleeceCubes = (entries) => {
      for (const entry of entries) {
        if (entry.id?.startsWith(`${woolyPrefix}_oggd`)) delete entry.boxes;
        clearFleeceCubes(entry.submodels ?? []);
      }
    };
    clearFleeceCubes(models);
  }
  return [...models, ...vanillaSheepSuppressors(prefix)];
}

const modelDefinitions = {
  "villager.jem": { models: rootVillagerModels("villager_news", undefined, [
    { geometry: "geometry.oreville_vn.131968402", texture: "dtd", visibility: "vnap_cosmetic_mayor_hat", scaleXZ: 0.9, drop: 1.7, monocleInset: 1.75 },
    { geometry: "geometry.oreville_vn.1221980082", texture: "djn", visibility: "vnap_cosmetic_helmet", beltInflate: 0.0625 },
    { geometry: "geometry.oreville_vn.1878756082", texture: "dta", visibility: "vnap_cosmetic_microphone" },
    { geometry: "geometry.oreville_vn.208670578", texture: "djh", visibility: "vnap_cosmetic_moustache" },
  ]) },
  "villager_baby.jem": { models: rootVillagerModels("villager_news_baby", undefined, [
    { geometry: "geometry.oreville_vn.292718674", texture: "dtd", visibility: "vnap_cosmetic_mayor_hat" },
    { geometry: "geometry.oreville_vn.1221980082", texture: "djn", visibility: "vnap_cosmetic_helmet", beltInflate: 0.0625 },
    { geometry: "geometry.oreville_vn.1878756082", texture: "dta", visibility: "vnap_cosmetic_microphone" },
    { geometry: "geometry.oreville_vn.208670578", texture: "djh", visibility: "vnap_cosmetic_moustache" },
  ], {
    baseGeometry: "geometry.oreville_vn.-1769484142",
    rigScale: 1 / 3,
  }) },
  "villager2.jem": { models: rootVillagerModels("mayor", "mayor", [
    { geometry: "geometry.oreville_vn.292718674", texture: "dtd" },
  ], {
    baseGeometry: "geometry.oreville_vn.-1769484142",
    rigScale: 1 / 3,
  }) },
  "villager3.jem": { models: rootVillagerModels("testificate", "testificate_man", [
    { geometry: "geometry.oreville_vn.1221980082", texture: "djn" },
  ]) },
  "villager4.jem": { models: rootVillagerModels("number_five", "number_five", [
    { geometry: "geometry.oreville_vn.208670578", texture: "djh" },
  ]) },
  "villager5.jem": { models: rootVillagerModels("number_nine", "number_nine", [
    { geometry: "geometry.oreville_vn.1878756082", texture: "dta" },
  ]) },
  "villager6.jem": { models: rootVillagerModels("unreachable", "diq") },
  "wandering_trader.jem": { models: rootVillagerModels("wandering_trader_news", "dix") },
  "sheep2.jem": { models: rootSheepModels("wooly", "diw") },
  "sheep3.jem": { models: rootSheepModels("wooly", "diw", true) },
  "sheep_wool_undercoat2.jem": { models: vanillaSheepSuppressors("wooly_undercoat") },
  "sheep_wool2.jem": { models: vanillaSheepSuppressors("wooly_wool") },
};

const cemRoot = join(minecraftAssets, "optifine", "cem");
for (const [file, model] of Object.entries(modelDefinitions)) {
  writeJson(join(cemRoot, file), { textureSize: [64, 64], ...model });
}

writeText(join(cemRoot, "villager.properties"), [
  "models.1=2",
  "name.1=iregex:(Mayor|Mayor Villager|The Mayor)",
  "models.2=3",
  "name.2=iregex:(Testificate Man)",
  "models.3=4",
  "name.3=iregex:(Villager Number 5|Villager #5)",
  "models.4=5",
  "name.4=iregex:(Villager Number 9|Villager #9)",
  "models.5=6",
  "name.5=iregex:(Villager Unreachable|Can't Catch Me!)",
  "",
].join("\n"));

writeText(join(cemRoot, "sheep.properties"), [
  "models.1=3",
  "name.1=iregex:(Wooly|Wooly The Sheep)",
  "nbt.1.Sheared=1",
  "models.2=2",
  "name.2=iregex:(Wooly|Wooly The Sheep)",
  "",
].join("\n"));

for (const layer of ["sheep_wool_undercoat", "sheep_wool"]) {
  writeText(join(cemRoot, `${layer}.properties`), [
    "models.1=2",
    "name.1=iregex:(Wooly|Wooly The Sheep)",
    "",
  ].join("\n"));
}

const textureSource = join(resourceRoot, "textures", "oreville", "vn");
const ffmpegCandidates = [
  process.env.FFMPEG_PATH,
  "C:\\Users\\marcy\\Downloads\\LiSA-win32-x64-2.1.0\\resources\\resources\\lisa\\_internal\\ffmpeg.exe",
].filter(Boolean);
const ffmpeg = ffmpegCandidates.find(existsSync);

const compositeTextures = {
  mayor: ["dkn", "dkq"],
  testificate_man: ["dil", "djo"],
  number_five: ["dil", "dim", "djg"],
  number_nine: ["dil", "dim", "din"],
};

function copyTexture(name, destination) {
  const png = join(textureSource, `${name}.png`);
  const tga = join(textureSource, `${name}.tga`);
  mkdirSync(dirname(destination), { recursive: true });
  if (existsSync(png)) copyFileSync(png, destination);
  else if (existsSync(tga) && ffmpeg) {
    execFileSync(ffmpeg, ["-y", "-hide_banner", "-loglevel", "error", "-i", tga, destination]);
  } else if (existsSync(tga)) {
    throw new Error(`Texture ${name}.tga needs FFmpeg. Set FFMPEG_PATH to an FFmpeg executable.`);
  } else throw new Error(`Missing texture ${name}`);
}

for (const [item, texture] of Object.entries({
  mayor_villager_spawn_egg: "eas",
  testificate_man_spawn_egg: "eat",
  villager_5_spawn_egg: "eau",
  villager_9_spawn_egg: "eav",
  untouchable_villager_spawn_egg: "eaw",
  wooly_spawn_egg: "eax",
})) {
  copyTexture(texture, join(modAssets, "textures", "item", `${item}.png`));
}

const wearableItems = {
  mayor_hat: { geometry: "geometry.oreville_vn.1064568764", texture: "eba", textureSize: [32, 32] },
  moustache: { geometry: "geometry.oreville_vn.1940352316", texture: "ebc", textureSize: [16, 16] },
  testificate_man_helmet: { geometry: "geometry.oreville_vn.-1144631652", texture: "ebd", textureSize: [16, 32] },
  villager_nose: { geometry: "geometry.oreville_vn.834522044", texture: "dil", textureSize: [64, 64] },
};

function standardCubeFaces(cube) {
  const [u, v] = cube.uv;
  const [x, y, z] = cube.size;
  return {
    west: { uv: [u, v + z], uv_size: [z, y] },
    north: { uv: [u + z, v + z], uv_size: [x, y] },
    east: { uv: [u + z + x, v + z], uv_size: [z, y] },
    south: { uv: [u + z + x + z, v + z], uv_size: [x, y] },
    up: { uv: [u + z, v], uv_size: [x, z] },
    down: { uv: [u + z + x, v], uv_size: [x, z] },
  };
}

function wornItemFaces(cube, textureSize) {
  const sourceFaces = Array.isArray(cube.uv) ? standardCubeFaces(cube) : cube.uv;
  const sourceFaceByItemFace = {
    north: "north",
    south: "south",
    east: "west",
    west: "east",
    up: "up",
    down: "down",
  };
  const faces = {};
  for (const [itemFace, sourceName] of Object.entries(sourceFaceByItemFace)) {
    const source = sourceFaces?.[sourceName];
    if (!source?.uv || !source?.uv_size) continue;
    const [u, v] = source.uv;
    const [width, height] = source.uv_size;
    const face = {
      uv: vector([
        u * 16 / textureSize[0],
        v * 16 / textureSize[1],
        (u + width) * 16 / textureSize[0],
        (v + height) * 16 / textureSize[1],
      ]),
      texture: "#texture",
    };
    if (source.uv_rotation) face.rotation = source.uv_rotation;
    faces[itemFace] = face;
  }
  return faces;
}

function wornItemPoint(point) {
  return vector([
    8 - point[0] * 1.6,
    (point[1] - 23) * 1.6,
    8 + point[2] * 1.6,
  ]);
}

function wornItemElement(cube, bone, textureSize) {
  const inflate = cube.inflate ?? 0;
  const minimum = cube.origin.map((value) => value - inflate);
  const maximum = cube.origin.map((value, index) => value + cube.size[index] + inflate);
  const element = {
    from: wornItemPoint([maximum[0], minimum[1], minimum[2]]),
    to: wornItemPoint([minimum[0], maximum[1], maximum[2]]),
    faces: wornItemFaces(cube, textureSize),
  };
  const rotation = cube.rotation ?? bone.rotation;
  if (rotation?.some(Boolean)) {
    const activeAxes = axes.filter((axis, index) => rotation[index]);
    if (activeAxes.length !== 1) throw new Error(`Unsupported wearable rotation ${JSON.stringify(rotation)}`);
    const axis = activeAxes[0];
    const index = axes.indexOf(axis);
    element.rotation = {
      origin: wornItemPoint(cube.pivot ?? bone.pivot),
      axis,
      angle: cleanNumber(rotation[index]),
    };
  }
  return element;
}

for (const [item, definition] of Object.entries(wearableItems)) {
  const geometry = geometryById.get(definition.geometry);
  if (!geometry) throw new Error(`Missing wearable geometry ${definition.geometry}`);
  const originalTextureSize = [geometry.description.texture_width, geometry.description.texture_height];
  const wornTexture = join(modAssets, "textures", "item", "worn", `${item}.png`);
  mkdirSync(dirname(wornTexture), { recursive: true });
  const sourceTexture = join(textureSource, `${definition.texture}.png`);
  if (!existsSync(sourceTexture)) throw new Error(`Missing wearable texture ${definition.texture}.png`);
  if (originalTextureSize[0] === definition.textureSize[0] && originalTextureSize[1] === definition.textureSize[1]) {
    copyFileSync(sourceTexture, wornTexture);
  } else {
    if (!ffmpeg) throw new Error(`Padding ${definition.texture}.png needs FFmpeg. Set FFMPEG_PATH to an FFmpeg executable.`);
    execFileSync(ffmpeg, [
      "-y", "-hide_banner", "-loglevel", "error", "-i", sourceTexture,
      "-vf", `format=rgba,pad=${definition.textureSize[0]}:${definition.textureSize[1]}:0:0:color=black@0,format=rgba`,
      "-pix_fmt", "rgba", "-frames:v", "1", wornTexture,
    ]);
  }
  const elements = geometry.bones.flatMap((bone) =>
    (bone.cubes ?? []).map((cube) => wornItemElement(cube, bone, definition.textureSize)));
  writeJson(join(modAssets, "models", "item", `${item}_worn.json`), {
    ambientocclusion: false,
    textures: {
      texture: `${modNamespace}:item/worn/${item}`,
      particle: `${modNamespace}:item/worn/${item}`,
    },
    elements,
  });
  writeJson(join(modAssets, "items", `${item}.json`), {
    model: {
      type: "minecraft:select",
      property: "minecraft:display_context",
      cases: [{
        when: "head",
        model: {
          type: "minecraft:model",
          model: `${modNamespace}:item/${item}_worn`,
        },
      }],
      fallback: {
        type: "minecraft:model",
        model: `${modNamespace}:item/${item}`,
      },
    },
  });
}

const directlyUsedTextures = new Set(["dtd", "djn", "djh", "dta", "diq", "dix", "diw"]);
for (const layers of Object.values(compositeTextures)) {
  for (const texture of layers) directlyUsedTextures.add(texture);
}
for (const texture of directlyUsedTextures) {
  copyTexture(texture, join(modAssets, "textures", "entity", `${texture}.png`));
}
copyTexture("dsx", join(modAssets, "textures", "entity", "sign_text.png"));

function normalizeBinaryAlpha(name) {
  if (!ffmpeg) throw new Error(`Normalizing ${name}.png needs FFmpeg. Set FFMPEG_PATH to an FFmpeg executable.`);
  const destination = join(modAssets, "textures", "entity", `${name}.png`);
  const temporary = join(modAssets, "textures", "entity", `${name}.opaque.png`);
  try {
    execFileSync(ffmpeg, [
      "-y", "-hide_banner", "-loglevel", "error", "-i", destination,
      "-vf", "lut=a='if(eq(val,0),0,255)'", "-frames:v", "1", temporary,
    ]);
    copyFileSync(temporary, destination);
  } finally {
    rmSync(temporary, { force: true });
  }
}

normalizeBinaryAlpha("diw");

function composeTexture(name, layers) {
  if (!ffmpeg) throw new Error(`Compositing ${name}.png needs FFmpeg. Set FFMPEG_PATH to an FFmpeg executable.`);
  const args = ["-y", "-hide_banner", "-loglevel", "error"];
  for (const layer of layers) args.push("-i", join(modAssets, "textures", "entity", `${layer}.png`));
  const filters = [];
  let previous = "0:v";
  for (let index = 1; index < layers.length; index++) {
    const output = index === layers.length - 1 ? "out" : `layer${index}`;
    filters.push(`[${previous}][${index}:v]overlay=format=auto[${output}]`);
    previous = output;
  }
  args.push("-filter_complex", filters.join(";"), "-map", "[out]", "-frames:v", "1",
    join(modAssets, "textures", "entity", `${name}.png`));
  execFileSync(ffmpeg, args);
}

for (const [name, layers] of Object.entries(compositeTextures)) composeTexture(name, layers);

const vanillaVillagerTextures = {
  "villager.png": "dil",
  "villager_baby.png": "dkn",
  "type/desert.png": "djp",
  "type/jungle.png": "djq",
  "type/plains.png": "dim",
  "type/savanna.png": "djr",
  "type/snow.png": "djs",
  "type/swamp.png": "djt",
  "type/taiga.png": "dju",
  "profession/none.png": "din",
  "profession/armorer.png": "djv",
  "profession/butcher.png": "djw",
  "profession/cartographer.png": "djx",
  "profession/cleric.png": "djy",
  "profession/farmer.png": "djz",
  "profession/fisherman.png": "dka",
  "profession/fletcher.png": "dkb",
  "profession/leatherworker.png": "dkc",
  "profession/librarian.png": "dkd",
  "profession/mason.png": "dkg",
  "profession/nitwit.png": "dkh",
  "profession/shepherd.png": "dke",
  "profession/toolsmith.png": "djg",
  "profession/weaponsmith.png": "dkf",
  "profession_level/stone.png": "dki",
  "profession_level/iron.png": "dkj",
  "profession_level/gold.png": "dkk",
  "profession_level/emerald.png": "dkl",
  "profession_level/diamond.png": "dkm",
};
for (const [destination, source] of Object.entries(vanillaVillagerTextures)) {
  copyTexture(source, join(minecraftAssets, "textures", "entity", "villager", destination));
}

const clientAnimationOwners = new Map();
for (const file of walkFiles(join(resourceRoot, "entity")).filter((path) => path.endsWith(".json"))) {
  const description = readJson(file)["minecraft:client_entity"]?.description;
  if (!description) continue;
  for (const [shortName, animation] of Object.entries(description.animations ?? {})) {
    for (const animationName of [shortName, animation]) {
      if (typeof animationName !== "string") continue;
      const owners = clientAnimationOwners.get(animationName) ?? new Set();
      owners.add(description.identifier);
      clientAnimationOwners.set(animationName, owners);
    }
  }
}

const soundDefinitions = readJson(join(resourceRoot, "sounds", "sound_definitions.json")).sound_definitions;
const originalLanguage = readLang(join(resourceRoot, "texts", "en_US.lang"));
const script = readFileSync(join(behaviorRoot, "scripts", "oreville", "ebi.js"), "utf8");
const sourceFile = ts.createSourceFile("ebi.js", script, ts.ScriptTarget.Latest, true, ts.ScriptKind.JS);
const metadataById = new Map();
const dialogueGroups = new Map();
const seenSounds = new Set();
mkdirSync(join(modAssets, "sounds", "voice"), { recursive: true });

function property(object, name) {
  if (!ts.isObjectLiteralExpression(object)) return undefined;
  return object.properties.find((candidate) =>
    ts.isPropertyAssignment(candidate) && candidate.name.getText(sourceFile) === name,
  )?.initializer;
}

function literalText(node) {
	if (!node) return undefined;
  if (ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node)) return node.text;
  if (ts.isPropertyAccessExpression(node) && node.expression.getText(sourceFile) === "lkryzq") {
    return literalAliasMap("lkryzq").get(node.name.text);
  }
  return undefined;
}

function literalNumber(node, fallback = 0) {
  if (!node) return fallback;
  if (ts.isNumericLiteral(node)) return Number(node.text);
  if (ts.isPrefixUnaryExpression(node) && ts.isNumericLiteral(node.operand)) {
    return node.operator === ts.SyntaxKind.MinusToken ? -Number(node.operand.text) : Number(node.operand.text);
  }
  return fallback;
}

function subtitleTimeline(variant) {
  const timeline = property(variant, "aswuwr");
  if (!ts.isObjectLiteralExpression(timeline)) return [];
  const subtitles = [];
  for (const entry of timeline.properties) {
    if (!ts.isPropertyAssignment(entry) || !ts.isObjectLiteralExpression(entry.initializer)) continue;
    const time = Number(entry.name.getText(sourceFile));
    const key = literalText(property(entry.initializer, "ysyeto"));
    if (!Number.isFinite(time) || !key) continue;
    const value = originalLanguage.get(key);
    if (value === undefined) throw new Error(`Missing subtitle translation ${key}`);
    subtitles.push({ time: cleanNumber(time), text: value });
  }
  return subtitles.sort((left, right) => left.time - right.time);
}

function scanMetadata(node) {
  if (
    ts.isPropertyAssignment(node)
    && ts.isComputedPropertyName(node.name)
    && ts.isPropertyAccessExpression(node.name.expression)
    && node.name.expression.expression.getText(sourceFile) === "uxyuyr"
    && ts.isObjectLiteralExpression(node.initializer)
  ) {
    const id = node.name.expression.name.text;
    metadataById.set(id, {
      title: literalText(property(node.initializer, "title")) ?? "",
      browseTitle: literalText(property(node.initializer, "bkltkm")) ?? literalText(property(node.initializer, "title")) ?? "",
      body: literalText(property(node.initializer, "body")) ?? "",
    });
  }
  ts.forEachChild(node, scanMetadata);
}

function scanDialogue(node) {
  if (
    ts.isCallExpression(node)
    && ts.isPropertyAccessExpression(node.expression)
    && node.expression.expression.getText(sourceFile) === "Dialog"
    && node.expression.name.text === "kidjht"
    && ts.isObjectLiteralExpression(node.arguments[0])
  ) {
    const definition = node.arguments[0];
    const id = literalText(property(definition, "id"));
    const variants = property(definition, "slhkqn");
    if (!id || !ts.isArrayLiteralExpression(variants)) return;
    const sounds = [];
    let maximumDuration = 0;
    for (const variant of variants.elements) {
      if (!ts.isObjectLiteralExpression(variant)) continue;
      const soundId = literalText(property(variant, "soundId"));
      const animationName = literalText(property(variant, "animationName"));
      const source = soundDefinitions[soundId]?.sounds?.[0]?.name;
      if (!source) continue;
		const outputName = source.split("/").at(-1);
      const duration = literalNumber(property(variant, "duration"));
      const weight = Math.max(1, Math.round(literalNumber(property(variant, "weight"), 1) * 100));
      const subtitles = subtitleTimeline(variant);
      if (!subtitles.length) throw new Error(`Missing subtitle for dialogue ${id}.${sounds.length}`);
      maximumDuration = Math.max(maximumDuration, duration);
      sounds.push({ name: outputName, weight, duration, animationName: animationName ?? "", subtitles });
		if (!seenSounds.has(outputName)) {
			const outputFile = join(modAssets, "sounds", "voice", `${outputName}.ogg`);
			copyFileSync(join(resourceRoot, `${source}.ogg`), outputFile);
			repairOggGranules(outputFile);
			seenSounds.add(outputName);
      }
    }
    dialogueGroups.set(id, { sounds, maximumDuration });
  }
  ts.forEachChild(node, scanDialogue);
}

scanMetadata(sourceFile);
scanDialogue(sourceFile);

function variableInitializer(name) {
  let result;
  function visit(node) {
    if (result) return;
    if (ts.isVariableDeclaration(node) && ts.isIdentifier(node.name) && node.name.text === name) {
      result = node.initializer;
      return;
    }
    ts.forEachChild(node, visit);
  }
  visit(sourceFile);
  return result;
}

function literalAliasMap(name) {
  const object = variableInitializer(name);
  const result = new Map();
  if (!ts.isObjectLiteralExpression(object)) return result;
  for (const entry of object.properties) {
    if (!ts.isPropertyAssignment(entry) || !ts.isIdentifier(entry.name)) continue;
    const value = literalText(entry.initializer);
    if (value !== undefined) result.set(entry.name.text, value);
  }
  return result;
}

function referenceAlias(node, owner) {
  return ts.isPropertyAccessExpression(node) && node.expression.getText(sourceFile) === owner
    ? node.name.text : undefined;
}

function referenceArray(name, owner, aliases) {
  const array = variableInitializer(name);
  if (!ts.isArrayLiteralExpression(array)) return [];
  return array.elements.map((entry) => aliases.get(referenceAlias(entry, owner))).filter(Boolean);
}

function computedLabels(name, owner, aliases) {
  const object = variableInitializer(name);
  const result = new Map();
  if (!ts.isObjectLiteralExpression(object)) return result;
  for (const entry of object.properties) {
    if (!ts.isPropertyAssignment(entry) || !ts.isComputedPropertyName(entry.name)) continue;
    const key = aliases.get(referenceAlias(entry.name.expression, owner));
    const value = literalText(entry.initializer);
    if (key && value !== undefined) result.set(key, value);
  }
  return result;
}

function computedArrays(name, keyOwner, keyAliases, valueOwner, valueAliases) {
  const object = variableInitializer(name);
  const result = new Map();
  if (!ts.isObjectLiteralExpression(object)) return result;
  for (const entry of object.properties) {
    if (!ts.isPropertyAssignment(entry) || !ts.isComputedPropertyName(entry.name)
      || !ts.isArrayLiteralExpression(entry.initializer)) continue;
    const key = keyAliases.get(referenceAlias(entry.name.expression, keyOwner));
    if (!key) continue;
    result.set(key, entry.initializer.elements
      .map((value) => valueAliases.get(referenceAlias(value, valueOwner)))
      .filter(Boolean));
  }
  return result;
}

function handbookEntries(name) {
  const array = variableInitializer(name);
  if (!ts.isArrayLiteralExpression(array)) return [];
  return array.elements.filter(ts.isObjectLiteralExpression).map((entry) => ({
    title: literalText(property(entry, "header")) ?? "",
    body: literalText(property(entry, "body")) ?? "",
  }));
}

const categoryAliases = literalAliasMap("juuyxy");
const sectionAliases = literalAliasMap("hbkffr");
const groupAliases = literalAliasMap("uxyuyr");
const groupReferences = new Map([...groupAliases.keys()].map((key) => [key, key]));
const categoryLabels = computedLabels("pyveoi", "juuyxy", categoryAliases);
const sectionLabels = computedLabels("eawipt", "hbkffr", sectionAliases);
const categories = referenceArray("hgphbc", "juuyxy", categoryAliases);
const sectionsByCategory = computedArrays("negxgt", "juuyxy", categoryAliases, "hbkffr", sectionAliases);
const groupsBySection = computedArrays("qoimcn", "hbkffr", sectionAliases, "uxyuyr", groupReferences);
const handbook = {
  headline: "Breaking News!\n\nVillagers are now judging your every move with over 2,000+ voiced reactions.",
  guideIntro: "Explore Villager News, meet special characters and discover hidden reactions.\n\nOnline Guide\norevillestudios.com/villager-news",
  overview: handbookEntries("cphxgl"),
  specialVillagers: handbookEntries("gucqpi"),
  cosmetics: handbookEntries("vxivvu"),
  generalInformation: handbookEntries("vhbeyx"),
  socials: handbookEntries("oshzpy"),
  support: "Need help, found a bug or have some feedback? Get in touch with Oreville Studios below.\n\nWebsite\norevillestudios.com\n\nEmail Support\nsupport@orevillestudios.com\n\nCommunity Discord\norevillestudios.com/discord",
  settings: [
    { title: "Show Subtitles", body: "Whether subtitles appear when Villagers speak." },
    { title: "Villager Chattiness", body: "How often Villagers speak: Muted, Shy, Chatty, or Super Chatty." },
    { title: "Rare Villager Voicelines", body: "How often Villagers say longer, more unusual lines: Never, Default, or Often." },
    { title: "Spawn Special Villagers", body: "Whether Villager News characters can naturally spawn." },
    { title: "Villager Style", body: "The Java port uses the Villager News style supplied through EMF and ETF." },
  ],
  contexts: Object.fromEntries(metadataById),
  categories: categories.map((category) => ({
    id: category,
    title: categoryLabels.get(category) ?? category,
    sections: (sectionsByCategory.get(category) ?? []).map((section) => ({
      id: section,
      title: sectionLabels.get(section) ?? section,
      groups: groupsBySection.get(section) ?? [],
      entries: section === sectionAliases.get("qllzox") ? handbookEntries("mqptgt") : [],
    })),
  })),
};

const knownSpeakers = new Map();
for (const [speaker, ids] of Object.entries({
  mayor: ["dpwhhs", "xxehbq", "njyapy", "ssbhiv", "ltdnvy", "bgzmea", "shrrya"],
  number_5: ["xccwah", "legnsy", "sclaoa", "behifz", "nfdery", "msofrj"],
  number_9: ["kzogzi", "ezgbfw", "snnkrl", "wrbvvp", "asuufu", "hvjfnk"],
  testificate_man: ["nmwmrz", "luoibc", "mpbnsm", "fzoqwd", "ctzfzj", "rdugrl", "xcjort"],
  unreachable: ["eltxge"],
  wandering_trader: ["hxlyuc", "stqafd", "yubpbb", "kxoqky", "bvrbhy", "erbcfn", "uzdvsi"],
  wooly: ["uvtocs", "vmohcm", "fskcce", "jqaekk", "eyiraw", "ncyeaw"],
})) {
  for (const id of ids) knownSpeakers.set(id, speaker);
}

function detectSpeaker(metadata, id) {
  if (knownSpeakers.has(id)) return knownSpeakers.get(id);
  const description = `${metadata.title} ${metadata.body}`;
  if (/Regular Villager comments/i.test(description)) return "villager";
  if (/Wooly(?:'s| to notice| while| take| reaction| responses| idle)/i.test(description)) return "wooly";
  if (/The Mayor(?:'s| to notice| while| take)|Mayor-specific|from The Mayor/i.test(description)) return "mayor";
  if (/Villager #5(?:'s| to notice| while| take| reactions| comments| farewells)/i.test(description)) return "number_5";
  if (/Villager #9(?:'s| to notice| while| take| reactions| comments| farewells)|reporter-style/i.test(description)) return "number_9";
  if (/Testificate Man(?:'s| to notice| while| take| reactions| comments| farewells)/i.test(description)) return "testificate_man";
  if (/Untouchable Villager/i.test(description)) return "unreachable";
  if (/Wandering Trader/i.test(description)) return "wandering_trader";
  return "villager";
}

const catalog = { groups: {}, titles: {} };
const javaSounds = {};
const javaLanguageFile = join(modAssets, "lang", "en_us.json");
const javaLanguage = existsSync(javaLanguageFile) ? readJson(javaLanguageFile) : {};
for (const key of Object.keys(javaLanguage)) {
  if (key === `subtitles.${modNamespace}.talking`
    || key.startsWith(`subtitles.${modNamespace}.dialogue.`)) delete javaLanguage[key];
}
for (const [id, group] of dialogueGroups) {
  const metadata = metadataById.get(id) ?? { title: "", body: "" };
  catalog.groups[id] = {
    title: metadata.title,
    body: metadata.body,
    speaker: detectSpeaker(metadata, id),
    maximumDuration: cleanNumber(group.maximumDuration),
    variants: group.sounds.map((sound, index) => ({
      index,
      duration: cleanNumber(sound.duration),
      weight: sound.weight,
      animation: sound.animationName,
      subtitles: sound.subtitles,
    })),
  };
  if (metadata.title) catalog.titles[metadata.title] = id;
  for (const [index, sound] of group.sounds.entries()) {
    catalog.groups[id].variants[index].subtitles = sound.subtitles.map((subtitle, subtitleIndex) => {
      const key = `subtitles.${modNamespace}.dialogue.${id}.${index}.${subtitleIndex}`;
      javaLanguage[key] = subtitle.text.replaceAll("%", "%%");
      return { time: subtitle.time, key };
    });
    javaSounds[`dialogue.${id}.${index}`] = {
      sounds: [{
      name: `${modNamespace}:voice/${sound.name}`,
      stream: true,
      }],
    };
  }
}
writeJson(join(modAssets, "dialogues.json"), catalog);
writeJson(join(modAssets, "handbook.json"), handbook);
writeJson(join(modAssets, "sounds.json"), javaSounds);
writeJson(javaLanguageFile, javaLanguage);

const commonClientDescription = walkFiles(join(resourceRoot, "entity"))
  .filter((path) => path.endsWith(".json"))
  .map((path) => readJson(path)["minecraft:client_entity"]?.description)
  .find((description) => description?.geometry?.default === commonVillager);
const commonAnimationAliases = commonClientDescription?.animations ?? {};

function flattenedTimelineStatements(value) {
  if (Array.isArray(value)) return value.flatMap(flattenedTimelineStatements);
  return typeof value === "string" ? [value] : [];
}

function dialogueAnimation(animationName) {
  const animation = animationById.get(animationName) ?? {};
  const mouth = { invysa: 0, cfulfb: 1, ziisoq: 1 };
  const mouthFrames = [[0, mouth.invysa, mouth.cfulfb, mouth.ziisoq]];
  const gestureFrames = [];
  for (const [timeText, rawStatements] of Object.entries(animation.timeline ?? {}).sort((a, b) => Number(a[0]) - Number(b[0]))) {
    const time = cleanNumber(Number(timeText));
    let mouthChanged = false;
    for (const statement of flattenedTimelineStatements(rawStatements)) {
      for (const match of statement.matchAll(/v\.(invysa|cfulfb|ziisoq)\s*=\s*(-?(?:\d+(?:\.\d*)?|\.\d+))/g)) {
        mouth[match[1]] = Number(match[2]);
        mouthChanged = true;
      }
      for (const match of statement.matchAll(/v\.skwjdr\s*=\s*'([^']+)'/g)) {
        gestureFrames.push([time, match[1]]);
      }
    }
    if (mouthChanged) {
      const next = [time, cleanNumber(mouth.invysa), cleanNumber(mouth.cfulfb), cleanNumber(mouth.ziisoq)];
      if (mouthFrames.at(-1)[0] === time) mouthFrames[mouthFrames.length - 1] = next;
      else mouthFrames.push(next);
    }
  }
  return { mouth: mouthFrames, gestures: gestureFrames };
}

const dialogueAnimationData = {};
const usedGestureNames = new Set();
for (const [groupId, group] of dialogueGroups) {
  dialogueAnimationData[groupId] = group.sounds.map((sound) => {
    const animation = dialogueAnimation(sound.animationName);
    for (const [, gestureName] of animation.gestures) {
      if (gestureName !== "default") usedGestureNames.add(gestureName);
    }
    return animation;
  });
}

const degreeMath = {
  sin: (degrees) => Math.sin(degrees * Math.PI / 180),
  cos: (degrees) => Math.cos(degrees * Math.PI / 180),
  tan: (degrees) => Math.tan(degrees * Math.PI / 180),
  asin: (value) => Math.asin(value) * 180 / Math.PI,
  acos: (value) => Math.acos(value) * 180 / Math.PI,
  atan: (value) => Math.atan(value) * 180 / Math.PI,
  abs: Math.abs,
  min: Math.min,
  max: Math.max,
  floor: Math.floor,
  ceil: Math.ceil,
  sqrt: Math.sqrt,
};

function evaluateMolang(value, time, fallback) {
  if (typeof value === "number") return Number.isFinite(value) ? value : fallback;
  if (typeof value !== "string") return fallback;
  try {
	const expression = value
	  .replaceAll("q.anim_time", "t")
	  .replaceAll("q.life_time", "t")
	  .replaceAll("v.aaziza", "1")
	  .replaceAll("Math.", "M.");
    const result = Function("M", "t", `return (${expression});`)(degreeMath, time);
    return Number.isFinite(result) ? result : fallback;
  } catch {
    return fallback;
  }
}

function keyframeVector(raw, side, time, defaults) {
  let value = raw;
  if (value && !Array.isArray(value) && typeof value === "object") {
    value = value[side] ?? value.post ?? value.pre;
  }
  if (!Array.isArray(value)) value = [value, value, value];
  return defaults.map((fallback, axis) => evaluateMolang(value[axis], time, fallback));
}

function catmullRom(progress, p0, p1, p2, p3) {
  const squared = progress * progress;
  const cubed = squared * progress;
  return 0.5 * ((2 * p1) + (-p0 + p2) * progress
    + (2 * p0 - 5 * p1 + 4 * p2 - p3) * squared
    + (-p0 + 3 * p1 - 3 * p2 + p3) * cubed);
}

function sampleTrack(track, time, defaults) {
  if (track == null) return defaults;
  if (Array.isArray(track) || typeof track !== "object") return keyframeVector(track, "post", time, defaults);
  const frames = Object.entries(track)
    .filter(([key]) => Number.isFinite(Number(key)))
    .sort((a, b) => Number(a[0]) - Number(b[0]));
  if (!frames.length) return defaults;
  if (time <= Number(frames[0][0])) return keyframeVector(frames[0][1], "pre", time, defaults);
  if (time >= Number(frames.at(-1)[0])) return keyframeVector(frames.at(-1)[1], "post", time, defaults);
  let nextIndex = frames.findIndex(([key]) => Number(key) >= time);
  if (nextIndex <= 0) nextIndex = 1;
  const previousIndex = nextIndex - 1;
  const previousTime = Number(frames[previousIndex][0]);
  const nextTime = Number(frames[nextIndex][0]);
  const progress = (time - previousTime) / Math.max(0.00001, nextTime - previousTime);
  const previous = keyframeVector(frames[previousIndex][1], "post", time, defaults);
  const next = keyframeVector(frames[nextIndex][1], "pre", time, defaults);
  const interpolation = frames[nextIndex][1]?.lerp_mode ?? frames[previousIndex][1]?.lerp_mode;
  if (interpolation !== "catmullrom") {
    return defaults.map((ignored, axis) => previous[axis] + (next[axis] - previous[axis]) * progress);
  }
  const before = keyframeVector(frames[Math.max(0, previousIndex - 1)][1], "post", time, defaults);
  const after = keyframeVector(frames[Math.min(frames.length - 1, nextIndex + 1)][1], "pre", time, defaults);
  return defaults.map((ignored, axis) => catmullRom(progress, before[axis], previous[axis], next[axis], after[axis]));
}

const animationTargets = {
  root: ["root"],
  waist: ["waist"],
  body: ["body"],
  head: ["head"],
  head_inner: ["headjgl2l6"],
  arms: ["2jek"],
  left_leg_root: ["jggl_leftleg"],
  left_leg: ["leftleg"],
  right_leg_root: ["jggl_rightleg"],
  right_leg: ["rightleg"],
  brow: ["egfg3jgo"],
  eye_group: ["6q6da5kmhh6j"],
  lower_face: ["6q6da5kdgo6j"],
  pupil_left: ["ja89l_6q6"],
  pupil_right: ["d67l_6q6"],
  eye_left: ["6q6da5kmhh6jja89l"],
  eye_right: ["6q6da5kmhh6jd67l"],
  nose: ["fgk6"],
};
const transformKinds = [
  ["rotation", "r", [0, 0, 0]],
  ["position", "t", [0, 0, 0]],
  ["scale", "s", [1, 1, 1]],
];
const bakedFramesPerSecond = 24;

function sampleTarget(animation, sourceBones, kind, time, defaults) {
  const sampled = defaults.slice();
  for (const sourceBone of sourceBones) {
    const value = sampleTrack(animation.bones?.[sourceBone]?.[kind], time, defaults);
    for (let axis = 0; axis < 3; axis++) {
      if (kind === "scale") sampled[axis] *= value[axis];
      else sampled[axis] += value[axis];
    }
  }
  if (kind === "rotation") {
    sampled[0] = sampled[0] * Math.PI / 180;
    sampled[1] = sampled[1] * Math.PI / 180;
    sampled[2] = sampled[2] * Math.PI / 180;
  } else if (kind === "position") {
    sampled[1] = -sampled[1];
  }
  return sampled.map(cleanNumber);
}

const gestureCompanions = {
  phmycx: ["clnzxd"],
  qcjrlv: ["xmtqdi"],
  srtjvb: ["aahqsf"],
  hlofgw: ["cytfsh"],
  tlrowv: ["pbfspv"],
  hmnopd: ["qswzxh"],
  ypxycs: ["kkagqa"],
};
function bakeAnimationLayers(layerNames) {
	const sourceAnimations = layerNames.map((name) => animationById.get(commonAnimationAliases[name]) ?? {});
  const layerDurations = sourceAnimations.map((animation) => Number(animation.animation_length ?? 0)).filter((value) => value > 0);
  const duration = layerDurations.length ? Math.min(...layerDurations) : 0;
  const frameCount = Math.max(1, Math.ceil(duration * bakedFramesPerSecond) + 1);
  const tracks = {};
  for (const [target, sourceBones] of Object.entries(animationTargets)) {
    for (const [kind, shortKind, defaults] of transformKinds) {
      const values = Array.from({ length: 3 }, () => []);
      for (let frame = 0; frame < frameCount; frame++) {
        const time = Math.min(duration, frame / bakedFramesPerSecond);
        const sampled = defaults.slice();
        for (const animation of sourceAnimations) {
          const layer = sampleTarget(animation, sourceBones, kind, time, defaults);
          for (let axis = 0; axis < 3; axis++) {
            if (kind === "scale") sampled[axis] *= layer[axis];
            else sampled[axis] += layer[axis];
          }
        }
        for (let axis = 0; axis < 3; axis++) values[axis].push(sampled[axis]);
      }
      for (let axis = 0; axis < 3; axis++) {
        const fallback = defaults[axis];
        if (values[axis].some((value) => Math.abs(value - fallback) > 0.00001)) {
          tracks[`${target}_${shortKind}${axes[axis]}`] = values[axis];
        }
      }
    }
  }
	return { layers: layerNames, duration: cleanNumber(duration), tracks };
}

const gestureNames = [...usedGestureNames].sort();
const gestureIndexes = new Map(gestureNames.map((name, index) => [name, index]));
const bakedGestures = gestureNames.map((gestureName) => ({
	name: gestureName,
	...bakeAnimationLayers([gestureName, ...(gestureCompanions[gestureName] ?? [])]),
}));
const locomotionAnimation = bakeAnimationLayers(["move"]);
const idleAnimations = ["unjyad", "supuhq", "qvpghh", "edhave", "kvjhyc", "igrbri"]
	.map((name) => ({ name, ...bakeAnimationLayers([name]) }));

for (const variants of Object.values(dialogueAnimationData)) {
  for (const animation of variants) {
    animation.gestures = animation.gestures.map(([time, gestureName]) => [time, gestureIndexes.get(gestureName) ?? -1]);
  }
}
writeJson(join(modAssets, "dialogue_animations.json"), {
	framesPerSecond: bakedFramesPerSecond,
	continuousIdle: commonAnimationAliases.uchrur,
	targetLook: commonAnimationAliases.target,
	groups: dialogueAnimationData,
	gestures: bakedGestures,
	locomotion: locomotionAnimation,
	idles: idleAnimations,
});

const silenceFile = join(modAssets, "sounds", "silence.ogg");
if (preservedSilence) {
  writeFileSync(silenceFile, preservedSilence);
} else {
  if (!ffmpeg) throw new Error("FFmpeg is required to generate the ESF silence clip.");
  execFileSync(ffmpeg, [
    "-y", "-hide_banner", "-loglevel", "error",
    "-f", "lavfi", "-i", "anullsrc=r=44100:cl=mono",
    "-t", "0.1", "-c:a", "libvorbis", silenceFile,
  ]);
}

for (const event of ["ambient", "hurt", "death", "trade", "no"]) {
  const eventRoot = join(minecraftAssets, "esf", "entity", "villager");
  writeJson(join(eventRoot, `${event}2.json`), {
    sounds: [{ name: `${modNamespace}:silence`, volume: 0.01, weight: 1 }],
  });
  writeText(join(eventRoot, `${event}.properties`), [
    "sounds.1=2",
    "",
  ].join("\n"));
}

for (const event of ["ambient", "hurt", "death", "trade", "no", "yes"]) {
  const eventRoot = join(minecraftAssets, "esf", "entity", "wandering_trader");
  writeJson(join(eventRoot, `${event}2.json`), {
    sounds: [{ name: `${modNamespace}:silence`, volume: 0.01, weight: 1 }],
  });
  writeText(join(eventRoot, `${event}.properties`), [
    "sounds.1=2",
    "",
  ].join("\n"));
}

for (const event of ["ambient", "hurt", "death"]) {
  const eventRoot = join(minecraftAssets, "esf", "entity", "sheep");
  writeJson(join(eventRoot, `${event}2.json`), {
    sounds: [{ name: `${modNamespace}:silence`, volume: 0.01, weight: 1 }],
  });
  writeText(join(eventRoot, `${event}.properties`), [
    "sounds.1=2",
    "name.1=iregex:(Wooly|Wooly The Sheep)",
    "",
  ].join("\n"));
}

const originalIcon = join(resourceRoot, "pack_icon.png");
if (existsSync(originalIcon)) copyFileSync(originalIcon, join(modAssets, "icon.png"));

console.log(JSON.stringify({
  geometries: geometryById.size,
  models: Object.keys(modelDefinitions).length,
  textures: directlyUsedTextures.size + Object.keys(vanillaVillagerTextures).length,
  voiceClips: seenSounds.size,
  dialogueGroups: dialogueGroups.size,
  documentedContexts: metadataById.size,
  output: relative(projectRoot, outputRoot),
}, null, 2));
