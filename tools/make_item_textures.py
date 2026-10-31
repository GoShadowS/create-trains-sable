"""Draws the item sprites.

Item sprites are 16x16 and semi-transparent at the edges, the way vanilla tools are: a steel
hammer with a red-painted head and a brass band, drawn diagonally across the sprite.
"""

import os

import make_textures

SIZE = 16


def assembly_tool():
    """A steel spanner with a brass jaw: the tool that pulls a vehicle into one piece."""
    px = make_textures.canvas()
    steel = make_textures.STEEL
    steel_light = make_textures.STEEL_LIGHT
    steel_hi = make_textures.STEEL_HI
    dark = make_textures.RUST_DARK
    brass = make_textures.BRASS
    brass_hi = make_textures.BRASS_HI
    brass_dark = make_textures.BRASS_DARK

    # Shaft: two pixels wide, from the lower left to the jaw, with a highlight and a shadow.
    for step in range(10):
        x = 2 + step
        y = 13 - step
        make_textures.put(px, x, y, steel_light)
        make_textures.put(px, x + 1, y, steel)
        make_textures.put(px, x, y + 1, dark)

    # Jaw: a brass C opening up and to the right, the way a spanner is drawn.
    for y in range(1, 8):
        for x in range(9, 15):
            if (x, y) in ((9, 4), (10, 4), (11, 4), (9, 5), (10, 5), (11, 5),
                          (13, 2), (13, 3), (13, 4), (13, 5), (12, 6), (13, 6)):
                continue
            px[y][x] = brass
    for x in range(9, 14):
        make_textures.put(px, x, 1, brass_hi)
        make_textures.put(px, x, 7, brass_dark)
    make_textures.vline(px, 9, 1, 7, brass_hi)
    make_textures.vline(px, 14, 1, 7, brass_dark)

    # Collar where the jaw meets the shaft.
    for y in range(6, 9):
        for x in range(7, 10):
            px[y][x] = steel_hi
        make_textures.put(px, 9, y, dark)
    return px


ITEMS = {"assembly_tool": assembly_tool}


if __name__ == "__main__":
    target = "src/main/resources/assets/createtrainssable/textures/item"
    os.makedirs(target, exist_ok=True)
    for name, painter in ITEMS.items():
        path = os.path.join(target, name + ".png")
        make_textures.write_png(path, painter())
        print("wrote", path)
