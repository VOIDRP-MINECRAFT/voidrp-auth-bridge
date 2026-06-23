param(
    [string]$ProjectRoot = (Get-Location).Path,
    [int]$Retries = 4
)

$ErrorActionPreference = "Stop"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

function Write-Step([string]$Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Ensure-Directory([string]$Path) {
    if (-not (Test-Path $Path)) {
        New-Item -ItemType Directory -Path $Path -Force | Out-Null
    }
}

function Download-File {
    param(
        [Parameter(Mandatory = $true)][string]$Url,
        [Parameter(Mandatory = $true)][string]$OutFile,
        [int]$Attempts = 5,
        [int]$DelaySeconds = 5
    )

    Ensure-Directory (Split-Path $OutFile -Parent)

    for ($i = 1; $i -le $Attempts; $i++) {
        try {
            Write-Host "Downloading [$i/$Attempts]: $Url"
            Invoke-WebRequest -Uri $Url -OutFile $OutFile -UseBasicParsing -TimeoutSec 180
            if ((Test-Path $OutFile) -and ((Get-Item $OutFile).Length -gt 0)) {
                return
            }
            throw "Downloaded file is empty: $OutFile"
        }
        catch {
            Write-Warning "Failed to download: $Url"
            Write-Warning $_.Exception.Message
            if ($i -eq $Attempts) {
                throw
            }
            Start-Sleep -Seconds $DelaySeconds
        }
    }
}

function Set-Or-Add-Line {
    param(
        [string]$FilePath,
        [string]$Key,
        [string]$Value
    )

    $lines = @()
    if (Test-Path $FilePath) {
        $lines = Get-Content $FilePath -Encoding UTF8
    }

    $pattern = '^\s*' + [regex]::Escape($Key) + '\s*='
    $found = $false
    $result = New-Object System.Collections.Generic.List[string]

    foreach ($line in $lines) {
        if ($line -match $pattern) {
            $result.Add("${Key}=${Value}")
            $found = $true
        }
        else {
            $result.Add($line)
        }
    }

    if (-not $found) {
        $result.Add("${Key}=${Value}")
    }

    Set-Content -Path $FilePath -Value $result -Encoding UTF8
}

function Run-ProcessWithRetry {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][string]$WorkingDirectory,
        [int]$Attempts = 4,
        [int]$DelaySeconds = 8
    )

    for ($i = 1; $i -le $Attempts; $i++) {
        Write-Host ""
        Write-Host "Running [$i/$Attempts]: $FilePath $($Arguments -join ' ')" -ForegroundColor Yellow

        Push-Location $WorkingDirectory
        try {
            & $FilePath @Arguments
            $exitCode = $LASTEXITCODE
        }
        finally {
            Pop-Location
        }

        if ($exitCode -eq 0) {
            return
        }

        Write-Warning "Command exited with code $exitCode"
        if ($i -eq $Attempts) {
            throw "Command failed after $Attempts attempts: $FilePath $($Arguments -join ' ')"
        }

        Start-Sleep -Seconds $DelaySeconds
    }
}

if (-not (Test-Path $ProjectRoot)) {
    throw "Project folder not found: $ProjectRoot"
}

$ProjectRoot = (Resolve-Path $ProjectRoot).Path
$WrapperDir = Join-Path $ProjectRoot "gradle\wrapper"
$GradleProps = Join-Path $ProjectRoot "gradle.properties"
$GradlewBat = Join-Path $ProjectRoot "gradlew.bat"
$Gradlew = Join-Path $ProjectRoot "gradlew"

Write-Step "Check Java"
java -version

Write-Step "Check system Gradle"
gradle -v

Write-Step "Download wrapper from official NeoForge 1.21.1 template"
Ensure-Directory $WrapperDir

$baseRaw = "https://raw.githubusercontent.com/NeoForgeMDKs/MDK-1.21.1-NeoGradle/main"

Download-File -Url "$baseRaw/gradlew.bat" -OutFile $GradlewBat
Download-File -Url "$baseRaw/gradlew" -OutFile $Gradlew
Download-File -Url "$baseRaw/gradle/wrapper/gradle-wrapper.jar" -OutFile (Join-Path $WrapperDir "gradle-wrapper.jar")
Download-File -Url "$baseRaw/gradle/wrapper/gradle-wrapper.properties" -OutFile (Join-Path $WrapperDir "gradle-wrapper.properties")

Write-Step "Patch gradle.properties"
if (-not (Test-Path $GradleProps)) {
    New-Item -ItemType File -Path $GradleProps -Force | Out-Null
}

Set-Or-Add-Line -FilePath $GradleProps -Key "org.gradle.parallel" -Value "false"
Set-Or-Add-Line -FilePath $GradleProps -Key "org.gradle.configuration-cache" -Value "false"
Set-Or-Add-Line -FilePath $GradleProps -Key "org.gradle.daemon" -Value "true"
Set-Or-Add-Line -FilePath $GradleProps -Key "systemProp.org.gradle.internal.http.connectionTimeout" -Value "120000"
Set-Or-Add-Line -FilePath $GradleProps -Key "systemProp.org.gradle.internal.http.socketTimeout" -Value "120000"

Write-Step "Clean project temp folders"
Remove-Item (Join-Path $ProjectRoot ".gradle") -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item (Join-Path $ProjectRoot "build") -Recurse -Force -ErrorAction SilentlyContinue

Write-Step "Check wrapper"
Run-ProcessWithRetry -FilePath $GradlewBat -Arguments @("--version") -WorkingDirectory $ProjectRoot -Attempts 2 -DelaySeconds 3

Write-Step "Warm up NeoForge dependencies"
Run-ProcessWithRetry -FilePath $GradlewBat -Arguments @("--refresh-dependencies", "help", "--stacktrace") -WorkingDirectory $ProjectRoot -Attempts $Retries -DelaySeconds 15

Write-Step "Build project"
Run-ProcessWithRetry -FilePath $GradlewBat -Arguments @("build", "--stacktrace") -WorkingDirectory $ProjectRoot -Attempts $Retries -DelaySeconds 15

Write-Step "Done"
Write-Host "If build succeeds, artifact will be in build\libs" -ForegroundColor Green