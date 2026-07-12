param(
    [string]$Version = ""
)

$ErrorActionPreference = "Stop"

$root = Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")
$pkg = Get-Content -LiteralPath (Join-Path $root "package.json") -Raw -Encoding UTF8 | ConvertFrom-Json
if ([string]::IsNullOrWhiteSpace($Version)) {
    $Version = $pkg.version
}

$releaseDir = Resolve-Path -LiteralPath (Join-Path $root "..\..\releases")
$zipPath = Join-Path $releaseDir "ddys-cloudstream-v$Version.zip"

if (Test-Path -LiteralPath $zipPath) {
    $resolvedZip = (Resolve-Path -LiteralPath $zipPath).Path
    if (-not $resolvedZip.StartsWith($releaseDir.Path, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to remove outside release directory: $resolvedZip"
    }
    Remove-Item -LiteralPath $resolvedZip -Force
}

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$archive = [System.IO.Compression.ZipFile]::Open($zipPath, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    $files = Get-ChildItem -LiteralPath $root.Path -Recurse -Force -File |
        Where-Object {
            $relative = $_.FullName.Substring($root.Path.Length + 1).Replace("\", "/")
            $segments = $relative -split "/"
            $blocked = $false
            foreach ($segment in $segments) {
                if ($segment -in @(".git", ".gradle", "node_modules", "coverage", "package", "build")) {
                    $blocked = $true
                }
            }
            $name = [System.IO.Path]::GetFileName($relative)
            (-not $blocked) -and
                ($name -notin @("package-lock.json", "pnpm-lock.yaml", "yarn.lock")) -and
                (-not ($name -match "^\.env" -and $name -ne ".env.example")) -and
                (-not ($name -match "\.(log|tmp|cache|tgz|zip|cs3)$"))
        } |
        Sort-Object FullName

    foreach ($file in $files) {
        $entryName = $file.FullName.Substring($root.Path.Length + 1).Replace("\", "/")
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $archive,
            $file.FullName,
            $entryName,
            [System.IO.Compression.CompressionLevel]::Optimal
        ) | Out-Null
    }
} finally {
    $archive.Dispose()
}

$hash = Get-FileHash -Algorithm SHA256 -LiteralPath $zipPath
[pscustomobject]@{
    ok = $true
    zip = $zipPath
    files = $files.Count
    bytes = (Get-Item -LiteralPath $zipPath).Length
    sha256 = $hash.Hash
} | ConvertTo-Json
