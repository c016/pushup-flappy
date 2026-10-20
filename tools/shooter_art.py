"""Generates the original pixel art for the push-up shooter (a horizontal shooter in the style of
80s arcade games).

Run with: python3 tools/shooter_art.py  (needs Pillow). Writes shooter_*.png into
app/src/main/res/drawable-nodpi. Shapes are drawn procedurally and outlined automatically.
"""
import os
from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'app', 'src', 'main', 'res', 'drawable-nodpi')
C = {
    'k': (24, 20, 40),      # outline
    'w': (240, 244, 255), 'G': (170, 180, 205), 'D': (90, 96, 120),
    'b': (90, 210, 255), 'c': (200, 245, 255),
    'B': (60, 100, 220), 'N': (40, 60, 150),
    'f': (255, 140, 30), 'F': (255, 236, 120),
    'O': (255, 120, 20), 'Y': (255, 240, 140), 'o': (200, 60, 10),
    'r': (230, 40, 50), 'R': (255, 150, 150), 'm': (150, 20, 40),
    'g': (60, 200, 90), 'h': (170, 255, 170), 'e': (30, 120, 60),
    'p': (170, 80, 220), 'P': (220, 170, 255), 'q': (90, 40, 140),
    'u': (60, 140, 255), 'U': (180, 220, 255),
    'x': (255, 255, 255),
}


def grid(w, h):
    return [['.'] * w for _ in range(h)]


def ellipse(g, cx, cy, rx, ry, ch, cond=None):
    for y in range(len(g)):
        for x in range(len(g[0])):
            if ((x + .5 - cx) / rx) ** 2 + ((y + .5 - cy) / ry) ** 2 <= 1 and (cond is None or cond(x, y)):
                g[y][x] = ch


def polygon(g, pts, ch):
    def inside(px, py):
        n, res, j = len(pts), False, len(pts) - 1
        for i in range(n):
            xi, yi = pts[i]
            xj, yj = pts[j]
            if (yi > py) != (yj > py) and px < (xj - xi) * (py - yi) / (yj - yi) + xi:
                res = not res
            j = i
        return res
    for y in range(len(g)):
        for x in range(len(g[0])):
            if inside(x + .5, y + .5):
                g[y][x] = ch


def rect(g, x0, y0, x1, y1, ch):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            g[y][x] = ch


