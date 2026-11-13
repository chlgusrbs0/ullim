#requires -Version 7.2
param(
    [string[]]$ApkPaths = @(
        'app/build/outputs/apk/debug/app-debug.apk',
        'app/build/outputs/apk/release/app-release-unsigned.apk'
    )
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$assetRoot = [IO.Path]::GetFullPath('app/src/main/assets')
$noticeRoot = Join-Path $assetRoot 'notices'
$sha = [Security.Cryptography.SHA256]::Create()
try {
    $files = @(Get-ChildItem -LiteralPath $noticeRoot -Recurse -File)
    $expectedNoticeNames = @($files | ForEach-Object {
        'assets/' + [IO.Path]::GetRelativePath($assetRoot, $_.FullName).Replace('\','/')
    } | Sort-Object)
    $catalog = Get-Content -LiteralPath (Join-Path $noticeRoot 'components.json') -Raw | ConvertFrom-Json
    $native = Get-Content -LiteralPath (Join-Path $noticeRoot 'native-components.json') -Raw | ConvertFrom-Json
    foreach ($entry in @($catalog.components.embeddedNotices) + @($native.components)) {
        $expectedHash = if ($entry.sha256) { $entry.sha256 } else { $entry.copiedLicenseSha256 }
        $actualHash = (Get-FileHash -LiteralPath (Join-Path $noticeRoot $entry.file) -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actualHash -ne $expectedHash) { throw "Notice hash mismatch: $($entry.file)" }
    }
    $modelHashes = @{
        'yamnet.tflite' = '10c95ea3eb9a7bb4cb8bddf6feb023250381008177ac162ce169694d05c317de'
        'yamnet-labels.txt' = '8e1267a120c1932b7273c0d0e0c5529edbb9a35512b437b1c8982baa59047051'
    }
    foreach ($apkPath in $ApkPaths) {
        $archive = [IO.Compression.ZipFile]::OpenRead([IO.Path]::GetFullPath($apkPath))
        try {
            $actualNoticeNames = @($archive.Entries.FullName | Where-Object { $_ -like 'assets/notices/*' -and !$_.EndsWith('/') } | Sort-Object)
            if (Compare-Object $expectedNoticeNames $actualNoticeNames) { throw "Packaged notice list differs: $apkPath" }
            foreach ($file in $files) {
                $entryName = 'assets/' + [IO.Path]::GetRelativePath($assetRoot, $file.FullName).Replace('\','/')
                $stream = $archive.GetEntry($entryName).Open()
                try { $actual = [Convert]::ToHexString($sha.ComputeHash($stream)) }
                finally { $stream.Dispose() }
                if ($actual -ne (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash) {
                    throw "Packaged notice bytes differ: $entryName in $apkPath"
                }
            }
            foreach ($name in $modelHashes.Keys) {
                $entry = $archive.GetEntry('assets/' + $name)
                if ($null -eq $entry) { throw "Model asset missing: $name in $apkPath" }
                $stream = $entry.Open()
                try { $actual = [Convert]::ToHexString($sha.ComputeHash($stream)).ToLowerInvariant() }
                finally { $stream.Dispose() }
                if ($actual -ne $modelHashes[$name]) { throw "Model bytes differ: $name in $apkPath" }
            }
            [pscustomobject]@{
                apk = $apkPath
                bytes = (Get-Item -LiteralPath $apkPath).Length
                sha256 = (Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash.ToLowerInvariant()
                noticeFilesMatched = $files.Count
                modelAndLabelsUnchanged = $true
                completeNativeProvenance = $native.completeNativeInventory
            }
        } finally { $archive.Dispose() }
    }
    # Packaging equality does not establish completeness of upstream native notices/source provenance.
} finally { $sha.Dispose() }
