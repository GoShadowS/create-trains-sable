"""Renders the block models to PNG, so the art can be looked at without starting the game.

A small software renderer: every element becomes six quads, the texture is sampled through the
UV window of each face, and the result is drawn in the same isometric projection the inventory
uses. It is deliberately dumb - no lighting model beyond a fixed shade per face direction - but it
shows exactly what the block will show: right proportions, right UV windows, no stretched texture.
"""

import json
import os
import struct
import zlib

import make_models

SCALE = 7.0         # pixels per model unit (a block is 16)
GAP = 24
SHADE = {"up": 1.0, "down": 0.55, "north": 0.8, "south": 0.8, "east": 0.68, "west": 0.68}
COS30 = 0.8660254
SIN30 = 0.5


def load_png(path):
    data = open(path, "rb").read()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", path
    pos, width, height, idat = 8, 0, 0, b""
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        kind = data[pos + 4:pos + 8]
        payload = data[pos + 8:pos + 8 + length]
        if kind == b"IHDR":
            width, height = struct.unpack(">II", payload[:8])
        elif kind == b"IDAT":
            idat += payload
        pos += 12 + length
    raw = zlib.decompress(idat)
    pixels = []
    stride = width * 4 + 1
    for y in range(height):
        row = raw[y * stride + 1:(y + 1) * stride]
        pixels.append([tuple(row[x * 4:x * 4 + 4]) for x in range(width)])
    return width, height, pixels


def project(point):
    x, y, z = point
    return ((x - z) * COS30 * SCALE, ((x + z) * SIN30 - y) * SCALE)


NORMALS = {
    "up": (0, 1, 0), "down": (0, -1, 0),
    "north": (0, 0, -1), "south": (0, 0, 1),
    "east": (1, 0, 0), "west": (-1, 0, 0),
}
# The camera looks down the (1, 1, 1) axis of the model, the way the inventory does.
VIEW = (0.5773503, 0.5773503, 0.5773503)


def visible(name):
    normal = NORMALS[name]
    return normal[0] * VIEW[0] + normal[1] * VIEW[1] + normal[2] * VIEW[2] > 1.0e-6


def depth(quad):
    """Distance of a quad's centre from the camera; quads are drawn back to front."""
    cx = sum(p[0] for p in quad) / 4.0
    cy = sum(p[1] for p in quad) / 4.0
    cz = sum(p[2] for p in quad) / 4.0
    return cx * VIEW[0] + cy * VIEW[1] + cz * VIEW[2]


def faces_of(element):
    x0, y0, z0 = element["from"]
    x1, y1, z1 = element["to"]
    return {
        "down": ((x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)),
        "up": ((x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)),
        "north": ((x1, y0, z0), (x0, y0, z0), (x0, y1, z0), (x1, y1, z0)),
        "south": ((x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)),
        "west": ((x0, y0, z0), (x0, y0, z1), (x0, y1, z1), (x0, y1, z0)),
        "east": ((x1, y0, z1), (x1, y0, z0), (x1, y1, z0), (x1, y1, z1)),
    }


def draw_quad(canvas, tex, projected, uvs, shade, width, height, origin_x, origin_y):
    """Fills one quad, given its already projected screen corners."""
    u0, v0, u1, v1 = uvs
    corners = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
    xs = [p[0] for p in projected]
    ys = [p[1] for p in projected]
    min_x, max_x = int(min(xs)) - 1, int(max(xs)) + 1
    min_y, max_y = int(min(ys)) - 1, int(max(ys)) + 1
    for triangle in ((0, 1, 2), (0, 2, 3)):
        (ax, ay), (bx, by), (cx, cy) = (projected[i] for i in triangle)
        denominator = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
        if abs(denominator) < 1e-9:
            continue
        for py in range(min_y, max_y + 1):
            for px in range(min_x, max_x + 1):
                if not (0 <= px < width and 0 <= py < height):
                    continue
                w0 = ((by - cy) * (px - cx) + (cx - bx) * (py - cy)) / denominator
                w1 = ((cy - ay) * (px - cx) + (ax - cx) * (py - cy)) / denominator
                w2 = 1.0 - w0 - w1
                if w0 < -0.002 or w1 < -0.002 or w2 < -0.002:
                    continue
                u = corners[triangle[0]][0] * w0 + corners[triangle[1]][0] * w1 + corners[triangle[2]][0] * w2
                v = corners[triangle[0]][1] * w0 + corners[triangle[1]][1] * w1 + corners[triangle[2]][1] * w2
                tx = min(tex[0] - 1, max(0, int(u / 16.0 * tex[0])))
                ty = min(tex[1] - 1, max(0, int(v / 16.0 * tex[1])))
                r, g, b, a = tex[2][ty][tx]
                if a < 128:
                    continue
                canvas[py][px] = (int(r * shade), int(g * shade), int(b * shade), 255)


def bounds(model):
    xs, ys = [], []
    for element in model["elements"]:
        for quad in faces_of(element).values():
            for vertex in quad:
                px, py = project(vertex)
                xs.append(px)
                ys.append(py)
    return min(xs), min(ys), max(xs), max(ys)


def render(model, textures, width, height, origin_x, origin_y):
    """Draws the model centred on the given origin; caller passes the offset from `bounds`."""
    canvas = [[(0, 0, 0, 0) for _ in range(width)] for _ in range(height)]
    # Painter's algorithm over faces, not elements, with back faces culled: boxes overlap each
    # other in these models, so sorting whole boxes would draw the wrong one on top.
    quads = []
    for element in model["elements"]:
        for name, quad in faces_of(element).items():
            if not visible(name):
                continue
            info = element["faces"][name]
            texture_id = info["texture"].split(":")[-1].split("/")[-1]
            if texture_id not in textures or "uv" not in info:
                continue
            quads.append((depth(quad), name, quad, info["uv"], texture_id))
    quads.sort(key=lambda entry: entry[0])
    for _, name, quad, uvs, texture_id in quads:
        shifted_quad = [(origin_x + project(v)[0], origin_y + project(v)[1]) for v in quad]
        draw_quad(canvas, textures[texture_id], shifted_quad, uvs, SHADE[name], width, height,
                  origin_x, origin_y)
    return canvas


def write_png(path, rows):
    height = len(rows)
    width = len(rows[0])
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
    open(path, "wb").write(png)


def main():
    texture_dir = "src/main/resources/assets/createtrainssable/textures/block"
    textures = {}
    for name in ("truck_frame", "truck_wheel", "truck_drive", "coupling_plate", "coupling_coil"):
        textures[name] = load_png(os.path.join(texture_dir, name + ".png"))

    names = list(make_models.MODELS)
    cell = 340
    width = cell * len(names)
    height = cell
    sheet = [[(26, 28, 32, 255) for _ in range(width)] for _ in range(height)]
    for index, name in enumerate(names):
        model = make_models.MODELS[name]
        min_x, min_y, max_x, max_y = bounds(model)
        origin_x = cell / 2 - (min_x + max_x) / 2
        origin_y = cell / 2 - (min_y + max_y) / 2
        canvas = render(model, textures, cell, cell, origin_x, origin_y)
        for y in range(cell):
            for x in range(cell):
                pixel = canvas[y][x]
                if pixel[3] == 255:
                    sheet[y][index * cell + x] = pixel
    write_png("/home/user/create-trains-sable-blocks.png", sheet)
    print("wrote /home/user/create-trains-sable-blocks.png")


if __name__ == "__main__":
    main()
