"""Builds the block models of the rail blocks.

Every face gets a UV rectangle computed from the size of the box it belongs to, so a texture is
never stretched: a 4x11 wheel face shows a 4x11 window of its texture, not the whole square. This
is what keeps the art readable on a small block.
"""

import json
import os

TEX = "createtrainssable:block/"


def face(texture, uv):
    return {"texture": TEX + texture, "uv": [round(v, 2) for v in uv]}


def cube(bounds, textures, uvs=None, rotation=None):
    """A box from `bounds` with per-face textures and UV windows.

    `uvs` maps a face name to a [u1, v1, u2, v2] window; missing faces get the full texture.
    """
    x0, y0, z0, x1, y1, z1 = bounds
    size_x, size_y, size_z = x1 - x0, y1 - y0, z1 - z0
    faces = {}
    default = {
        "down": (0, 0, size_x, size_z),
        "up": (0, 0, size_x, size_z),
        "north": (0, 0, size_x, size_y),
        "south": (0, 0, size_x, size_y),
        "west": (0, 0, size_z, size_y),
        "east": (0, 0, size_z, size_y),
    }
    for name in ("down", "up", "north", "south", "west", "east"):
        texture = textures.get(name, textures["all"])
        faces[name] = face(texture, (uvs or {}).get(name, default[name]))
    element = {"from": [x0, y0, z0], "to": [x1, y1, z1], "faces": faces}
    if rotation is not None:
        element["rotation"] = rotation
    return element


def truck(driven):
    elements = [
        # Solebar: the plate the truck is built around, 1..15 across and 2..9 up.
        cube((1, 2, 1, 15, 9, 15), {"all": "truck_frame"}),
        # Wheels: a 10x10 square plate on each side of the axle, so the wheel texture maps onto
        # the face one to one and a 16x16 wheel stays round instead of being squashed.
        cube((0, 0, 3, 2, 10, 13),
             {"all": "truck_frame", "east": "truck_wheel", "west": "truck_wheel"}),
        cube((14, 0, 3, 16, 10, 13),
             {"all": "truck_frame", "east": "truck_wheel", "west": "truck_wheel"}),
        # Axle between the wheels.
        cube((4, 4, 7, 12, 6, 9), {"all": "truck_frame"}),
    ]
    if driven:
        # Motor housing: only the driven axle has one, so the two trucks never look alike.
        elements.append(cube((3, 9, 4, 13, 12, 12), {"all": "truck_drive"}))
        elements.append(cube((7, 12, 6, 9, 14, 10), {"all": "truck_drive"}))
    else:
        # Idler: a bolted cover instead.
        elements.append(cube((3, 9, 4, 13, 10, 12), {"all": "truck_frame"}))
        elements.append(cube((6, 10, 7, 10, 11, 10), {"all": "truck_frame"}))
    return {
        "parent": "minecraft:block/block",
        "textures": {},
        "elements": elements,
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
            "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0],
                                      "scale": [0.375, 0.375, 0.375]},
        },
    }


def coupling():
    return {
        "parent": "minecraft:block/block",
        "textures": {},
        "elements": [
            # Buffer head, facing the vehicle in front.
            cube((3, 2, 7, 13, 10, 13), {"all": "coupling_plate"}),
            # Drawbar reaching out of the head.
            cube((7, 5, 12, 9, 8, 16), {"all": "coupling_plate"}),
            # Coil spring: four turns between the buffer head and the vehicle, offset so the
            # silhouette is a spring and not a slab.
            cube((5, 1, 3, 11, 3, 8), {"all": "coupling_coil"}),
            cube((4, 3, 3, 12, 5, 8), {"all": "coupling_coil"}),
            cube((5, 5, 3, 11, 7, 8), {"all": "coupling_coil"}),
            cube((4, 7, 3, 12, 9, 8), {"all": "coupling_coil"}),
        ],
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
            "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0],
                                      "scale": [0.375, 0.375, 0.375]},
        },
    }


MODELS = {
    "traction_truck": truck(True),
    "rolling_truck": truck(False),
    "spring_coupling": coupling(),
}


if __name__ == "__main__":
    target = "src/main/resources/assets/createtrainssable/models/block"
    os.makedirs(target, exist_ok=True)
    for name, model in MODELS.items():
        path = os.path.join(target, name + ".json")
        with open(path, "w") as handle:
            json.dump(model, handle, indent=2)
            handle.write("\n")
        print("wrote", path)
