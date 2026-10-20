#Requires -Version 7.0
[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (-not $IsWindows) { throw '此验收入口需要真实 Windows 操作系统。' }
$repositoryPath = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$runDirectory = Join-Path $repositoryPath '.local/windows-validation'
$bindingsPath = Join-Path $repositoryPath '.env'
if (Test-Path -LiteralPath $bindingsPath) { throw '此入口仅用于全新验收检出目录，不覆盖已有 .env。' }
if (Test-Path -LiteralPath $runDirectory) { throw '验收目录已存在；请使用新的检出目录，不覆盖旧 MySQL 数据。' }
New-Item -ItemType Directory -Path $runDirectory -Force | Out-Null

function Invoke-NativeChecked {
    param([string]$Program,[string[]]$Arguments)
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "验收命令失败：$Program，退出码 $LASTEXITCODE" }
}
function New-PrivatePassword {
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $bytes = New-Object byte[] 20
        $generator.GetBytes($bytes)
        $value = ([BitConverter]::ToString($bytes)).Replace('-','').ToLowerInvariant()
        if ($env:GITHUB_ACTIONS -eq 'true') { Write-Host "::add-mask::$value" }
        return $value
    } finally { $generator.Dispose() }
}
function Prepare-Demo {
    param([int]$Port)
    $lines = & python -X utf8 scripts/demo.py prepare --date 2030-05-08 --port $Port --no-build --mysql-client $mysqlClient
    if ($LASTEXITCODE -ne 0) { throw '独立演示库初始化未通过。' }
    $line = $lines | Where-Object { $_ -like 'Manifest: *' } | Select-Object -Last 1
    if (-not $line) { throw '缺少演示初始化记录。' }
    return $line.Substring(10).Trim()
}

$mysqlProcess = $null
Push-Location $repositoryPath
try {
    $client = New-Object Net.Sockets.TcpClient
    $connected = $false
    try { $connected = $client.ConnectAsync('127.0.0.1',33306).Wait(300) -and $client.Connected }
    catch { $connected = $false }
    finally { $client.Dispose() }
    if ($connected) { throw '33306已使用，不结束未知服务。' }
    $mysqlVersion = '8.0.44'
    $archive = Join-Path $runDirectory 'mysql.zip'
    # Versioned official archive; do not use a system MySQL service or its data directory.
    Invoke-WebRequest -Uri "https://cdn.mysql.com/archives/mysql-8.0/mysql-$mysqlVersion-winx64.zip" -OutFile $archive
    (Get-FileHash $archive -Algorithm SHA256).Hash | Set-Content (Join-Path $runDirectory 'mysql-archive.sha256')
    Expand-Archive -LiteralPath $archive -DestinationPath $runDirectory
    $mysqlDirectory = Join-Path $runDirectory "mysql-$mysqlVersion-winx64"
    $mysqlServer = Join-Path $mysqlDirectory 'bin/mysqld.exe'
    $mysqlClient = Join-Path $mysqlDirectory 'bin/mysql.exe'
    $mysqlAdmin = Join-Path $mysqlDirectory 'bin/mysqladmin.exe'
    $dataDirectory = Join-Path $runDirectory 'data'
    Invoke-NativeChecked -Program $mysqlServer -Arguments @('--no-defaults','--initialize-insecure',"--basedir=$mysqlDirectory","--datadir=$dataDirectory")
    $serverArguments = @('--no-defaults',('--basedir="{0}"' -f $mysqlDirectory),('--datadir="{0}"' -f $dataDirectory),'--port=33306','--bind-address=127.0.0.1','--mysqlx=OFF','--console')
    $mysqlProcess = Start-Process -FilePath $mysqlServer -ArgumentList $serverArguments -PassThru -RedirectStandardOutput (Join-Path $runDirectory 'mysql.out.log') -RedirectStandardError (Join-Path $runDirectory 'mysql.err.log')
    $ready = $false
    $deadline = [DateTime]::UtcNow.AddSeconds(90)
    while ([DateTime]::UtcNow -lt $deadline) {
        if ($mysqlProcess.HasExited) { throw '临时 MySQL 启动失败，见验收日志。' }
        & $mysqlAdmin --no-defaults --protocol=TCP -h127.0.0.1 -P33306 -uroot ping 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Milliseconds 300
    }
    if (-not $ready) { throw '临时 MySQL 未就绪。' }
    $appPassword = New-PrivatePassword
    $demoPassword = New-PrivatePassword
    # Only this newly initialized loopback-only server has an empty root password.
    # Pass SQL on stdin, and pass subsequent passwords in process environment, never CLI arguments.
    $env:MYSQL_PWD = ''
    "CREATE USER 'library_validation'@'%' IDENTIFIED BY '$appPassword';" | & $mysqlClient --no-defaults --protocol=TCP -h127.0.0.1 -P33306 -uroot
    if ($LASTEXITCODE -ne 0) { throw '创建临时应用用户失败。' }
    $bindings = @(
        'DB_HOST=127.0.0.1','DB_PORT=33306','DB_NAME=validation_base','DB_USERNAME=library_validation',
        "DB_PASSWORD=$appPassword",'MYSQL_ROOT_PASSWORD=','SPRING_PROFILES_ACTIVE=dev',
        "APP_DEMO_PASSWORD=$demoPassword",'APP_BOOTSTRAP_ADMIN_PASSWORD='
    )
    [IO.File]::WriteAllLines($bindingsPath,$bindings,(New-Object Text.UTF8Encoding($false)))
    Invoke-NativeChecked -Program 'python' -Arguments @('-X','utf8','scripts/validation/browser-regression.py','--no-build','--mysql-client',$mysqlClient,'--output','.local/windows-browser-regression')
    $galleryManifest = Prepare-Demo 8083
    Invoke-NativeChecked -Program 'python' -Arguments @('-X','utf8','scripts/validation/demo-browser.py','--start','--manifest',$galleryManifest,'--output','.local/windows-demo-gallery')
    $clockManifest = Prepare-Demo 8084
    Invoke-NativeChecked -Program 'python' -Arguments @('-X','utf8','scripts/validation/demo-clock.py','--manifest',$clockManifest,'--output','.local/windows-demo-clock')
    Write-Host 'PASS: real Windows Java 17 + native MySQL 8 + Chromium business and packaged demo acceptance.'
} finally {
    if ($mysqlProcess -and -not $mysqlProcess.HasExited) {
        & $mysqlAdmin --no-defaults --protocol=TCP -h127.0.0.1 -P33306 -uroot shutdown 2>$null | Out-Null
        if (-not $mysqlProcess.WaitForExit(15000)) { $mysqlProcess.Kill(); $mysqlProcess.WaitForExit() }
    }
    Pop-Location
}
