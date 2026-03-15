import re, zlib, sys, pathlib

def decode_pdf_string(s: bytes) -> str:
    out = bytearray()
    i = 0
    while i < len(s):
        c = s[i]
        if c == 92:  # backslash
            i += 1
            if i >= len(s):
                break
            c2 = s[i]
            if c2 in b'nrtbf':
                out += {ord('n'):b'\n',ord('r'):b'\r',ord('t'):b'\t',ord('b'):b'\b',ord('f'):b'\f'}[c2]
            elif c2 in b'()\\':
                out.append(c2)
            elif 48 <= c2 <= 55:
                oct_digits = bytes([c2])
                for _ in range(2):
                    if i + 1 < len(s) and 48 <= s[i+1] <= 55:
                        i += 1
                        oct_digits += bytes([s[i]])
                    else:
                        break
                out.append(int(oct_digits, 8))
            else:
                out.append(c2)
        else:
            out.append(c)
        i += 1
    try:
        return out.decode('utf-8')
    except Exception:
        try:
            return out.decode('latin-1')
        except Exception:
            return out.decode('utf-8', errors='ignore')

def extract_text_from_stream(data: bytes):
    texts = []
    # Find literal strings used in text drawing ops
    for m in re.finditer(rb'\((?:\\.|[^\\()])*\)\s*Tj', data, re.S):
        raw = m.group(0)
        s = raw[:raw.rfind(b')')+1]
        inner = s[1:-1]
        texts.append(decode_pdf_string(inner))
    for m in re.finditer(rb'\[(.*?)\]\s*TJ', data, re.S):
        chunk = m.group(1)
        parts = re.findall(rb'\((?:\\.|[^\\()])*\)', chunk)
        for p in parts:
            texts.append(decode_pdf_string(p[1:-1]))
    return texts

def extract_pdf(path):
    b = pathlib.Path(path).read_bytes()
    all_text = []
    # uncompressed text sections
    all_text.extend(extract_text_from_stream(b))
    # compressed streams
    for m in re.finditer(rb'stream\r?\n(.*?)\r?\nendstream', b, re.S):
        stream = m.group(1)
        for candidate in (stream, stream.strip(b'\r\n')):
            try:
                dec = zlib.decompress(candidate)
                all_text.extend(extract_text_from_stream(dec))
                break
            except Exception:
                pass
    cleaned = []
    for t in all_text:
        t = t.replace('\x00','').strip()
        if t:
            cleaned.append(t)
    # de-duplicate while preserving order
    seen = set()
    out = []
    for t in cleaned:
        if t not in seen:
            seen.add(t)
            out.append(t)
    return out

if __name__ == '__main__':
    p = sys.argv[1]
    lines = extract_pdf(p)
    for ln in lines[:1200]:
        print(ln)
