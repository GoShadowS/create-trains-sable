"""Generates the block textures of the rail system.

The art direction is a low, plank-like bogey frame seen from the side: a steel plate with
rivets, a black wheel with a bright rim, a hot red drive housing for the driven axle, a cast
buffer plate for the coupling and a coil spring for the coupler. Everything is drawn from
scratch here - no reference image is read or copied.
"""

import os
import struct
import zlib

SIZE = 16

STEEL_DARK = (54, 59, 68, 255)
STEEL = (104, 111, 122, 255)
STEEL_MID = (126, 134, 146, 255)
STEEL_LIGHT = (158, 167, 180, 255)
STEEL_HI = (200, 208, 220, 255)
RUST_DARK = (38, 40, 46, 255)
BLACK = (24, 24, 28, 255)
TYRE = (38, 39, 44, 255)
TYRE_HI = (72, 74, 82, 255)
BRASS = (198, 152, 58, 255)
BRASS_DARK = (132, 96, 30, 255)
BRASS_HI = (232, 196, 108, 255)
RED = (172, 54, 40, 255)
RED_DARK = (116, 32, 24, 255)
RED_HI = (212, 94, 70, 255)
SPRING = (176, 184, 196, 255)
SPRING_SHADOW = (66, 72, 82, 255)


def canvas():
    return [[(0, 0, 0, 0) for _ in range(SIZE)] for _ in range(SIZE)]


def put(px, x, y, colour):
    if 0 <= x < SIZE and 0 <= y < SIZE:
        px[y][x] = colour


def rect(px, x0, y0, x1, y1, colour):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(px, x, y, colour)


def frame(px, x0, y0, x1, y1, colour):
    for x in range(x0, x1 + 1):
        put(px, x, y0, colour)
        put(px, x, y1, colour)
    for y in range(y0, y1 + 1):
        put(px, x0, y, colour)
        put(px, x1, y, colour)


def hline(px, y, x0, x1, colour):
    for x in range(x0, x1 + 1):
        put(px, x, y, colour)


def vline(px, x, y0, y1, colour):
    for y in range(y0, y1 + 1):
        put(px, x, y, colour)


def disc(px, cx, cy, radius, colour):
    for y in range(SIZE):
        for x in range(SIZE):
            dx = x + 0.5 - cx
            dy = y + 0.5 - cy
            if dx * dx + dy * dy <= radius * radius:
                put(px, x, y, colour)


def ring(px, cx, cy, inner, outer, colour):
    for y in range(SIZE):
        for x in range(SIZE):
            dx = x + 0.5 - cx
            dy = y + 0.5 - cy
            distance = dx * dx + dy * dy
            if inner * inner < distance <= outer * outer:
                put(px, x, y, colour)


def grain(px, rows, colour):
    """A one pixel brush line every ``rows`` rows, the way a milled plate reads."""
    for y in range(0, SIZE, rows):
        hline(px, y, 0, SIZE - 1, colour)


