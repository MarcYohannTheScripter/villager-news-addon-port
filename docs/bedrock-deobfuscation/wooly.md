# Wooly Bedrock source map

Run `node tools/deobfuscate-wooly.mjs` after extracting the original add-on into
`build/bedrock-source`. It writes readable copies of Wooly's client entity,
behavior entity, geometry, and three render controllers to
`build/deobfuscated-bedrock-source/wooly` without changing the original files.

The original entity identifier `oreville_vn:mlkxjo` is Wooly. Its main geometry
is `geometry.oreville_vn.-650401518`, its texture is `diw`, and its main render
controller is `controller.render.oreville_vn.mlkxjo`.

The `oggd*` bones are fleece shells. Bedrock hides them when `q.is_sheared` is
true, leaving the skin body, head, legs, face, eyelids, and mouth visible.

| Original bone | Meaning |
| --- | --- |
| `46fljga5` | Skin body |
| `oggd_46fljga5` | Body fleece |
| `k966h_head` | Skin head |
| `oggd_head` | Head fleece |
| `3dafc` | Eyelids |
| `7246gn6jd2q` | Neutral face |
| `l66l9` | Closed-mouth controller |
| `l66l9lgh` | Upper lip |
| `l66l93gllge` | Lower lip |
| `egml9` | Open mouth |
| `d680`, `d681` | Right and left hind skin legs |
| `d682`, `d683` | Right and left front skin legs |
| `oggd_d680` through `oggd_d683` | Leg fleece shells |

Bedrock renders `diw` with its built-in `sheep` material. In that texture, the
wool uses alpha 255 while the face, skin, and hooves use alpha 3. The low alpha
is a sheep material/dye mask in Bedrock; ordinary Java rendering interprets it
as opacity. The Java port therefore converts every nonzero alpha value in
`diw.png` to 255 while preserving alpha 0 background pixels.
