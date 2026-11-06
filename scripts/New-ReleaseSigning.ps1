$ErrorActionPreference = 'Stop'
$directory = if ($env:SUDOKU_SIGNING_DIR) { $env:SUDOKU_SIGNING_DIR } else {
    Join-Path $env:LOCALAPPDATA 'QuietGrid\Signing'
}
if (Test-Path $directory) {
    throw 'The signing directory already exists. Reuse or privately back up the existing key; do not overwrite it.'
}
if (-not $env:JAVA_HOME) {
    throw 'Set JAVA_HOME to your Java 17 installation before generating the release key.'
}
$keytool = Join-Path $env:JAVA_HOME 'bin\keytool.exe'
if (-not (Test-Path $keytool)) {
    throw "keytool not found at JAVA_HOME."
}

New-Item -ItemType Directory -Path $directory | Out-Null
$sid = [System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value
$allowed = @($sid, 'S-1-5-18', 'S-1-5-32-544')
foreach ($rule in (Get-Acl $directory).Access) {
    $identity = $rule.IdentityReference.Translate([System.Security.Principal.SecurityIdentifier]).Value
    if ($rule.AccessControlType -eq 'Allow' -and $identity -notin $allowed) {
        throw 'Signing storage must be private to this user, SYSTEM, and Administrators. No key was generated.'
    }
}

$bytes = New-Object byte[] 32
$random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try { $random.GetBytes($bytes) } finally { $random.Dispose() }
$password = -join ($bytes | ForEach-Object { $_.ToString('x2') })
$env:SUDOKU_SIGNING_PASSWORD = $password
try {
    & $keytool -genkeypair -noprompt -keystore (Join-Path $directory 'sudoku-release.jks') `
        -storetype JKS -alias sudoku-release -keyalg RSA -keysize 3072 -validity 10000 `
        -dname 'CN=Sudoku Release' -storepass:env SUDOKU_SIGNING_PASSWORD `
        -keypass:env SUDOKU_SIGNING_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'Release key generation failed. Do not use a partially created signing directory.' }
    @(
        'storeFile=sudoku-release.jks'
        "storePassword=$password"
        'keyAlias=sudoku-release'
        "keyPassword=$password"
    ) | Set-Content -Path (Join-Path $directory 'signing.properties') -Encoding ASCII
} finally {
    Remove-Item Env:\SUDOKU_SIGNING_PASSWORD
    $password = $null
}
Write-Output 'Release signing configured in private user storage. Back up the keystore and signing.properties. Never publish them.'
