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
    'N': (60, 130, 80),
    'Q': (255, 130, 140), 'O': (205, 50, 75), 'U': (130, 25, 55),       # ship body reds: light, mid, shadow
    'A': (120, 200, 255), 'C': (200, 245, 255), 'D': (60, 120, 210),    # ship window
    'H': (235, 245, 255), 'I': (170, 200, 240),                         # feathers
    'g2': (0, 0, 0),                                                  # background outline
    'V': (255, 205, 175), 'W': (255, 235, 215),                         # peach buds
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


def outline(g, color='k'):
    h, w = len(g), len(g[0])
    out = [r[:] for r in g]
    for y in range(h):
        for x in range(w):
            if g[y][x] == '.' and any(0 <= y + dy < h and 0 <= x + dx < w and g[y + dy][x + dx] not in '.k'
                                      for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out[y][x] = color
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


def bud_stalk(height):
    """Background plant: a tall pale stalk with rings and a peach bud on top."""
    g = grid(20, height)
    rect(g, 8, 12, 11, height - 1, 'G')
    rect(g, 8, 12, 8, height - 1, 'g')
    for y in range(18, height - 2, 9):
        rect(g, 7, y, 12, y + 1, 'g')
        g[y - 3][10] = 'Y'
    ellipse(g, 10, 9, 8, 8.5, 'V')
    ellipse(g, 10, 13, 8, 4, 'R', lambda x, y: y > 13)
    ellipse(g, 7.5, 6, 3, 2.5, 'W')
    for x in (5, 10, 15):
        rect(g, x, 3, x, 6, 'R')
    ellipse(g, 4, height * 0.6, 4, 2, 'j')
    return outline(g, 'N')


def flower_column(height):
    """Background plant: a thick segmented column dotted with little flowers, crowned with petals."""
    g = grid(24, height)
    rect(g, 6, 10, 17, height - 1, 'M')
    rect(g, 6, 10, 8, height - 1, 'm')
    rect(g, 15, 10, 17, height - 1, 'm')
    for y in range(16, height - 3, 12):
        rect(g, 5, y, 18, y + 1, 'n')
        for x in (9, 14):
            ellipse(g, x, y + 6, 1.6, 1.6, 'p')
            g[y + 6][x] = 'y'
    for dx in (-6, 0, 6):
        ellipse(g, 12 + dx, 7, 4.5, 4, 'p')
    ellipse(g, 12, 6, 3.5, 3, 'y')
    return outline(g, 'N')


def ball_plant(height):
    """Background plant: a thin stem with a stack of pink balls."""
    g = grid(18, height)
    rect(g, 8, 8, 9, height - 1, 'i')
    for i, y in enumerate(range(8, int(height * 0.6), 13)):
        r = 6.5 - i * 0.6
        ellipse(g, 9, y, r, r, 'p')
        ellipse(g, 7.5, y - 2, r * 0.4, r * 0.35, 'P')
    ellipse(g, 13, height * 0.75, 4, 2, 'j')
    return outline(g, 'N')


def to_image(g):
    h, w = len(g), len(g[0])
    im = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    for y in range(h):
        for x in range(w):
            if g[y][x] != '.':
                im.putpixel((x, y), C[g[y][x]] + (255,))
    return im


def feather_wing():
    """A wing of three scalloped feathers pointing left; its root is at the right end."""
    g = grid(17, 11)
    for cx, cy, rx, ry in ((4, 6, 3.6, 2.8), (8, 5.5, 3.8, 3.2), (12, 5, 3.8, 3.6)):
        ellipse(g, cx, cy, rx, ry, 'H')
    for cx in (4, 8, 12):
        ellipse(g, cx, 7.2, 3.4, 1.6, 'I', lambda x, y: y >= 7)
    ellipse(g, 12, 3.4, 2, 1, 'w')
    g = outline(g)
    # Lines between the feathers.
    for x, y0 in ((6, 4), (10, 3)):
        for y in range(y0 + 2, 9):
            if g[y][x] != '.':
                g[y][x] = 'I'
    return g


def ship_v3(wing):
    """The player's pod: white top, red belly, big window, a flapping feathered wing and little feet.

    [wing] is 0 (up), 1 (level) or 2 (down) for a three-frame flap.
    """
    g = grid(44, 36)
    cx, cy = 22, 20
    ellipse(g, cx, cy, 13, 10.5, 'w')
    ellipse(g, cx + 4, cy - 1, 10, 7, 'S', lambda x, y: y > cy - 3 and y < cy)
    ellipse(g, cx, cy, 13, 10.5, 'O', lambda x, y: y >= cy)
    ellipse(g, cx, cy + 3, 13, 8, 'U', lambda x, y: y >= cy + 6)
    ellipse(g, cx - 4, cy + 3, 5, 1.8, 'Q', lambda x, y: y >= cy + 2)
    ellipse(g, cx - 5, cy - 6, 3, 1.6, 'H')
    rect(g, 10, cy, 34, cy, 'R')
    # Window.
    ellipse(g, 29, 16, 5.5, 4.5, 'D')
    ellipse(g, 29.3, 15.6, 4.5, 3.6, 'A')
    ellipse(g, 28, 14.3, 1.8, 1.2, 'C')
    g[18][32] = 'C'
    # Nozzle at the back.
    rect(g, 6, 18, 9, 23, 'I')
    rect(g, 6, 18, 9, 18, 'H')
    g = outline(g)
    rect(g, 4, 19, 5, 22, 'y')
    rect(g, 3, 20, 3, 21, 'F')
    # Feet.
    for fx in (16, 25):
        rect(g, fx, 30, fx + 1, 31, 'U')
        rect(g, fx - 1, 32, fx + 3, 33, 'a')
    body = to_image(outline(g))

    # The wing is rotated about its root, which sits on the body's shoulder.
    wing_im = to_image(feather_wing())
    layer = Image.new('RGBA', body.size, (0, 0, 0, 0))
    root = (16, 5)                     # wing root in the wing image
    shoulder = (20, 13)                # where it attaches on the body
    layer.paste(wing_im, (shoulder[0] - root[0], shoulder[1] - root[1]))
    # PIL rotates counter-clockwise, which swings a left-pointing tip downwards, so lifting
    # the wing takes a negative angle.
    angle = (-35, -8, 22)[wing]
    layer = layer.rotate(angle, resample=Image.NEAREST, center=shoulder)
    body.alpha_composite(layer)
    return body


def save_image(im, name):
    im.save(os.path.join(OUT, name + '.png'))

if __name__ == '__main__':
    for i in range(2):
        save(puff(i * 1.0), f'zone_puff_{i + 1}')
        save(bee(i == 0), f'zone_bee_{i + 1}')
        save(coin(i == 1), f'zone_coin_{i + 1}')
        save(boss(i == 1), f'zone_boss_{i + 1}')
        save(heart(i == 0), f'zone_heart_{i + 1}')
    for i in range(3):
        save_image(ship_v3(i), f'zone_ship_{i + 1}')
    for i in range(2):
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
    save(bud_stalk(76), 'zone_bg_bud')
    save(flower_column(90), 'zone_bg_column')
    save(ball_plant(70), 'zone_bg_balls')
    save(reverse_balloon(), 'zone_reverse')
    save(shop_balloon(), 'zone_shop')
    save(bomb(), 'zone_bomb')
    for i in range(3):
        save(pop(i), f'zone_pop_{i + 1}')
