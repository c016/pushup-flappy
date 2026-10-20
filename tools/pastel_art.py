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
    's': (225, 235, 250), 'S': (180, 205, 245), 'K': (150, 180, 235),   # cloud shading and soft outline
    'e': (230, 60, 70), 'E': (160, 30, 50),                              # ship red
    'f': (255, 210, 60), 'F': (255, 240, 150), 'h': (215, 150, 30),      # ship wings
    'a': (250, 140, 40),                                                 # feet
    't': (150, 100, 60), 'T': (110, 70, 40),                             # trunks
    'j': (90, 200, 90), 'J': (160, 240, 120), 'i': (50, 140, 70),        # leaves
    'x': (40, 80, 200), 'X': (90, 140, 255), 'z': (255, 90, 160), 'Z': (255, 180, 220),
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



def ship_v2(wing_up):
    """White round pod with a red band, yellow wings on top and little feet, facing right."""
    g = grid(30, 28)
    ellipse(g, 14, 15, 10, 8.5, 'w')
    ellipse(g, 14, 19, 10, 8.5, 'S', lambda x, y: y > 19)
    rect(g, 4, 14, 24, 15, 'e')
    ellipse(g, 19, 12, 3.6, 3, 'c')
    ellipse(g, 18.3, 11.2, 1.3, 1, 'w')
    g = outline(g)
    # Wings behind the body: up or swept back.
    if wing_up:
        polygon(g, [(6, 9), (3, 1), (9, 3), (12, 8)], 'f', under=True)
        polygon(g, [(12, 8), (12, 0), (17, 3), (17, 8)], 'F', under=True)
    else:
        polygon(g, [(5, 10), (0, 5), (8, 5), (12, 8)], 'f', under=True)
        polygon(g, [(12, 8), (15, 2), (19, 5), (17, 8)], 'F', under=True)
    # Feet.
    rect(g, 9, 24, 11, 26, 'a')
    rect(g, 16, 24, 18, 26, 'a')
    return outline(g)


def generator(lit):
    """An enemy base: a floating dome with a glowing eye, antenna and petal-like vents."""
    g = grid(40, 42)
    rect(g, 19, 1, 20, 7, 'u')
    ellipse(g, 19.5, 2, 2.2, 2.2, 'y' if lit else 'o')
    for dx in (-13, -7, 7, 13):
        ellipse(g, 20 + dx, 30, 4, 7, 'z' if abs(dx) > 10 else 'Z')
    ellipse(g, 20, 20, 15, 13, 'l')
    ellipse(g, 20, 16, 13, 8, 'L', lambda x, y: y < 15)
    ellipse(g, 20, 27, 15, 6, 'u', lambda x, y: y > 27)
    for x in (9, 15, 25, 31):
        ellipse(g, x, 25, 1.4, 1.4, 'y' if lit else 'Y')
    g = outline(g)
    ellipse(g, 20, 19, 6, 5, 'k'); ellipse(g, 20, 19, 5, 4, 'w')
    ellipse(g, 21 if lit else 19, 19, 2.4, 2.6, 'e')
    ellipse(g, 20.5 if lit else 18.5, 18, 0.9, 0.9, 'w')
    return g


