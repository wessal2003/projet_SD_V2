import re, zlib, pathlib, sys, time

KEYWORDS = ['thread','socket','tcp','udp','rmi','soap','jax-ws','jax-rs','rest','kafka','mysql','web service','client','serveur','wsdl','producer','consumer','topic','jdbc']

def decode_pdf_string(s: bytes) -> str:
    out = bytearray()
    i = 0
    while i < len(s):
        c = s[i]
        if c == 92:
            i += 1
            if i >= len(s): break
            c2 = s[i]
            if c2 in b'nrtbf':
                out += {ord('n'):b'\n',ord('r'):b'\r',ord('t'):b'\t',ord('b'):b'\b',ord('f'):b'\f'}[c2]
            elif c2 in b'()\\':
                out.append(c2)
            else:
                out.append(c2)
        else:
            out.append(c)
        i += 1
    try:
        return out.decode('utf-8')
    except Exception:
        return out.decode('latin-1', errors='ignore')

def extract_from_data(data: bytes, cap=500):
    texts = []
    for m in re.finditer(rb'\((?:\\.|[^\\()])*\)\s*Tj', data, re.S):
        s = m.group(0)
        lit = s[:s.rfind(b')')+1][1:-1]
        t = decode_pdf_string(lit).strip()
        if t:
            texts.append(t)
        if len(texts) >= cap:
            return texts
    for m in re.finditer(rb'\[(.*?)\]\s*TJ', data, re.S):
        chunk = m.group(1)
        for p in re.findall(rb'\((?:\\.|[^\\()])*\)', chunk):
            t = decode_pdf_string(p[1:-1]).strip()
            if t:
                texts.append(t)
            if len(texts) >= cap:
                return texts
    return texts

def quick_extract(path, max_streams=200, max_tokens=1200, max_sec=25):
    start = time.time()
    b = pathlib.Path(path).read_bytes()
    out = extract_from_data(b, cap=max_tokens)
    streams = 0
    for m in re.finditer(rb'stream\r?\n(.*?)\r?\nendstream', b, re.S):
        if time.time() - start > max_sec:
            break
        streams += 1
        if streams > max_streams or len(out) >= max_tokens:
            break
        s = m.group(1)
        try:
            d = zlib.decompress(s)
        except Exception:
            try:
                d = zlib.decompress(s.strip(b'\r\n'))
            except Exception:
                continue
        out.extend(extract_from_data(d, cap=max_tokens-len(out)))
    text = ' '.join(out)
    text = re.sub(r'\s+', ' ', text)
    low = text.lower()
    hits = [(k, low.count(k)) for k in KEYWORDS if low.count(k)]
    hits.sort(key=lambda x: (-x[1], x[0]))
    return streams, len(out), text, hits

if __name__ == '__main__':
    files = sys.argv[1:]
    for f in files:
        try:
            streams, nt, txt, hits = quick_extract(f)
            print(f'=== {pathlib.Path(f).name} ===')
            print(f'streams_scanned={streams} tokens={nt}')
            print('keywords=' + ', '.join([f'{k}:{c}' for k,c in hits[:12]]))
            print('snippet=' + txt[:1200])
            print()
        except Exception as e:
            print(f'=== {pathlib.Path(f).name} ===')
            print('ERROR', e)
            print()
