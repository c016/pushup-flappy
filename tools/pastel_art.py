"""Generates the original pastel pixel art for the push-up pastel zone (a looping side-scroller in
the style of cute 80s arcade shooters).

Run with: python3 tools/pastel_art.py  (needs Pillow). Writes zone_*.png into
app/src/main/res/drawable-nodpi. Shapes are drawn procedurally and outlined automatically.
"""
import os
from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'app', 'src', 'main', 'res', 'drawable-nodpi')
C = {
    'k': (70, 50, 90),       # outline
    'w': (255, 255, 255),
    'm': (150, 235, 200), 'M': (210, 255, 235), 'n': (90, 190, 160),   # mint
    'p': (255, 170, 200), 'P': (255, 225, 235), 'q': (225, 110, 160),   # pink
    'y': (255, 225, 110), 'Y': (255, 245, 190), 'o': (240, 170, 60),    # yellow
    'b': (140, 190, 255), 'B': (210, 230, 255), 'v': (90, 120, 220),    # blue
    'l': (200, 160, 255), 'L': (235, 215, 255), 'u': (150, 100, 220),   # lilac
    'r': (255, 110, 110), 'R': (255, 190, 180),
    'c': (130, 220, 255),
    'g': (140, 220, 120), 'G': (200, 245, 180),
    'd': (40, 30, 60),
}


def grid(w, h):
    return [['.'] * w for _ in range(h)]


def ellipse(g, cx, cy, rx, ry, ch, cond=None):
    for y in range(len(g)):
        for x in range(len(g[0])):
            if ((x + .5 - cx) / rx) ** 2 + ((y + .5 - cy) / ry) ** 2 <= 1 and (cond is None or cond(x, y)):
                g[y][x] = ch


def polygon(g, pts, ch, under=False):
    """Fills a polygon; with [under] only empty or outline cells are painted, so it sits behind."""
    def inside(px, py):
        res, j = False, len(pts) - 1
        for i in range(len(pts)):
            xi, yi = pts[i]
            xj, yj = pts[j]
            if (yi > py) != (yj > py) and px < (xj - xi) * (py - yi) / (yj - yi) + xi:
                res = not res
            j = i
        return res
    for y in range(len(g)):
        for x in range(len(g[0])):
            if inside(x + .5, y + .5) and (not under or g[y][x] in '.k'):
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


def eyes(g, xs, y, big=1.6):
    for x in xs:
        ellipse(g, x, y, big, big + 0.4, 'd')
        g[int(y - 1)][int(x - 1)] = 'w'


def ship(fin_up):
    """A round mint pod with a fin on top and a little propeller tail, facing right."""
    g = grid(26, 24)
    ellipse(g, 12, 12, 9, 6.5, 'm')
    ellipse(g, 12, 11, 8, 4.5, 'M', lambda x, y: y < 10)
    ellipse(g, 12, 15, 9, 6.5, 'n', lambda x, y: y > 16)
    ellipse(g, 16, 11, 3.2, 2.6, 'c')
    ellipse(g, 15.5, 10.4, 1.2, 0.9, 'w')
    rect(g, 1, 11, 3, 13, 'y')                                        # tail rotor
    g = outline(g)
    # Fins flap above and below the body.
    polygon(g, [(7, 7), (9, 0 if fin_up else 2), (14, 1 if fin_up else 3), (14, 7)], 'p', under=True)
    polygon(g, [(7, 17), (9, 23 if not fin_up else 21), (14, 22 if not fin_up else 20), (14, 17)], 'p', under=True)
    g = outline(g)
    return g


def flower_base(open_):
    """Enemy generator: a big pastel flower whose petals open and close."""
    g = grid(30, 30)
    r = 13 if open_ else 11.5
    for i, (dx, dy) in enumerate(((0, -1), (1, 0), (0, 1), (-1, 0), (.7, .7), (-.7, .7), (.7, -.7), (-.7, -.7))):
        ellipse(g, 15 + dx * (r - 5), 15 + dy * (r - 5), 5, 5, 'p' if i < 4 else 'P')
    ellipse(g, 15, 15, 7, 7, 'y')
    ellipse(g, 15, 14, 5, 4, 'Y', lambda x, y: y < 13)
    g = outline(g)
    eyes(g, (12.5, 17.5), 15)
    rect(g, 13, 19, 16, 19, 'q')
    return g


def puff(squash):
    g = grid(18, 16)
    ellipse(g, 9, 9, 8, 6.5 - squash, 'P')
    ellipse(g, 6, 7, 4, 3.5 - squash / 2, 'w')
    g = outline(g)
    eyes(g, (7, 11), 9)
    ellipse(g, 9, 12, 1.5, 1, 'p')
    return g


