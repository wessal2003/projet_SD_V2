$ports = 7070, 7071, 8080, 8082, 8083, 8084, 8085, 9092, 9093, 1099
Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
    Where-Object { $ports -contains $_.LocalPort } |
    Select-Object -ExpandProperty OwningProcess -Unique |
    ForEach-Object {
        try {
            Stop-Process -Id $_ -Force -ErrorAction Stop
        } catch {
        }
    }

Write-Host "SmartTraffic V2 stopped on ports: $($ports -join ', ')"
