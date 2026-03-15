import pathlib
files=[r'c:\Users\wissa\Downloads\Sys Dist-20260306T105503Z-3-001 (1)\Sys Dist\Cours\2- Socket).pdf',r'c:\Users\wissa\Downloads\Sys Dist-20260306T105503Z-3-001 (1)\Sys Dist\Cours\3- RMI_finale-1.pdf',r'c:\Users\wissa\Downloads\Sys Dist-20260306T105503Z-3-001 (1)\Sys Dist\Cours\5- Jax-Ws.pdf',r'c:\Users\wissa\Downloads\Sys Dist-20260306T105503Z-3-001 (1)\Sys Dist\Cours\JAX-RS.pdf']
keys=[b'socket',b'tcp',b'udp',b'rmi',b'jax',b'rest',b'soap',b'web service']
for f in files:
    b=pathlib.Path(f).read_bytes().lower()
    print('\n==',pathlib.Path(f).name)
    for k in keys:
        print(k.decode(), b.count(k))
