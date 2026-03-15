import re, zlib, pathlib, unicodedata

FILES = [
r'c:\Users\wissa\Downloads\Sys Dist-20260306T105503Z-3-001 (1)\Sys Dist\Tp\TP4 Socket UDP-1.pdf',
r'c:\Users\wissa\Downloads\Sys Dist-20260306T105503Z-3-001 (1)\Sys Dist\Tp\TP5 RMI-2.pdf',
r'c:\Users\wissa\Downloads\Sys Dist-20260306T105503Z-3-001 (1)\Sys Dist\Tp\Tp6 Jax-ws.pdf',
r'c:\Users\wissa\Downloads\Sys Dist-20260306T105503Z-3-001 (1)\Sys Dist\Tp\TP7 Web Services SOAP et Rest.pdf',
]

def decode_pdf_string(s: bytes) -> str:
    out = bytearray()
    i = 0
    while i < len(s):
        c = s[i]
        if c == 92:
            i += 1
            if i >= len(s):
                break
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

def extract(path):
    b = pathlib.Path(path).read_bytes()
    out = []
    def add_from(data):
        for m in re.finditer(rb'\((?:\\.|[^\\()])*\)\s*Tj', data, re.S):
            s = m.group(0)
            lit = s[:s.rfind(b')')+1][1:-1]
            t = decode_pdf_string(lit).strip()
            if t: out.append(t)
        for m in re.finditer(rb'\[(.*?)\]\s*TJ', data, re.S):
            for p in re.findall(rb'\((?:\\.|[^\\()])*\)', m.group(1)):
                t = decode_pdf_string(p[1:-1]).strip()
                if t: out.append(t)
    add_from(b)
    for m in re.finditer(rb'stream\r?\n(.*?)\r?\nendstream', b, re.S):
        s = m.group(1)
        try:
            d = zlib.decompress(s)
        except Exception:
            try:
                d = zlib.decompress(s.strip(b'\r\n'))
            except Exception:
                continue
        add_from(d)
        if len(out) > 3000:
            break
    txt = ' '.join(out)
    txt = re.sub(r'\s+', ' ', txt)
    return txt

outdir = pathlib.Path('.analysis_pdf')
outdir.mkdir(exist_ok=True)
for f in FILES:
    p = pathlib.Path(f)
    txt = extract(p)
    txt_ascii = unicodedata.normalize('NFKD', txt).encode('ascii','ignore').decode('ascii')
    (outdir / (p.stem + '.ascii.txt')).write_text(txt_ascii[:30000], encoding='utf-8')
    print(p.name, '->', (outdir / (p.stem + '.ascii.txt')).resolve())
