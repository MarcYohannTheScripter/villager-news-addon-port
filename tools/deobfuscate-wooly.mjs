import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const sourceRoot = join(projectRoot, "build", "bedrock-source");
const resourceRoot = join(sourceRoot, "Villager News 1.0 Add-On RP");
const behaviorRoot = join(sourceRoot, "Villager News 1.0 Add-On BP");
const outputRoot = join(projectRoot, "build", "deobfuscated-bedrock-source", "wooly");

const files = {
  clientEntity: join(resourceRoot, "entity", "ebl.json"),
  geometry: join(resourceRoot, "models", "entity", "defce198.json"),
  renderControllers: join(resourceRoot, "render_controllers", "fa2a4464.json"),
  behaviorEntity: join(behaviorRoot, "entities", "eyx.json"),
};

for (const [kind, file] of Object.entries(files)) {
  if (!existsSync(file)) throw new Error(`Missing original ${kind}: ${file}`);
}

const boneNames = {
  root: "root",
  body: "body_controller",
  "46fljga5": "skin_body",
  "oggd_46fljga5": "fleece_body",
  k966h_head: "skin_head",
  oggd_head: "fleece_head",
  "3dafc": "eyelids",
  "7246gn6jd2q": "neutral_face",
  l66l9: "closed_mouth_controller",
  l66l9lgh: "upper_lip",
  l66l93gllge: "lower_lip",
  egml9: "open_mouth",
  root_d680: "right_hind_leg_controller",
  d0_7dggj: "right_hind_leg_anchor",
  d680: "right_hind_skin_leg",
  oggd_d680: "right_hind_fleece",
  root_d681: "left_hind_leg_controller",
  d1_7dggj: "left_hind_leg_anchor",
  d681: "left_hind_skin_leg",
  oggd_d681: "left_hind_fleece",
  root_d682: "right_front_leg_controller",
  d2_7dggj: "right_front_leg_anchor",
  d682: "right_front_skin_leg",
  oggd_d682: "right_front_fleece",
  root_d683: "left_front_leg_controller",
  d3_7dggj: "left_front_leg_anchor",
  d683: "left_front_skin_leg",
  oggd_d683: "left_front_fleece",
};

const symbols = {
  "oreville_vn:mlkxjo": "villager_news:wooly",
  "geometry.oreville_vn.-650401518": "geometry.villager_news.wooly",
  "geometry.oreville_vn.567476114": "geometry.villager_news.wooly_variant",
  "controller.render.oreville_vn.mlkxjo": "controller.render.villager_news.wooly",
  "controller.render.oreville_vn.okydtp": "controller.render.villager_news.wooly_jeb_base",
  "controller.render.oreville_vn.fenzmp": "controller.render.villager_news.wooly_jeb_tint",
  "p:pmpece": "villager_news:model_variant",
  "p:gwvveg": "villager_news:behavior_enabled",
  "p:gmtjzx": "villager_news:movement_paused",
  afeggp: "despawn_now",
  bfpenh: "mobile_ai",
  hsqaat: "paused_ai",
  uzdkwv: "despawn",
  jpprvt: "set_red_wool",
  jzvicg: "enable_behavior",
  gaxgod: "disable_behavior",
  ryfcbi: "pause_until_hurt",
  egrovy: "resume_after_hurt",
  tcglpl: "notify_wooly_script",
  wycgfr: "is_model_variant_1",
  mtbwfe: "is_model_variant_2",
  okkijt: "is_jeb_named",
  arekac: "is_baby",
  qkqtgf: "is_alive",
  kfqdwh: "is_on_ground",
  xuvdhk: "is_in_water",
  chtlsx: "smoothed_target_y_rotation",
  utxgkp: "smoothed_target_x_rotation",
  lghskj: "body_yaw_delta",
  hayxze: "rainbow_cycle_time",
  vnvgga: "rainbow_color_index",
  fessds: "rainbow_next_color_index",
  istewz: "rainbow_lerp",
  zflqwl: "rainbow_red",
  tjwwdo: "rainbow_green",
  mgyfin: "rainbow_blue",
  zmpsgj: "rainbow_next_red",
  elstnr: "rainbow_next_green",
  fvizqb: "rainbow_next_blue",
  yyuxbe: "render_red",
  wiuzqi: "render_green",
  edxddh: "render_blue",
  bdqfdl: "texture_dir",
  jaaxqc: "texture_dis",
  qqkjbz: "texture_dit",
  pituqg: "texture_diu",
  hwgmje: "texture_div",
  bvyrue: "variant_geometry",
};

// `body` is also part of Molang query names such as q.body_y_rotation, so
// exact bone names are handled separately below instead of globally.
const expressionBoneNames = Object.fromEntries(Object.entries(boneNames)
  .filter(([name]) => name !== "root" && name !== "body"));
const replacements = Object.entries({ ...symbols, ...expressionBoneNames })
  .sort(([left], [right]) => right.length - left.length);

function readJson(file) {
  return JSON.parse(readFileSync(file, "utf8"));
}

function replaceString(value) {
  let result = value;
  for (const [opaque, readable] of replacements) {
    result = result.replaceAll(opaque, readable);
  }
  return result;
}

function deobfuscate(value) {
  if (Array.isArray(value)) return value.map(deobfuscate);
  if (value && typeof value === "object") {
    return Object.fromEntries(Object.entries(value).map(([key, child]) => [replaceString(key), deobfuscate(child)]));
  }
  return typeof value === "string" ? replaceString(value) : value;
}

function writeJson(name, value) {
  mkdirSync(outputRoot, { recursive: true });
  writeFileSync(join(outputRoot, name), `${JSON.stringify(value, null, 2)}\n`);
}

const geometrySource = readJson(files.geometry);
const woolyGeometries = geometrySource["minecraft:geometry"].filter(({ description }) =>
  ["geometry.oreville_vn.-650401518", "geometry.oreville_vn.567476114"].includes(description.identifier));
if (woolyGeometries.length !== 2) throw new Error(`Expected two Wooly geometries, found ${woolyGeometries.length}`);

const renderSource = readJson(files.renderControllers);
const woolyControllerNames = [
  "controller.render.oreville_vn.mlkxjo",
  "controller.render.oreville_vn.okydtp",
  "controller.render.oreville_vn.fenzmp",
];
const woolyControllers = Object.fromEntries(woolyControllerNames.map((name) => {
  const controller = renderSource.render_controllers[name];
  if (!controller) throw new Error(`Missing Wooly render controller ${name}`);
  return [name, controller];
}));

const readableGeometry = deobfuscate({
  format_version: geometrySource.format_version,
  "minecraft:geometry": woolyGeometries,
});
for (const geometry of readableGeometry["minecraft:geometry"]) {
  for (const bone of geometry.bones ?? []) {
    bone.name = boneNames[bone.name] ?? bone.name;
    if (bone.parent) bone.parent = boneNames[bone.parent] ?? bone.parent;
  }
}
writeJson("geometry.json", readableGeometry);
writeJson("client-entity.json", deobfuscate(readJson(files.clientEntity)));
writeJson("render-controllers.json", deobfuscate({
  format_version: renderSource.format_version,
  render_controllers: woolyControllers,
}));
writeJson("behavior-entity.json", deobfuscate(readJson(files.behaviorEntity)));
writeJson("symbol-map.json", { symbols, boneNames });

console.log(JSON.stringify({ output: outputRoot, geometries: woolyGeometries.length, bones: Object.keys(boneNames).length }, null, 2));