def bee(wing_up):
    g = grid(18, 19)
    ellipse(g, 9, 13, 7, 5, 'y')
    for x in (8, 11, 14):
        rect(g, x, 9, x, 17, 'o')
    g = outline(g)
    ellipse(g, 9 if wing_up else 10, 5 if wing_up else 8, 3.2, 2.6, 'k')
    ellipse(g, 9 if wing_up else 10, 5 if wing_up else 8, 2.4, 1.8, 'B')
    eyes(g, (5,), 12, 1.2)
    return g


def coin(turn):
    g = grid(12, 12)
    ellipse(g, 6, 6, 5 if not turn else 2.5, 5, 'y')
    ellipse(g, 6, 6, 3 if not turn else 1.2, 3, 'Y')
    if not turn:
        rect(g, 6, 4, 6, 7, 'o')
    return outline(g)


def reverse_balloon():
    g = grid(18, 24)
    ellipse(g, 9, 9, 8, 8.5, 'r')
    ellipse(g, 6.5, 6, 2.5, 2, 'R')
    rect(g, 8, 18, 9, 23, 'k')
    g = outline(g)
    # double-headed arrow
    rect(g, 4, 9, 13, 9, 'w')
    for d in range(1, 3):
        g[9 - d][4 + d] = 'w'; g[9 + d][4 + d] = 'w'
        g[9 - d][13 - d] = 'w'; g[9 + d][13 - d] = 'w'
    return g


def shop_balloon():
    g = grid(30, 34)
    ellipse(g, 15, 13, 13, 12.5, 'l')
    ellipse(g, 11, 8, 4, 3, 'L')
    polygon(g, [(11, 25), (19, 25), (17, 28), (13, 28)], 'u')
    rect(g, 10, 29, 20, 33, 'o')
    rect(g, 11, 29, 19, 30, 'y')
    g = outline(g)
    return g


def bomb():
    g = grid(10, 11)
    ellipse(g, 5, 6.5, 4, 4, 'd')
    ellipse(g, 4, 5.5, 1.2, 1, 'B')
    rect(g, 5, 1, 5, 2, 'o')
    return outline(g)


def boss(blink):
    """A giant smiling cloud with a glowing heart core."""
    g = grid(64, 50)
    for cx, cy, r in ((18, 22, 14), (32, 16, 16), (46, 22, 14), (24, 33, 14), (40, 33, 14), (32, 30, 16)):
        ellipse(g, cx, cy, r, r * 0.9, 'B')
    for cx, cy, r in ((26, 14, 7), (38, 12, 6)):
        ellipse(g, cx, cy, r, r * 0.8, 'w')
    g = outline(g)
    if blink:
        rect(g, 20, 22, 25, 22, 'd'); rect(g, 39, 22, 44, 22, 'd')
    else:
        eyes(g, (22.5, 41.5), 22, 2.4)
    ellipse(g, 15, 28, 3, 1.6, 'p'); ellipse(g, 49, 28, 3, 1.6, 'p')
    return g


def heart(bright):
    g = grid(16, 15)
    ellipse(g, 5, 5, 4, 4, 'r' if bright else 'q')
    ellipse(g, 11, 5, 4, 4, 'r' if bright else 'q')
    polygon(g, [(1, 6), (15, 6), (8, 14)], 'r' if bright else 'q')
    ellipse(g, 4.5, 4, 1.4, 1.1, 'R')
    return outline(g)


def pop(stage):
    """A soft pastel burst for explosions."""
    g = grid(18, 18)
    r = [4, 7, 8.5][stage]
    ellipse(g, 9, 9, r, r, 'P' if stage < 2 else 'L')
    ellipse(g, 9, 9, r * 0.6, r * 0.6, 'w' if stage < 2 else 'P')
    if stage == 2:
        ellipse(g, 9, 9, r * 0.45, r * 0.45, '.')
    return g


if __name__ == '__main__':
    for i in range(2):
        save(ship(i == 0), f'zone_ship_{i + 1}')
        save(flower_base(i == 0), f'zone_base_{i + 1}')
        save(puff(i * 1.0), f'zone_puff_{i + 1}')
        save(bee(i == 0), f'zone_bee_{i + 1}')
        save(coin(i == 1), f'zone_coin_{i + 1}')
        save(boss(i == 1), f'zone_boss_{i + 1}')
        save(heart(i == 0), f'zone_heart_{i + 1}')
    save(reverse_balloon(), 'zone_reverse')
    save(shop_balloon(), 'zone_shop')
    save(bomb(), 'zone_bomb')
    for i in range(3):
        save(pop(i), f'zone_pop_{i + 1}')