def outline(g):
    h, w = len(g), len(g[0])
    out = [r[:] for r in g]
    for y in range(h):
        for x in range(w):
            if g[y][x] == '.' and any(0 <= y + dy < h and 0 <= x + dx < w and g[y + dy][x + dx] not in '.k'
                                      for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out[y][x] = 'k'
    return out


def save(g, name):
    h, w = len(g), len(g[0])
    im = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    for y in range(h):
        for x in range(w):
            if g[y][x] != '.':
                im.putpixel((x, y), C[g[y][x]] + (255,))
    im.save(os.path.join(OUT, name + '.png'))


def ship(flame):
    g = grid(34, 16)
    polygon(g, [(8, 9), (12, 3), (17, 3), (15, 7)], 'B')         # upper wing
    polygon(g, [(8, 9), (12, 15), (17, 15), (15, 11)], 'N')       # lower wing
    polygon(g, [(7, 6), (24, 6), (33, 9), (24, 12), (7, 12)], 'G')  # fuselage
    polygon(g, [(7, 6), (24, 6), (33, 9), (7, 9)], 'w')
    ellipse(g, 21, 8, 3.5, 1.8, 'b')
    ellipse(g, 21.5, 7.6, 1.6, 0.8, 'c')
    rect(g, 7, 7, 8, 11, 'D')
    g = outline(g)
    ellipse(g, 4.5, 9.5, flame, 1.6, 'f', lambda x, y: x < 7)
    ellipse(g, 5.5, 9.5, flame * 0.55, 0.9, 'F', lambda x, y: x < 7)
    return g


def option(r):
    g = grid(12, 12)
    ellipse(g, 6, 6, r, r, 'O')
    ellipse(g, 6, 6, r * 0.55, r * 0.55, 'Y')
    ellipse(g, 5, 5, 1.2, 1.2, 'x')
    return outline(g)


def fan(spin):
    g = grid(16, 16)
    ellipse(g, 8, 8, 6.5, 6.5, 'u')
    for i in range(-5, 6):
        if spin:
            g[8 + i][8 + i] = 'U' if abs(i) > 1 else g[8 + i][8 + i]
            g[8 + i][7 - i] = 'U' if abs(i) > 1 else g[8 + i][7 - i]
        else:
            g[8][8 + i] = 'U' if abs(i) > 1 else g[8][8 + i]
            g[8 + i][8] = 'U' if abs(i) > 1 else g[8 + i][8]
    ellipse(g, 8, 8, 2.2, 2.2, 'B')
    ellipse(g, 7.5, 7.5, 0.9, 0.9, 'x')
    return outline(g)


def dart():
    g = grid(18, 12)
    polygon(g, [(1, 6), (16, 1), (12, 6), (16, 11)], 'r')
    polygon(g, [(1, 6), (16, 1), (12, 6)], 'R')
    ellipse(g, 9, 6, 2.2, 1.3, 'm')
    return outline(g)


def waver(blink):
    g = grid(18, 12)
    ellipse(g, 9, 7.5, 8, 3.2, 'g')
    ellipse(g, 9, 6.8, 8, 2.0, 'h', lambda x, y: y < 7)
    ellipse(g, 9, 4.5, 3.5, 3, 'c', lambda x, y: y < 6)
    for x in (3, 9, 15):
        g[8][x] = 'Y' if blink else 'e'
    return outline(g)


def gunner():
    g = grid(22, 18)
    ellipse(g, 12, 9, 9, 7, 'p')
    ellipse(g, 12, 8, 7, 4.5, 'P', lambda x, y: y < 7)
    rect(g, 1, 8, 6, 10, 'q')
    ellipse(g, 12, 9, 3, 3, 'r')
    ellipse(g, 11.5, 8.5, 1.1, 1.1, 'R')
    return outline(g)


def capsule(bright):
    g = grid(14, 10)
    ellipse(g, 7, 5, 6, 4, 'r' if bright else 'm')
    rect(g, 3, 2, 10, 3, 'R' if bright else 'r')
    return outline(g)


def explosion(stage):
    g = grid(18, 18)
    r = [4, 7, 8.5][stage]
    ellipse(g, 9, 9, r, r, 'O' if stage < 2 else 'o')
    ellipse(g, 9, 9, r * 0.65, r * 0.65, 'Y' if stage < 2 else 'O')
    if stage == 0:
        ellipse(g, 9, 9, r * 0.35, r * 0.35, 'x')
    if stage == 2:
        ellipse(g, 9, 9, r * 0.4, r * 0.4, '.')
    return g



def turret():
    g = grid(16, 13)
    rect(g, 7, 1, 8, 6, 'D')                       # barrel
    ellipse(g, 8, 10, 6.5, 4.5, 'G', lambda x, y: y < 11)
    ellipse(g, 8, 10, 6.5, 4.5, 'w', lambda x, y: y < 8)
    rect(g, 1, 10, 14, 12, 'D')
    ellipse(g, 8, 9, 1.6, 1.3, 'r')
    return outline(g)


def boss_body():
    g = grid(60, 46)
    polygon(g, [(14, 4), (44, 4), (56, 12), (30, 18)], 'N')       # upper wing
    polygon(g, [(14, 42), (44, 42), (56, 34), (30, 28)], 'N')     # lower wing
    polygon(g, [(4, 16), (40, 10), (58, 16), (58, 30), (40, 36), (4, 30)], 'G')
    polygon(g, [(4, 16), (40, 10), (58, 16), (58, 23), (4, 23)], 'w')
    rect(g, 6, 19, 22, 27, 'D')                   # core tunnel
    for y in (14, 31):
        rect(g, 30, y, 52, y + 1, 'B')
    rect(g, 56, 18, 58, 28, 'f')                  # engine glow
    return outline(g)


def boss_core(bright):
    g = grid(14, 14)
    ellipse(g, 7, 7, 6, 6, 'b' if bright else 'B')
    ellipse(g, 7, 7, 3.5, 3.5, 'c' if bright else 'b')
    ellipse(g, 6, 6, 1.4, 1.4, 'x')
    return outline(g)


def boss_plate():
    g = grid(6, 20)
    rect(g, 1, 1, 4, 18, 'G')
    rect(g, 1, 1, 2, 18, 'w')
    return outline(g)

if __name__ == '__main__':
    for i, f in enumerate((2.0, 3.5)):
        save(ship(f), f'shooter_ship_{i + 1}')
    for i, r in enumerate((4.5, 5.2)):
        save(option(r), f'shooter_option_{i + 1}')
    for i in range(2):
        save(fan(i == 1), f'shooter_fan_{i + 1}')
        save(waver(i == 1), f'shooter_waver_{i + 1}')
        save(capsule(i == 0), f'shooter_capsule_{i + 1}')
    save(dart(), 'shooter_dart')
    save(gunner(), 'shooter_gunner')
    for i in range(3):
        save(explosion(i), f'shooter_explosion_{i + 1}')
    save(turret(), 'shooter_turret')
    save(boss_body(), 'shooter_boss_body')
    for i in range(2):
        save(boss_core(i == 0), f'shooter_boss_core_{i + 1}')
    save(boss_plate(), 'shooter_boss_plate')
