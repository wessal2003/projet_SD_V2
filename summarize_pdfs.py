import re, zlib, pathlib

KEYWORDS = [
    'thread','socket','tcp','udp','rmi','soap','jax-ws','jaxws','jax-rs','rest','kafka','mysql','web service','service',
    'client','serveur','server','wsdl','stub','registry','broker','topic','consumer','producer','jdbc','base de donnees'
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
    for enc in ('utf-8','latin-1'):
        try:
            return out.decode(enc)
        except Exception:
            pass
    return out.decode('utf-8', errors='ignore')

def extract_text_from_stream(data: bytes):
    texts = []
    for m in re.finditer(rb'\((?:\\.|[^\\()])*\)\s*Tj', data, re.S):
        raw = m.group(0)
        s = raw[:raw.rfind(b')')+1]
        texts.append(decode_pdf_string(s[1:-1]))
    for m in re.finditer(rb'\[(.*?)\]\s*TJ', data, re.S):
        chunk = m.group(1)
        parts = re.findall(rb'\((?:\\.|[^\\()])*\)', chunk)
        for p in parts:
            texts.append(decode_pdf_string(p[1:-1]))
    return texts

def extract_pdf(path: pathlib.Path):
    b = path.read_bytes()
    all_text = []
    all_text.extend(extract_text_from_stream(b))
    for m in re.finditer(rb'stream\r?\n(.*?)\r?\nendstream', b, re.S):
        stream = m.group(1)
        for cand in (stream, stream.strip(b'\r\n')):
            try:
                dec = zlib.decompress(cand)
                all_text.extend(extract_text_from_stream(dec))
                break
            except Exception:
                continue
    cleaned = []
    for t in all_text:
        t = t.replace('\x00',' ').replace('\n',' ').replace('\r',' ').strip()
        if t:
            cleaned.append(t)
    return cleaned

def normalize(tokens):
    # simple normalization and join for keyword search
    s = ' '.join(tokens)
    s = re.sub(r'\s+', ' ', s)
    return s

def main():
    files = [
        r'c:\Users\wissa\Downloads\Sys Dist-20260306T105503Z-3-001 (1)\Sys Dist\Cours\1- Cours Thread.pdf',
        r'c:\Users\wissa\Downloads\Sys Dist-20260306T105503Z-3-001 (1)\Sys Dist\Cours\2- Socket).pdf',
        r'c:\Users\wissa\Downloads\Sys Dist-20260306T105503Z-3-001 (1)\Sys Dist\Cours\3- RMI_finale-1.pdf',
        r'c:\Users\wissa\Downloads\Sys Dist-20260306T105503Z-3-001 (1)\Sys Dist\Cours\4- SOA et WebServices.pdf',
        r'c:\Users\wissa\Downloads\Sys Dist-20260306T105503Z-3-001 (1)\Sys Dist\Cours\5- Jax-Ws.pdf',
        r'c:\Users\wissa\Downloads\Sys Dist-20260306T105503Z-3-001 (1)\Sys Dist\Cours\JAX-RS.pdf',
        r'c:\Users\wissa\Downloads\Sys Dist-20260306T105503Z-3-001 (1)\Sys Dist\Cours\Mini-projet Distributed Systems v1.pdf',
        r'c:\Users\wissa\Downloads\Sys Dist-20260306T105503Z-3-001 (1)\Sys Dist\Tp\TP1-Multithreading.pdf',
        r'c:\Users\wissa\Downloads\Sys Dist-20260306T105503Z-3-001 (1)\Sys Dist\Tp\TP2-Multithreading (1).pdf',
        r'c:\Users\wissa\Downloads\Sys Dist-20260306T105503Z-3-001 (1)\Sys Dist\Tp\TP3 suite-1.pdf',
        r'c:\Users\wissa\Downloads\Sys Dist-20260306T105503Z-3-001 (1)\Sys Dist\Tp\TP3-1.pdf',
        r'c:\Users\wissa\Downloads\Sys Dist-20260306T105503Z-3-001 (1)\Sys Dist\Tp\TP4 Socket UDP-1.pdf',
        r'c:\Users\wissa\Downloads\Sys Dist-20260306T105503Z-3-001 (1)\Sys Dist\Tp\TP5 RMI-2.pdf',
        r'c:\Users\wissa\Downloads\Sys Dist-20260306T105503Z-3-001 (1)\Sys Dist\Tp\Tp6 Axi2.pdf',
        r'c:\Users\wissa\Downloads\Sys Dist-20260306T105503Z-3-001 (1)\Sys Dist\Tp\Tp6 Jax-ws.pdf',
        r'c:\Users\wissa\Downloads\Sys Dist-20260306T105503Z-3-001 (1)\Sys Dist\Tp\TP7 Web Services SOAP et Rest.pdf',
    ]
    outdir = pathlib.Path('.analysis_pdf')
    outdir.mkdir(exist_ok=True)
    summary_lines = []
    for f in files:
        p = pathlib.Path(f)
        if not p.exists():
            summary_lines.append(f'[MISSING] {f}')
            continue
        tokens = extract_pdf(p)
        text = normalize(tokens)
        low = text.lower()
        kws = []
        for kw in KEYWORDS:
            c = low.count(kw)
            if c:
                kws.append((kw,c))
        kws.sort(key=lambda x: (-x[1], x[0]))
        outtxt = outdir / (p.stem + '.txt')
        with outtxt.open('w', encoding='utf-8') as wf:
            wf.write(text[:200000])
        summary_lines.append(f'\n=== {p.name} ===')
        summary_lines.append(f'tokens={len(tokens)} extracted_chars={len(text)}')
        summary_lines.append('keywords=' + ', '.join([f'{k}:{c}' for k,c in kws[:20]]))
        # store short snippet for manual check
        summary_lines.append('snippet=' + text[:600])
    (outdir / 'summary.txt').write_text('\n'.join(summary_lines), encoding='utf-8')
    print((outdir / 'summary.txt').resolve())

if __name__ == '__main__':
    main()
