#Requires -Version 5.1
[CmdletBinding()]
param(
    [ValidateSet('Setup','Start','Verify','Package','Check')][string]$Action = 'Start',
    [switch]$ExistingMySql
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repoPath = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $repoPath '.env'

function Invoke-Checked {
    param([string]$Program,[string[]]$Arguments)
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "命令执行失败（退出码 $LASTEXITCODE）：$Program" }
}
function Initialize-Bindings {
    if (Test-Path -LiteralPath $envPath) { return }
    $template = [IO.File]::ReadAllText((Join-Path $repoPath '.env.example'),[Text.Encoding]::UTF8)
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        foreach ($bindingName in @('DB_PASSWORD','MYSQL_ROOT_PASSWORD','APP_DEMO_PASSWORD')) {
            $bytes = New-Object byte[] 20
            $generator.GetBytes($bytes)
            $generated = ([BitConverter]::ToString($bytes)).Replace('-','').ToLowerInvariant()
            $template = [regex]::Replace($template,"(?m)^${bindingName}=\r?$",("${bindingName}=" + $generated))
        }
        $stream = [IO.File]::Open($envPath,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write)
        try {
            $payload = (New-Object Text.UTF8Encoding($false)).GetBytes($template)
            $stream.Write($payload,0,$payload.Length)
        } finally { $stream.Dispose() }
    } finally { $generator.Dispose() }
    Write-Host '已生成本地 .env；密码仅保存在该文件中，不会显示或提交。'
}
function Read-Bindings {
    if (-not (Test-Path -LiteralPath $envPath)) { throw '缺少 .env，请先执行 Setup。' }
    $allowed = @('DB_HOST','DB_PORT','DB_NAME','DB_USERNAME','DB_PASSWORD','MYSQL_ROOT_PASSWORD','SPRING_PROFILES_ACTIVE','APP_DEMO_PASSWORD','APP_BOOTSTRAP_ADMIN_PASSWORD','SERVER_ADDRESS','SERVER_PORT')
    foreach ($line in [IO.File]::ReadAllLines($envPath,[Text.Encoding]::UTF8)) {
        if ($line -match '^([A-Z][A-Z0-9_]*)=(.*)$') {
            $bindingName = $Matches[1]; $bindingValue = $Matches[2]
            if ($allowed -notcontains $bindingName) { throw "不支持的配置名称：$bindingName" }
            [Environment]::SetEnvironmentVariable($bindingName,$bindingValue,'Process')
        }
    }
}
function Assert-Jdk {
    $null = Get-Command java -ErrorAction Stop
    $null = Get-Command javac -ErrorAction Stop
    $compilerVersion = & javac -version 2>&1
    if ($LASTEXITCODE -ne 0 -or "$compilerVersion" -notmatch '^javac 17[\.]') { throw '请安装完整JDK 17，并检查JAVA_HOME及PATH。' }
}
function Assert-PortFree {
    $portText = [Environment]::GetEnvironmentVariable('SERVER_PORT','Process')
    $applicationPort = if ($portText) { [int]$portText } else { 8080 }
    $client = New-Object Net.Sockets.TcpClient
    $connected = $false
    try { $task = $client.ConnectAsync('127.0.0.1',$applicationPort); $connected = $task.Wait(500) -and $client.Connected }
    catch { $connected = $false }
    finally { $client.Dispose() }
    if ($connected) { throw "端口 $applicationPort 正在使用；请先在原应用终端按Ctrl+C停止，再构建。不会结束未知进程。" }
}
if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) { throw '此启动脚本面向Windows，请在Windows终端执行。' }
Push-Location $repoPath
try {
    if ($Action -eq 'Setup' -or $Action -eq 'Start') { Initialize-Bindings }
    if ($Action -ne 'Package' -or (Test-Path -LiteralPath $envPath)) { Read-Bindings }
    if ($Action -eq 'Check') {
        $portText = [Environment]::GetEnvironmentVariable('SERVER_PORT','Process')
        $applicationPort = if ($portText) { [int]$portText } else { 8080 }
        $baseUrl = "http://127.0.0.1:$applicationPort"
        $login = Invoke-WebRequest ($baseUrl + '/login') -UseBasicParsing
        if ($login.StatusCode -ne 200 -or $login.Content -notmatch 'name="_csrf"') { throw '登录页或CSRF就绪检查未通过。' }
        foreach ($asset in @('/webjars/bootstrap/5.3.8/css/bootstrap.min.css','/webjars/echarts/5.6.0/dist/echarts.min.js')) {
            $response = Invoke-WebRequest ($baseUrl + $asset) -UseBasicParsing
            if ($response.StatusCode -ne 200) { throw '本地前端资源检查失败。' }
        }
        Write-Host '就绪检查通过：登录页、CSRF、Bootstrap和ECharts。请继续按验收表操作三种角色。'
    } else {
        Assert-Jdk
        if (($Action -eq 'Setup' -or $Action -eq 'Start') -and -not $ExistingMySql) {
            Invoke-Checked -Program 'docker' -Arguments @('compose','up','-d','--wait')
        }
        if ($Action -eq 'Setup') { Write-Host '开发绑定和数据库准备完成；尚未执行应用测试。' }
        if ($Action -eq 'Verify' -or $Action -eq 'Package' -or $Action -eq 'Start') {
            Assert-PortFree
            $wrapperPath = Join-Path $repoPath 'mvnw.cmd'
            if ($Action -eq 'Verify') { Invoke-Checked -Program $wrapperPath -Arguments @('-B','-ntp','verify') }
            else {
                Invoke-Checked -Program $wrapperPath -Arguments @('-B','-ntp','-DskipTests','package')
                Write-Host '打包完成（未执行测试，不能以此代替Verify）。'
            }
            if ($Action -eq 'Start') {
                Invoke-Checked -Program 'java' -Arguments @('-jar',(Join-Path $repoPath 'target/library-seat-reservation-system-0.1.0-SNAPSHOT.jar'))
            }
        }
    }
} finally { Pop-Location }
