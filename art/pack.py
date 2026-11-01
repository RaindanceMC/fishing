"""
Puts the fishing looks into the module's resource pack: the built fish, dishes, sea chests, ripple and bobbers under
assets/rainsfishing, an item definition for each, and files.txt — the list FishingPack hands to Core.

Run the art scripts first (fish/fish.py, fish/dishes3d.py, rods/rods.py, lootbox/lootbox.py, fishing_spot/spot.py,
fishing_spot/bobber.py); this only copies what they wrote.

    python3 pack.py
"""
import json
import os
import shutil

HERE = os.path.dirname(os.path.abspath(__file__))
PACK = os.path.join(HERE, "..", "fishing-module", "src", "main", "resources", "de", "raindancer", "modules",
                    "fishing", "pack")
NS = "rainsfishing"
ASSETS = os.path.join(PACK, "assets", NS)


def copy(source, target):
    target = os.path.join(ASSETS, target)
    os.makedirs(os.path.dirname(target), exist_ok=True)
    shutil.copyfile(os.path.join(HERE, source), target)


def item(name, model, tints=None):
    definition = {"model": {"type": "minecraft:model", "model": NS + ":" + model}}
    if tints:
        definition["model"]["tints"] = tints
    path = os.path.join(ASSETS, "items", name + ".json")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as out:
        json.dump(definition, out, indent=1)


def main():
    if os.path.isdir(os.path.join(PACK, "assets")):
        shutil.rmtree(os.path.join(PACK, "assets"))
    catalog = json.load(open(os.path.join(HERE, "fish", "catalog.json")))
    shutil.copyfile(os.path.join(HERE, "fish", "catalog.json"),
                    os.path.join(PACK, "..", "catalog.json"))

    for fish in catalog["fish"]:
        fid = fish["id"]
        copy("fish/textures/%s.png" % fid, "textures/item/fish/%s.png" % fid)
        copy("fish/models/%s.json" % fid, "models/item/fish/%s.json" % fid)
        item("fish/" + fid, "item/fish/" + fid)

    for dish in catalog["dishes"]:
        did = dish["id"]
        copy("fish/dishes/%s.png" % did, "textures/item/dish/%s.png" % did)
        copy("fish/dishes/models/%s.json" % did, "models/item/dish/%s.json" % did)
        item("dish/" + did, "item/dish/" + did)
        copy("fish/dishes/raw_%s.png" % did, "textures/item/dish/raw_%s.png" % did)
        copy("fish/dishes/models/raw_%s.json" % did, "models/item/dish/raw_%s.json" % did)
        item("dish/raw_" + did, "item/dish/raw_" + did)

    for chest in ("driftwood", "reef", "pearl", "abyss", "leviathan", "void"):
        copy("lootbox/textures/%s.png" % chest, "textures/block/lootbox/%s.png" % chest)
        for part in ("", "_base", "_lid"):
            copy("lootbox/models/%s%s.json" % (chest, part), "models/item/chest/%s%s.json" % (chest, part))
            item("chest/%s%s" % (chest, part), "item/chest/%s%s" % (chest, part))

    for rod in ("reed", "reef", "pearl", "ember", "leviathan", "void"):
        for suffix in ("", "_cast"):
            copy("rods/textures/%s%s.png" % (rod, suffix), "textures/item/rod/%s%s.png" % (rod, suffix))
            copy("rods/models/%s%s.json" % (rod, suffix), "models/item/rod/%s%s.json" % (rod, suffix))
        copy("rods/items/%s.json" % rod, "items/rod/%s.json" % rod)

    copy("fishing_spot/textures/ripple.png", "textures/block/fishing_spot/ripple.png")
    copy("fishing_spot/textures/ripple.png.mcmeta", "textures/block/fishing_spot/ripple.png.mcmeta")
    copy("fishing_spot/models/ripple.json", "models/block/fishing_spot/ripple.json")
    item("spot/ripple", "block/fishing_spot/ripple",
         [{"type": "minecraft:custom_model_data", "index": 0, "default": 0xFFFFFF}])
    copy("fishing_spot/models/bobber.json", "models/item/fishing_spot/bobber.json")
    for kind in ("river", "ocean", "warm_ocean", "swamp", "frozen", "cave", "void"):
        copy("fishing_spot/textures/bobber_%s.png" % kind, "textures/item/fishing_spot/bobber_%s.png" % kind)
        copy("fishing_spot/models/bobber_%s.json" % kind, "models/item/fishing_spot/bobber_%s.json" % kind)
        item("spot/bobber_" + kind, "item/fishing_spot/bobber_" + kind)

    with open(os.path.join(PACK, "pack.mcmeta"), "w") as out:
        json.dump({"pack": {"description": "Rain's Fishing — fish, dishes, sea chests and fishing spots", "min_format": 88, "max_format": 97}}, out, indent=1)
    files = []
    for root, _, names in os.walk(PACK):
        for name in sorted(names):
            path = os.path.relpath(os.path.join(root, name), PACK)
            if path != "files.txt":
                files.append(path.replace(os.sep, "/"))
    with open(os.path.join(PACK, "files.txt"), "w") as out:
        out.write("\n".join(sorted(files)) + "\n")
    print(len(files), "files in the pack")


if __name__ == "__main__":
    main()
