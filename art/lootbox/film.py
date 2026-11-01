"""
A placed sea chest opening, filmed tick by tick on the same timeline as the plugin's ChestShow: it shakes harder and
harder in its tier's colour, the lid flies open on its hinge (a Void chest calls down lightning), and what was in it
comes out one thing at a time — up in a puff of smoke, a turn in the air, back down as the next comes up — and then
the empty chest shuts, wobbles, shrinks and is gone. Rendered in Blockbench on a patch of grass, then put together with
the flash, the bolt and the chat line it ends on.

    python3 film.py [tier] [vanilla-textures-dir]     (tier defaults to void; textures from the client jar)
"""
import base64
import io
import json
import math
import os
import random
import subprocess
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import lootbox  # noqa: E402
import build  # noqa: E402  (imported by lootbox from the adventurers' pipeline)

TIER = sys.argv[1] if len(sys.argv) > 1 else "void"
MC = sys.argv[2] if len(sys.argv) > 2 else "/tmp/mcassets/assets/minecraft/textures"
OUT = os.path.join(HERE, "film_" + TIER)
COLOURS = {"driftwood": (191, 186, 176), "reef": (92, 220, 130), "pearl": (110, 175, 255),
           "abyss": (190, 130, 255), "leviathan": (255, 190, 70), "void": (127, 246, 255)}
NAMES = {"driftwood": "Driftwood", "reef": "Reef", "pearl": "Pearl", "abyss": "Abyss", "leviathan": "Leviathan",
         "void": "Void"}
ORDER = list(COLOURS)

# ChestShow's timeline, in ticks
SHAKE_UNTIL = 30
LID_OPEN = 110.0
FISH = os.path.join(HERE, "..", "fish", "renders", "astral_leviathan.png")
ITEMS = [("fish", FISH), ("item", "netherite_ingot"), ("item", "golden_apple"), ("item", "totem_of_undying"),
         ("item", "elytra"), ("item", "shulker_shell"), ("item", "diamond"), ("item", "enchanted_book")]


def data_url(image):
    buffer = io.BytesIO()
    image.save(buffer, "PNG")
    return "data:image/png;base64," + base64.b64encode(buffer.getvalue()).decode().replace("/", "_")