def write_png(path, px):
    raw = b""
    for row in px:
        raw += b"\x00" + b"".join(struct.pack("BBBB", *pixel) for pixel in row)

    def chunk(kind, payload):
        body = kind + payload
        return struct.pack(">I", len(payload)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as handle:
        handle.write(png)


def truck_frame():
    """Side frame: a steel plate with rivets and a recessed waist."""
    px = canvas()
    rect(px, 0, 0, SIZE - 1, SIZE - 1, STEEL)
    grain(px, 4, STEEL_MID)
    hline(px, 0, 0, SIZE - 1, STEEL_LIGHT)
    hline(px, 1, 0, SIZE - 1, STEEL_MID)
    hline(px, SIZE - 2, 0, SIZE - 1, STEEL_DARK)
    hline(px, SIZE - 1, 0, SIZE - 1, RUST_DARK)

    # Waist of the frame: a shallower plate between two flanges.
    rect(px, 2, 5, 13, 10, STEEL_MID)
    hline(px, 5, 2, 13, STEEL_DARK)
    hline(px, 6, 2, 13, STEEL_LIGHT)
    hline(px, 10, 2, 13, STEEL_DARK)
    vline(px, 2, 5, 10, STEEL_DARK)
    vline(px, 13, 5, 10, STEEL_DARK)

    for x, y in ((2, 2), (8, 2), (13, 2), (2, 13), (8, 13), (13, 13)):
        put(px, x, y, STEEL_HI)
        put(px, x, y - 1, RUST_DARK)
    return px


def truck_wheel():
    """A wheel seen from the side: black tyre, bright flange, steel face, brass axle nut."""
    px = canvas()
    rect(px, 0, 0, SIZE - 1, SIZE - 1, TYRE)
    # Flange: the bright ring that actually touches the rail.
    ring(px, 8.0, 8.0, 5.6, 7.4, STEEL_LIGHT)
    ring(px, 8.0, 8.0, 7.4, 8.6, STEEL)
    # Steel face inside the flange, darker than the flange so the ring reads.
    disc(px, 8.0, 8.0, 5.6, STEEL_DARK)
    # Four spokes.
    for step in range(-4, 5):
        put(px, 8 + step, 8, STEEL_MID)
        put(px, 8, 8 + step, STEEL_MID)
    # Hub with the axle nut.
    disc(px, 8.0, 8.0, 2.2, STEEL)
    ring(px, 8.0, 8.0, 1.4, 2.2, STEEL_HI)
    put(px, 8, 8, BRASS)
    put(px, 8, 7, BRASS_HI)
    put(px, 7, 8, BRASS_DARK)
    return px


def truck_drive():
    """Drive housing: red machine casing with cooling fins and a brass shaft boss."""
    px = canvas()
    rect(px, 0, 0, SIZE - 1, SIZE - 1, RED)
    for y in range(2, SIZE - 1, 4):
        hline(px, y, 0, SIZE - 1, RED_DARK)
        hline(px, y + 1, 0, SIZE - 1, RED_HI)
    hline(px, 0, 0, SIZE - 1, RED_HI)
    hline(px, SIZE - 1, 0, SIZE - 1, RED_DARK)
    for x in (0, SIZE - 1):
        vline(px, x, 0, SIZE - 1, RED_DARK)
    disc(px, 8.0, 8.0, 3.4, RUST_DARK)
    disc(px, 8.0, 8.0, 2.6, BRASS)
    ring(px, 8.0, 8.0, 1.4, 2.6, BRASS)
    put(px, 8, 8, BRASS_HI)
    put(px, 7, 7, BRASS_HI)
    put(px, 9, 9, BRASS_DARK)
    return px


def coupling_plate():
    """Buffer plate: cast head with four bolts and the drawbar slot."""
    px = canvas()
    rect(px, 0, 0, SIZE - 1, SIZE - 1, STEEL_DARK)
    grain(px, 5, STEEL)
    rect(px, 1, 1, SIZE - 2, SIZE - 2, STEEL)
    frame(px, 1, 1, SIZE - 2, SIZE - 2, STEEL_DARK)
    disc(px, 8.0, 8.0, 5.4, STEEL_MID)
    ring(px, 8.0, 8.0, 4.4, 5.4, STEEL_LIGHT)
    ring(px, 8.0, 8.0, 3.0, 4.4, STEEL)
    rect(px, 4, 7, 11, 8, RUST_DARK)
    hline(px, 7, 4, 11, TYRE)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        put(px, x, y, BRASS)
        put(px, x + 1, y, BRASS_DARK) if x < 8 else put(px, x - 1, y, BRASS_DARK)
    return px


def coupling_coil():
    """Coil spring: bright wire on a dark well, with a steel cap on each side."""
    px = canvas()
    rect(px, 0, 0, SIZE - 1, SIZE - 1, SPRING_SHADOW)
    for y in range(SIZE):
        for x in range(SIZE):
            band = (x + y) % 8
            if band < 3:
                put(px, x, y, SPRING)
            elif band == 3:
                put(px, x, y, STEEL_LIGHT)
            elif band == 5:
                put(px, x, y, STEEL_DARK)
    for x in (0, 1, SIZE - 2, SIZE - 1):
        vline(px, x, 0, SIZE - 1, STEEL)
    vline(px, 0, 0, SIZE - 1, STEEL_LIGHT)
    vline(px, SIZE - 1, 0, SIZE - 1, STEEL_DARK)
    hline(px, 0, 0, SIZE - 1, STEEL_LIGHT)
    hline(px, SIZE - 1, 0, SIZE - 1, STEEL_DARK)
    return px


TEXTURES = {
    "truck_frame": truck_frame,
    "truck_wheel": truck_wheel,
    "truck_drive": truck_drive,
    "coupling_plate": coupling_plate,
    "coupling_coil": coupling_coil,
}


def preview(path, scale=16, gap=10):
    names = list(TEXTURES)
    images = [TEXTURES[name]() for name in names]
    width = len(images) * SIZE * scale + (len(images) + 1) * gap
    height = SIZE * scale + 2 * gap
    rows = [[(32, 34, 38, 255) for _ in range(width)] for _ in range(height)]
    for index, image in enumerate(images):
        left = gap + index * (SIZE * scale + gap)
        for y in range(SIZE):
            for x in range(SIZE):
                pixel = image[y][x]
                if pixel[3] == 0:
                    pixel = (32, 34, 38, 255)
                for dy in range(scale):
                    for dx in range(scale):
                        rows[gap + y * scale + dy][left + x * scale + dx] = pixel

    raw = b""
    for row in rows:
        raw += b"\x00" + b"".join(struct.pack("BBBB", *pixel) for pixel in row)

    def chunk(kind, payload):
        body = kind + payload
        return struct.pack(">I", len(payload)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as handle:
        handle.write(png)


if __name__ == "__main__":
    target = "src/main/resources/assets/createtrainssable/textures/block"
    os.makedirs(target, exist_ok=True)
    for name, painter in TEXTURES.items():
        write_png(os.path.join(target, name + ".png"), painter())
        print("wrote", os.path.join(target, name + ".png"))
    preview("/home/user/create-trains-sable-textures.png")
    print("wrote preview")
