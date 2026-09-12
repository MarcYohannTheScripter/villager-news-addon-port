import { mkdirSync, readFileSync, readdirSync, statSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const catalog = JSON.parse(readFileSync(join(root, "src/main/resources/assets/villager-news-addon-port/dialogues.json"), "utf8"));

function walk(directory) {
  return readdirSync(directory).flatMap((name) => {
    const path = join(directory, name);
    return statSync(path).isDirectory() ? walk(path) : [path];
  });
}

const java = walk(join(root, "src/main/java"))
  .filter((path) => path.endsWith(".java"))
  .map((path) => readFileSync(path, "utf8"))
  .join("\n");
const unreferenced = Object.entries(catalog.groups)
  .filter(([id, group]) => !java.includes(`"${id}"`) && (!group.title || !java.includes(`"${group.title}"`)))
  .map(([id, group]) => ({ id, speaker: group.speaker, title: group.title, variants: group.variants.length }));
const bySpeaker = Object.groupBy(unreferenced, (group) => group.speaker);
const report = unreferenced.map((group) => `${group.id} | ${group.speaker} | ${group.title}`).join("\n");
mkdirSync(join(root, "build"), { recursive: true });
writeFileSync(join(root, "build/unreferenced-dialogues.txt"), `${report}\n`);
console.log(JSON.stringify({
  total: Object.keys(catalog.groups).length,
  referenced: Object.keys(catalog.groups).length - unreferenced.length,
  unreferenced: unreferenced.length,
  bySpeaker: Object.fromEntries(Object.entries(bySpeaker).map(([speaker, groups]) => [speaker, groups.length])),
}, null, 2));