def icon(kind, name):
    if kind == "fish":
        im = Image.open(name).convert("RGBA")
        im = im.crop(im.getbbox())
        im.thumbnail((32, 32), Image.LANCZOS)
        out = Image.new("RGBA", (32, 32))
        out.alpha_composite(im, ((32 - im.width) // 2, (32 - im.height) // 2))
        return out
    return Image.open(os.path.join(MC, "item", name + ".png")).convert("RGBA").crop((0, 0, 16, 16)) \
        .resize((32, 32), Image.NEAREST)


def solid(rgb):
    return Image.new("RGBA", (2, 2), rgb + (255,))


SETUP = """
(() => {
  const spec = SPEC;
  const fix = s => s.split('_').join('/');
  window.FILM = window.FILM || {};
  const F = window.FILM;
  F.chest = Cube.all.filter(c => !c.name.startsWith('film')).map(c => c.uuid);
  F.tex = {};
  for (const [name, url] of Object.entries(spec.textures)) {
    F.tex[name] = new Texture({name: 'film_' + name}).fromDataURL(fix(url)).add(false);
  }
  const grass = F.tex.grass, side = F.tex.side, dirt = F.tex.dirt;
  const W = Project.texture_width, H = Project.texture_height;
  for (let x = -2; x <= 2; x++) {
    for (let z = -2; z <= 2; z++) {
      const faces = {up: {uv: [0, 0, W, H], texture: grass.uuid}, down: {uv: [0, 0, W, H], texture: dirt.uuid}};
      for (const s of ['north', 'south', 'east', 'west']) faces[s] = {uv: [0, 0, W, H], texture: side.uuid};
      new Cube({name: 'film_floor', from: [x * 16, -16, z * 16], to: [x * 16 + 16, 0, z * 16 + 16], faces}).init();
    }
  }
  Canvas.updateAll();
  for (const c of Cube.all) {
    c.mesh.userData.home = {p: c.mesh.position.clone(), q: c.mesh.quaternion.clone()};
  }
  return F.chest.length;
})()
"""

FRAME = """
(() => {
  const f = FRAMESPEC;
  const F = window.FILM;
  for (const c of Cube.all.filter(c => c.name === 'film_fx')) c.remove();
  const made = [];
  for (const fx of f.fx) {
    const t = F.tex[fx.tex];
    const faces = {};
    for (const s of ['north', 'south', 'east', 'west', 'up', 'down']) faces[s] = {uv: [0, 0, Project.texture_width, Project.texture_height], texture: t.uuid};
    const h = fx.size / 2;
    const cube = new Cube({name: 'film_fx', from: [fx.x - h, fx.y - h, fx.z - (fx.flat ? 0.05 : h)],
      to: [fx.x + h, fx.y + h, fx.z + (fx.flat ? 0.05 : h)], origin: [fx.x, fx.y, fx.z], faces}).init();
    made.push([cube, fx.yaw || 0]);
  }
  Canvas.updateAll();
  for (const [cube, yaw] of made) {
    cube.mesh.rotation.y = yaw;
  }
  const centre = new THREE.Vector3(8, 0, 8);
  const hinge = new THREE.Vector3(8, 9, 13);
  const turn = new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0, 0, 1), f.shake * Math.PI / 180);
  const swing = new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(1, 0, 0), f.open * Math.PI / 180);
  for (const id of F.chest) {
    const cube = Cube.all.find(c => c.uuid === id);
    if (!cube) continue;
    const mesh = cube.mesh, home = mesh.userData.home;
    let p = home.p.clone(), q = home.q.clone();
    if (cube.name.startsWith('lid')) {
      p = p.sub(hinge).applyQuaternion(swing).add(hinge);
      q = swing.clone().multiply(q);
    }
    p = p.sub(centre).multiplyScalar(f.scale).applyQuaternion(turn).add(centre);
    p.y += f.lift;
    mesh.position.copy(p);
    mesh.quaternion.copy(turn.clone().multiply(q));
    mesh.scale.set(f.scale, f.scale, f.scale);
  }
  return made.length;
})()
"""


def timeline(items):
    """ChestShow's timeline: a placed chest shakes, opens, gives up its things one at a time, shuts, wobbles, goes."""
    shake_until, open_for, rise, turn_step, sink, wobble, vanish = 30, 6, 8, 4, 7, 12, 10
    each = rise + 3 * turn_step
    reveal = shake_until + open_for
    last = reveal + len(items) * each
    wobble_at = last + sink
    vanish_at = wobble_at + wobble
    total = vanish_at + vanish + 14
    rng = random.Random(4)
    motes = []
    frames = []
    showing = None  # (index, born)
    sinking = []    # (index, started)
    for tick in range(total):
        scale, lift, shake = 1.0, 0.0, 0.0
        open_angle = 0.0
        if 0 < tick < shake_until:
            strength = tick / shake_until
            shake = (1 if (tick // 2) % 2 == 0 else -1) * (2 + 8 * strength)
            lift = 0.8 * strength
            for _ in range(2 + int(10 * strength)):
                motes.append([rng.uniform(1, 15), rng.uniform(1, 13), rng.uniform(1, 15), rng.uniform(-0.1, 0.1),
                              rng.uniform(0.2, 0.5), rng.uniform(-0.1, 0.1), 14, "tier", 0.55])
        if shake_until <= tick < wobble_at:
            open_angle = LID_OPEN * min(1.0, (tick - shake_until) / open_for)
        if tick == shake_until:
            for _ in range(70):
                angle = rng.uniform(0, math.tau)
                speed = rng.uniform(0.3, 1.1)
                motes.append([8, 12, 8, math.cos(angle) * speed, rng.uniform(0.4, 1.4), math.sin(angle) * speed,
                              22, rng.choice(["tier", "white", "purple"]), 0.55])
        fx = []
        if reveal <= tick <= last:
            since = tick - reveal
            index, phase = since // each, since % each
            if phase == 0:
                if showing is not None:
                    sinking.append((showing[0], tick))
                showing = (index, tick) if index < len(items) else None
                if showing is not None:
                    for _ in range(10):
                        motes.append([rng.uniform(6, 10), 10, rng.uniform(6, 10), rng.uniform(-0.05, 0.05),
                                      rng.uniform(0.15, 0.35), rng.uniform(-0.05, 0.05), 18, "smoke", 1.2])
            elif showing is not None and phase % 3 == 0:
                motes.append([8 + rng.uniform(-0.6, 0.6), 10 + phase * 0.6, 8 + rng.uniform(-0.6, 0.6), 0, 0.15, 0, 12,
                              "smoke", 0.9])
        if wobble_at <= tick < vanish_at:
            step = (tick - wobble_at) // 2
            shake = (1 if step % 2 == 0 else -1) * 6
        if tick >= vanish_at:
            scale = max(0.05, 1 - (tick - vanish_at) / vanish)
            if tick == vanish_at:
                for _ in range(30):
                    motes.append([rng.uniform(3, 13), rng.uniform(2, 12), rng.uniform(3, 13), rng.uniform(-0.1, 0.1),
                                  rng.uniform(0.05, 0.25), rng.uniform(-0.1, 0.1), 16, "smoke", 1.4])
        # the thing coming up, turning; the one before sinking back
        if showing is not None:
            age = tick - showing[1]
            up = min(1.0, age / rise)
            ease = 1 - (1 - up) ** 2
            turned = max(0, age - rise) / (3 * turn_step) * math.tau
            fx.append({"x": 8, "y": 12 + 21.6 * ease, "z": 8, "size": 3 + 6 * ease, "tex": "item%d" % showing[0],
                       "flat": True, "yaw": CAMERA_YAW + turned})
        still = []
        for index, started in sinking:
            age = tick - started
            if age <= sink:
                down = age / sink
                fx.append({"x": 8, "y": 12 + 21.6 * (1 - down), "z": 8, "size": 9 - 5 * down,
                           "tex": "item%d" % index, "flat": True, "yaw": CAMERA_YAW})
                still.append((index, started))
        sinking = still
        alive = []
        for mote in motes:
            mote[0] += mote[3]
            mote[1] += mote[4]
            mote[2] += mote[5]
            mote[4] -= 0.01 if mote[7] != "smoke" else 0
            mote[6] -= 1
            if mote[6] > 0:
                alive.append(mote)
                fx.append({"x": mote[0], "y": mote[1], "z": mote[2], "size": mote[8], "tex": mote[7]})
        motes = alive
        frames.append({"scale": scale, "lift": lift, "shake": shake, "open": open_angle, "fx": fx,
                       "flash": tick in (shake_until, shake_until + 1) and TIER == "void", "tick": tick,
                       "done": tick > vanish_at})
    return frames


CAMERA = (16, 34, -58)
TARGET = (0, 19, 0)
CAMERA_YAW = -math.atan2(CAMERA[0], -CAMERA[2])


def main():
    os.makedirs(OUT, exist_ok=True)
    lootbox.make(TIER)
    build.eval_js("(() => { Modes.options.edit.select(); return 1; })()")
    colour = COLOURS[TIER]
    textures = {"grass": data_url(tinted(os.path.join(MC, "block", "grass_block_top.png"), (124, 189, 107))),
                "side": data_url(Image.open(os.path.join(MC, "block", "grass_block_side.png")).convert("RGBA")),
                "dirt": data_url(Image.open(os.path.join(MC, "block", "dirt.png")).convert("RGBA")),
                "tier": data_url(solid(colour)), "white": data_url(solid((240, 250, 255))),
                "purple": data_url(solid((170, 90, 240))), "smoke": data_url(solid((150, 150, 158)))}
    for n, (kind, name) in enumerate(ITEMS):
        textures["item%d" % n] = data_url(icon(kind, name))
    build.eval_js(SETUP.replace("SPEC", json.dumps({"textures": textures})))
    import time
    time.sleep(1.5)
    frames = timeline(ITEMS)
    for frame in frames:
        build.eval_js(FRAME.replace("FRAMESPEC", json.dumps({key: frame[key] for key in
                                                            ("scale", "lift", "shake", "open", "fx")})))
        lootbox.shot(os.path.join(OUT, "r%03d.png" % frame["tick"]), CAMERA, TARGET, fov=52, size=720)
    compose(frames)


def tinted(path, rgb):
    im = Image.open(path).convert("RGBA")
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            grey = (r + g + b) / 3 / 255
            px[x, y] = (int(rgb[0] * grey), int(rgb[1] * grey), int(rgb[2] * grey), a)
    return im


def compose(frames):
    sys.path.insert(0, os.path.join(HERE, "..", "guis"))
    import render as mcfont  # the Minecraft bitmap font, from the GUI renderer
    size = 720
    sky = Image.new("RGBA", (size, size))
    draw = ImageDraw.Draw(sky)
    for y in range(size):
        t = y / size
        draw.line([(0, y), (size, y)], fill=(int(14 + 30 * t), int(18 + 34 * t), int(40 + 50 * t), 255))
    rng = random.Random(9)
    for _ in range(90):
        x, y = rng.randrange(size), rng.randrange(int(size * 0.55))
        c = rng.randrange(150, 255)
        draw.point((x, y), fill=(c, c, c, 255))
    out = []
    for frame in frames:
        shot = Image.open(os.path.join(OUT, "r%03d.png" % frame["tick"])).convert("RGBA").resize((size, size))
        scene = sky.copy()
        scene.alpha_composite(shot)
        if frame["flash"]:
            bolt = Image.new("RGBA", (size, size))
            d = ImageDraw.Draw(bolt)
            x, y = size * 0.5, 0
            points = [(x, y)]
            r = random.Random(frame["tick"])
            while y < size * 0.62:
                y += r.uniform(30, 60)
                x += r.uniform(-40, 40)
                points.append((x, y))
            d.line(points, fill=(230, 240, 255, 255), width=7)
            d.line(points, fill=(255, 255, 255, 255), width=3)
            scene.alpha_composite(bolt)
            scene = Image.blend(scene, Image.new("RGBA", (size, size), (235, 245, 255, 255)),
                                0.55 if frame["tick"] == SHAKE_UNTIL else 0.25)
        if frame.get("done"):
            chat = Image.new("RGBA", (360, 30), (0, 0, 0, 0))
            cd = ImageDraw.Draw(chat)
            cd.rectangle((0, 0, 360, 30), fill=(0, 0, 0, 110))
            mcfont.draw_text(chat, 3, 2, "...and 20,000 coins, straight to your balance.", (85, 255, 85))
            mcfont.draw_text(chat, 3, 16, "The " + NAMES[TIER] + " Chest held 8 thing(s), now in your bag.",
                             (85, 255, 85))
            chat = chat.resize((720, 60), Image.NEAREST)
            scene.alpha_composite(chat, (6, size - 72))
        out.append(scene.convert("RGB"))
    gif = os.path.join(HERE, "opening_film_" + TIER + ".gif")
    out[0].save(gif, save_all=True, append_images=out[1:] + [out[-1]] * 20, duration=50, loop=0)
    for n, image in enumerate(out):
        image.save(os.path.join(OUT, "c%03d.png" % n))
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-framerate", "20", "-i", os.path.join(OUT, "c%03d.png"),
                    "-vf", "tpad=stop_mode=clone:stop_duration=1", "-pix_fmt", "yuv420p",
                    os.path.join(HERE, "opening_film_" + TIER + ".mp4")], check=True)
    print(gif)


if __name__ == "__main__":
    main()
