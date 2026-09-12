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
import { createRequire } from "node:module";
import { dirname, extname, join, relative, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";

const require = createRequire(import.meta.url);
const ts = require("typescript");
const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const sourceRoot = join(projectRoot, "build", "bedrock-source");
const behaviorRoot = join(sourceRoot, "Villager News 1.0 Add-On BP");
const resourceRoot = join(sourceRoot, "Villager News 1.0 Add-On RP");
const outputRoot = join(projectRoot, "build", "deobfuscated-bedrock-source", "full-addon");
const scriptFile = join(behaviorRoot, "scripts", "oreville", "ebi.js");
const copiedExtensions = new Set([".json", ".js", ".lang", ".mcfunction", ".txt"]);

if (!existsSync(behaviorRoot) || !existsSync(resourceRoot) || !existsSync(scriptFile)) {
  throw new Error("Extract the original Bedrock add-on into build/bedrock-source first.");
}
if (!outputRoot.startsWith(`${projectRoot}${sep}`)) throw new Error(`Unsafe output path: ${outputRoot}`);

function walkFiles(root) {
  const output = [];
  for (const name of readdirSync(root)) {
    const path = join(root, name);
    if (statSync(path).isDirectory()) output.push(...walkFiles(path));
    else output.push(path);
  }
  return output;
}

function writeText(path, text) {
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, text);
}

function writeJson(path, value) {
  writeText(path, `${JSON.stringify(value, null, 2)}\n`);
}

function property(object, name, sourceFile) {
  if (!ts.isObjectLiteralExpression(object)) return undefined;
  return object.properties.find((candidate) =>
    ts.isPropertyAssignment(candidate) && candidate.name.getText(sourceFile) === name,
  )?.initializer;
}

function literalText(node) {
  return ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node) ? node.text : undefined;
}

const rawScript = readFileSync(scriptFile, "utf8");
const rawSource = ts.createSourceFile("ebi.js", rawScript, ts.ScriptTarget.Latest, true, ts.ScriptKind.JS);
const dialogueSymbols = new Map();
const dialogueMetadata = new Map();

function scanDialogueData(node) {
  if (ts.isVariableDeclaration(node) && ts.isIdentifier(node.name) && node.initializer
      && ts.isCallExpression(node.initializer)
      && ts.isPropertyAccessExpression(node.initializer.expression)
      && node.initializer.expression.expression.getText(rawSource) === "Dialog"
      && node.initializer.expression.name.text === "kidjht"
      && ts.isObjectLiteralExpression(node.initializer.arguments[0])) {
    const id = literalText(property(node.initializer.arguments[0], "id", rawSource));
    if (id) dialogueSymbols.set(node.name.text, id);
  }
  if (ts.isPropertyAssignment(node) && ts.isComputedPropertyName(node.name)
      && ts.isPropertyAccessExpression(node.name.expression)
      && node.name.expression.expression.getText(rawSource) === "uxyuyr"
      && ts.isObjectLiteralExpression(node.initializer)) {
    const symbol = node.name.expression.name.text;
    dialogueMetadata.set(symbol, {
      title: literalText(property(node.initializer, "title", rawSource)) ?? "",
      body: literalText(property(node.initializer, "body", rawSource)) ?? "",
    });
  }
  ts.forEachChild(node, scanDialogueData);
}

scanDialogueData(rawSource);

let readableScript = rawScript;
for (const [symbol, id] of [...dialogueSymbols].sort(([left], [right]) => right.length - left.length)) {
  readableScript = readableScript.replace(new RegExp(`\\b${symbol}\\b`, "g"), `dialogue_${id}`);
}
const readableSource = ts.createSourceFile("behavior-script.js", readableScript, ts.ScriptTarget.Latest, true, ts.ScriptKind.JS);
const printer = ts.createPrinter({ newLine: ts.NewLineKind.LineFeed, removeComments: true });

rmSync(outputRoot, { recursive: true, force: true });
mkdirSync(outputRoot, { recursive: true });

const packs = [
  ["behavior-pack", behaviorRoot],
  ["resource-pack", resourceRoot],
];
const packInventory = {};
const itemIds = new Set();
const entityIds = new Set();
const recipeIds = new Set();

