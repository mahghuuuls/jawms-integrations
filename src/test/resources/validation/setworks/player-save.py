"""Inspect a disposable 1.12.2 player save or write a separate head-empty seed."""

import argparse
import gzip
import json
import struct
from pathlib import Path


class Reader:
    def __init__(self, data):
        self.data = data
        self.offset = 0
        self.inventory = None

    def number(self, fmt):
        value = struct.unpack_from(">" + fmt, self.data, self.offset)[0]
        self.offset += struct.calcsize(">" + fmt)
        return value

    def text(self):
        size = self.number("H")
        return self.take(size).decode("utf-8", errors="replace")

    def take(self, size):
        if size < 0 or self.offset + size > len(self.data):
            raise ValueError("Invalid NBT length")
        start = self.offset
        self.offset += size
        return self.data[start:self.offset]

    def payload(self, kind, path=()):
        if 1 <= kind <= 6:
            return self.number("bhiqfd"[kind - 1])
        if kind in (7, 11, 12):
            return self.take(self.number("i") * {7: 1, 11: 4, 12: 8}[kind])
        if kind == 8:
            return self.text()
        if kind == 9:
            element = self.number("B")
            count_offset = self.offset
            count = self.number("i")
            if count < 0 or (count and element == 0):
                raise ValueError("Invalid NBT list")
            spans = []
            values = []
            for _ in range(count):
                start = self.offset
                values.append(self.payload(element))
                spans.append((start, self.offset))
            if path == ("Inventory",):
                if element != 10 and values:
                    raise ValueError("Player inventory is not a compound list")
                self.inventory = (count_offset, self.offset, spans)
            return values
        if kind == 10:
            values = {}
            while (child := self.number("B")) != 0:
                name = self.text()
                if name in values:
                    raise ValueError("Duplicate NBT tag")
                values[name] = self.payload(child, (*path, name))
            return values
        raise ValueError(f"Unsupported NBT tag {kind}")

    def read(self):
        if self.number("B") != 10:
            raise ValueError("Expected a root compound")
        self.text()
        root = self.payload(10)
        if self.offset != len(self.data) or self.inventory is None:
            raise ValueError("Incomplete player save")
        return root


def summarize(root):
    inventory = root["Inventory"]
    return {
        "savedCurrentMana": root["ForgeCaps"]["jawms:player_mana"]["currentMana"],
        "head": [item["id"] for item in inventory if item["Slot"] == 103],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("--empty-head", type=Path, metavar="NEW_OUTPUT")
    args = parser.parse_args()
    reader = Reader(gzip.decompress(args.source.read_bytes()))
    root = reader.read()
    summary = summarize(root)
    print(json.dumps(summary))
    if args.empty_head is None:
        return
    if summary["savedCurrentMana"] != 110 or summary["head"] not in (
            ["minecraft:leather_helmet"], ["minecraft:iron_helmet"]):
        raise ValueError("Head-empty seed requires saved 110 and one known fixture helmet")
    offset, end, spans = reader.inventory
    entries = [reader.data[start:stop] for item, (start, stop) in zip(root["Inventory"], spans)
               if item["Slot"] != 103]
    candidate = reader.data[:offset] + struct.pack(">i", len(entries)) + b"".join(entries) + reader.data[end:]
    check = summarize(Reader(candidate).read())
    if check != {"savedCurrentMana": 110, "head": []}:
        raise ValueError("Seed readback failed")
    with args.empty_head.open("xb") as output:
        output.write(gzip.compress(candidate, mtime=0))
    print(json.dumps(check))


if __name__ == "__main__":
    main()