def soft_cloud(w, h, seed):
    """A big fluffy cloud with soft blue shading and no dark outline."""
    import random
    rnd = random.Random(seed)
    g = grid(w, h)
    bumps = [(rnd.uniform(w * 0.2, w * 0.8), rnd.uniform(h * 0.42, h * 0.62), rnd.uniform(h * 0.16, h * 0.36)) for _ in range(9)]
    bumps += [(w * 0.18, h * 0.65, h * 0.3), (w * 0.82, h * 0.65, h * 0.3)]
    for cx, cy, r in bumps:
        ellipse(g, cx, cy, r * 1.2, r, 'w', lambda x, y: y < h - 2)
    for cx, cy, r in bumps:
        ellipse(g, cx, cy + r * 0.45, r * 1.1, r * 0.7, 's', lambda x, y, cy=cy: y > cy + 1 and g[y][x] == 'w')
    rect_bottom = [(x, h - 3) for x in range(w)]
    for x, y in rect_bottom:
        if g[y - 1][x] != '.':
            g[y][x] = 'S'
    out = [r[:] for r in g]
    for y in range(h):
        for x in range(w):
            if g[y][x] == '.' and any(0 <= y + dy < h and 0 <= x + dx < w and g[y + dy][x + dx] != '.'
                                      for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out[y][x] = 'K'
    return out


def round_tree(color, light):
    g = grid(26, 40)
    polygon(g, [(11, 39), (12, 18), (15, 18), (16, 39)], 't')
    rect(g, 12, 18, 12, 39, 'T')
    ellipse(g, 13, 12, 12, 11, color)
    ellipse(g, 10, 8, 6, 5, light)
    ellipse(g, 8, 6, 2, 1.5, 'w')
    return outline(g)


def tall_flower():
    g = grid(22, 44)
    rect(g, 10, 14, 11, 43, 'i')
    ellipse(g, 6, 30, 5, 2.5, 'j'); ellipse(g, 15, 36, 5, 2.5, 'j')
    for i, (dx, dy) in enumerate(((0, -6), (6, 0), (0, 6), (-6, 0), (4, -4), (4, 4), (-4, 4), (-4, -4))):
        ellipse(g, 10.5 + dx, 10 + dy, 4, 4, 'y' if i < 4 else 'F')
    ellipse(g, 10.5, 10, 4, 4, 'o')
    return outline(g)


def mushroom_plant():
    g = grid(24, 30)
    polygon(g, [(9, 29), (10, 14), (14, 14), (15, 29)], 'w')
    ellipse(g, 12, 12, 11, 8, 'x', lambda x, y: y < 14)
    ellipse(g, 12, 9, 9, 5, 'X', lambda x, y: y < 10)
    for cx, cy in ((7, 8), (13, 5), (17, 10)):
        ellipse(g, cx, cy, 1.6, 1.4, 'w')
    return outline(g)


def bush():
    g = grid(28, 14)
    for cx, r in ((7, 6), (14, 7), (21, 6)):
        ellipse(g, cx, 13, r, r, 'j')
        ellipse(g, cx - 1, 10, r * 0.5, r * 0.35, 'J')
    return outline(g)


def icon(kind):
    """12x12 shop item icons."""
    g = grid(14, 14)
    if kind == 'speed':
        polygon(g, [(2, 9), (6, 2), (12, 4), (8, 9)], 'f'); polygon(g, [(2, 12), (8, 9), (12, 11)], 'F')
    elif kind == 'wide':
        for dy in (-4, 0, 4):
            rect(g, 2, 6 + dy, 9, 7 + dy, 'z')
            g[6 + dy][10] = 'z'; g[7 + dy][10] = 'z'
    elif kind == 'laser':
        rect(g, 1, 6, 12, 7, 'c'); rect(g, 1, 5, 4, 8, 'X')
    elif kind == 'bomb':
        ellipse(g, 7, 8, 5, 5, 'd'); ellipse(g, 5.5, 6.5, 1.4, 1.2, 'B'); rect(g, 7, 1, 8, 3, 'o')
    elif kind == 'life':
        ellipse(g, 7, 7, 5, 4.5, 'w'); rect(g, 2, 7, 12, 7, 'e'); ellipse(g, 9, 5.5, 1.6, 1.3, 'c')
    elif kind == 'exit':
        polygon(g, [(2, 7), (7, 2), (7, 5), (12, 5), (12, 9), (7, 9), (7, 12)], 'L')
    return outline(g)


def hud_coin():
    g = grid(12, 12)
    ellipse(g, 6, 6, 5, 5, 'y'); ellipse(g, 6, 6, 3, 3, 'Y'); rect(g, 6, 3, 6, 8, 'o')
    return outline(g)

if __name__ == '__main__':
    for i in range(2):
        save(ship(i == 0), f'zone_ship_{i + 1}')
        save(flower_base(i == 0), f'zone_base_{i + 1}')
        save(puff(i * 1.0), f'zone_puff_{i + 1}')
        save(bee(i == 0), f'zone_bee_{i + 1}')
        save(coin(i == 1), f'zone_coin_{i + 1}')
        save(boss(i == 1), f'zone_boss_{i + 1}')
        save(heart(i == 0), f'zone_heart_{i + 1}')
    for i in range(2):
        save(ship_v2(i == 0), f'zone_ship_{i + 1}')
        save(generator(i == 0), f'zone_base_{i + 1}')
    for i in range(3):
        save(soft_cloud(56, 32, i + 1), f'zone_cloud_{i + 1}')
    save(round_tree('p', 'P'), 'zone_tree_pink')
    save(round_tree('j', 'J'), 'zone_tree_green')
    save(tall_flower(), 'zone_flower')
    save(mushroom_plant(), 'zone_mushroom')
    save(bush(), 'zone_bush')
    for kind in ('speed', 'wide', 'laser', 'bomb', 'life', 'exit'):
        save(icon(kind), f'zone_icon_{kind}')
    save(reverse_balloon(), 'zone_reverse')
    save(shop_balloon(), 'zone_shop')
    save(bomb(), 'zone_bomb')
    for i in range(3):
        save(pop(i), f'zone_pop_{i + 1}')