for (const [outputName, packRoot] of packs) {
  const files = walkFiles(packRoot);
  const extensions = {};
  const directories = {};
  for (const file of files) {
    const extension = extname(file).toLowerCase() || "none";
    const relativePath = relative(packRoot, file);
    const topDirectory = relativePath.split(/[\\/]/)[0];
    extensions[extension] = (extensions[extension] ?? 0) + 1;
    directories[topDirectory] = (directories[topDirectory] ?? 0) + 1;
    if (!copiedExtensions.has(extension)) continue;
    const destination = join(outputRoot, outputName, relativePath);
    if (file === scriptFile) {
      writeText(destination, `${printer.printFile(readableSource)}\n`);
      continue;
    }
    if (extension !== ".json") {
      mkdirSync(dirname(destination), { recursive: true });
      copyFileSync(file, destination);
      continue;
    }
    try {
      const value = JSON.parse(readFileSync(file, "utf8"));
      const item = value["minecraft:item"]?.description?.identifier;
      const entity = value["minecraft:entity"]?.description?.identifier
        ?? value["minecraft:client_entity"]?.description?.identifier;
      if (item) itemIds.add(item);
      if (entity) entityIds.add(entity);
      for (const [key, recipe] of Object.entries(value)) {
        if (key.startsWith("minecraft:recipe") && recipe?.description?.identifier) {
          recipeIds.add(recipe.description.identifier);
        }
      }
      writeJson(destination, value);
    } catch {
      mkdirSync(dirname(destination), { recursive: true });
      copyFileSync(file, destination);
    }
  }
  packInventory[outputName] = { files: files.length, extensions, directories };
}

const catalog = JSON.parse(readFileSync(join(projectRoot, "src", "main", "resources", "assets",
  "villager-news-addon-port", "dialogues.json"), "utf8"));
const javaSource = walkFiles(join(projectRoot, "src", "main", "java"))
  .filter((file) => file.endsWith(".java"))
  .map((file) => readFileSync(file, "utf8"))
  .join("\n");
const missingReferences = {};
for (const [id, group] of Object.entries(catalog.groups)) {
  if (javaSource.includes(`"${id}"`) || group.title && javaSource.includes(`"${group.title}"`)) continue;
  const symbol = [...dialogueSymbols].find(([, value]) => value === id)?.[0];
  const contexts = [];
  if (symbol) {
    const expression = new RegExp(`.{0,180}\\b${symbol}\\b.{0,180}`, "g");
    for (const match of rawScript.matchAll(expression)) contexts.push(match[0]);
  }
  missingReferences[id] = { symbol: symbol ?? "", title: group.title, contexts };
}

const settings = [
  "Show Subtitles",
  "Villager Chattiness",
  "Rare Villager Voiceines",
  "Spawn Special Villagers",
  "Villager Style",
].filter((name) => rawScript.includes(name));
const subscriptions = [...rawScript.matchAll(/(?:afterEvents|beforeEvents)\.([A-Za-z0-9_]+)\.subscribe/g)]
  .map((match) => match[1]);
const features = {
  packs: packInventory,
  dialogueGroups: Object.keys(catalog.groups).length,
  dialogueSymbols: dialogueSymbols.size,
  items: [...itemIds].sort(),
  entities: [...entityIds].sort(),
  recipes: [...recipeIds].sort(),
  settings,
  eventSubscriptions: Object.fromEntries([...new Set(subscriptions)].sort()
    .map((name) => [name, subscriptions.filter((value) => value === name).length])),
  mechanics: {
    cosmetics: rawScript.includes("p:mlxeez"),
    removableNoses: rawScript.includes("p:gcfsvg"),
    villagerSigns: rawScript.includes("p:sign"),
    specialVillagerSpawning: rawScript.includes("Spawn Special Villagers"),
    playerReputation: rawScript.includes("Player Reputation"),
    conversations: rawScript.includes("Conversations"),
  },
};

writeJson(join(outputRoot, "dialogue-symbol-map.json"), Object.fromEntries([...dialogueSymbols]
  .sort((left, right) => left[1].localeCompare(right[1]))));
writeJson(join(outputRoot, "missing-dialogue-references.json"), missingReferences);
writeJson(join(outputRoot, "feature-inventory.json"), features);

console.log(JSON.stringify({
  output: outputRoot,
  dialogueGroups: features.dialogueGroups,
  renamedDialogueSymbols: dialogueSymbols.size,
  missingJavaDialogueReferences: Object.keys(missingReferences).length,
  copiedSourceFiles: Object.values(packInventory).reduce((total, pack) => total
    + Object.entries(pack.extensions).filter(([extension]) => copiedExtensions.has(extension))
      .reduce((count, [, value]) => count + value, 0), 0),
}, null, 2));
