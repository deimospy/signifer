"""Reduce una captura PNG sin dependencias externas.

    python tools/shrink_png.py entrada.png salida.png 3
"""
import struct
import sys
import zlib


def read_png(path):
    data = open(path, "rb").read()
    position = 8
    compressed = b""
    width = height = colour_type = 0
    while position < len(data):
        length = struct.unpack(">I", data[position:position + 4])[0]
        tag = data[position + 4:position + 8]
        body = data[position + 8:position + 8 + length]
        if tag == b"IHDR":
            width, height, _, colour_type = struct.unpack(">IIBB", body[:10])
        elif tag == b"IDAT":
            compressed += body
        position += 12 + length
    return width, height, colour_type, zlib.decompress(compressed)


def unfilter(raw, width, height, bytes_per_pixel):
    stride = width * bytes_per_pixel
    output = bytearray()
    previous = bytearray(stride)
    position = 0
    for _ in range(height):
        filter_type = raw[position]
        position += 1
        line = bytearray(raw[position:position + stride])
        position += stride
        if filter_type == 1:
            for i in range(bytes_per_pixel, stride):
                line[i] = (line[i] + line[i - bytes_per_pixel]) & 255
        elif filter_type == 2:
            for i in range(stride):
                line[i] = (line[i] + previous[i]) & 255
        elif filter_type == 3:
            for i in range(stride):
                left = line[i - bytes_per_pixel] if i >= bytes_per_pixel else 0
                line[i] = (line[i] + ((left + previous[i]) >> 1)) & 255
        elif filter_type == 4:
            for i in range(stride):
                left = line[i - bytes_per_pixel] if i >= bytes_per_pixel else 0
                up = previous[i]
                corner = previous[i - bytes_per_pixel] if i >= bytes_per_pixel else 0
                estimate = left + up - corner
                distances = (abs(estimate - left), abs(estimate - up), abs(estimate - corner))
                if distances[0] <= distances[1] and distances[0] <= distances[2]:
                    predictor = left
                elif distances[1] <= distances[2]:
                    predictor = up
                else:
                    predictor = corner
                line[i] = (line[i] + predictor) & 255
        output += line
        previous = line
    return output


def write_png(path, rows, width, height):
    raw = bytearray()
    for row in rows:
        raw.append(0)
        raw.extend(row)

    def chunk(tag, data):
        return (
            struct.pack(">I", len(data))
            + tag
            + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        )

    output = b"\x89PNG\r\n\x1a\n"
    output += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    output += chunk(b"IDAT", zlib.compress(bytes(raw), 6))
    output += chunk(b"IEND", b"")
    open(path, "wb").write(output)


def main():
    if len(sys.argv) < 3:
        raise SystemExit(__doc__)
    source, target = sys.argv[1], sys.argv[2]
    factor = int(sys.argv[3]) if len(sys.argv) > 3 else 3

    width, height, colour_type, raw = read_png(source)
    bytes_per_pixel = {0: 1, 2: 3, 4: 2, 6: 4}[colour_type]
    pixels = unfilter(raw, width, height, bytes_per_pixel)

    new_width, new_height = width // factor, height // factor
    rows = []
    for y in range(new_height):
        row = bytearray()
        base = (y * factor) * width * bytes_per_pixel
        for x in range(new_width):
            offset = base + (x * factor) * bytes_per_pixel
            pixel = pixels[offset:offset + bytes_per_pixel]
            if bytes_per_pixel == 3:
                row += pixel + b"\xff"
            elif bytes_per_pixel == 4:
                row += pixel
            else:
                row += bytes([pixel[0]] * 3) + b"\xff"
        rows.append(row)

    write_png(target, rows, new_width, new_height)
    print(f"{width}x{height} -> {new_width}x{new_height}")


if __name__ == "__main__":
    main()
