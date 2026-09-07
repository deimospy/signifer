"""La marca Signifer: una S construida con modulos sobre grilla."""

SIGNUM = [
    "..........#.",
    ".##########.",
    ".##########.",
    ".###........",
    ".###........",
    ".###..##....",
    "....##..###.",
    "........###.",
    "........###.",
    ".##########.",
    ".##########.",
    ".#..........",
]

SIZE = len(SIGNUM)

INK = "#1F5A64"
BONE = "#F2EDE3"
BRONZE = "#B08D57"


def check_symmetry():
    return all(
        SIGNUM[r][c] == SIGNUM[SIZE - 1 - r][SIZE - 1 - c]
        for r in range(SIZE)
        for c in range(SIZE)
    )


def rectangles():
    """Los modulos encendidos, fusionados en el menor numero de rectangulos."""
    runs = []
    for r, row in enumerate(SIGNUM):
        c = 0
        while c < SIZE:
            if row[c] == "#":
                start = c
                while c < SIZE and row[c] == "#":
                    c += 1
                runs.append([r, start, c - start, 1])
            else:
                c += 1

    merged = []
    for run in runs:
        for done in merged:
            same_column = done[1] == run[1] and done[2] == run[2]
            contiguous = done[0] + done[3] == run[0]
            if same_column and contiguous:
                done[3] += 1
                break
        else:
            merged.append(run)
    return [tuple(m) for m in merged]
