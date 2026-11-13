#requires -Version 7.2
param(
    [Parameter(Mandatory=$true)][string]$InventoryPath,
    [string[]]$ApkPaths = @(
        'app/build/outputs/apk/debug/app-debug.apk',
        'app/build/outputs/apk/release/app-release-unsigned.apk'
    ),
    [ValidateSet('debug','release')][string[]]$Variants = @('debug','release')
)
$ErrorActionPreference = 'Stop'
if ($ApkPaths.Count -lt 1 -or $Variants.Count -lt 1) { throw 'At least one APK/variant pair is required.' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$assetRoot = [IO.Path]::GetFullPath('app/src/main/assets')
$noticeRoot = Join-Path $assetRoot 'notices'
$sha = [Security.Cryptography.SHA256]::Create()
try {
    $files = @(Get-ChildItem -LiteralPath $noticeRoot -Recurse -File)
    $expectedNoticeNames = @($files | ForEach-Object {
        'assets/' + [IO.Path]::GetRelativePath($assetRoot, $_.FullName).Replace('\','/')
    } | Sort-Object -CaseSensitive -Unique)
    $catalog = Get-Content -LiteralPath (Join-Path $noticeRoot 'components.json') -Raw | ConvertFrom-Json
    if ($catalog.schema -ne 2) { throw 'Runtime inventory catalog schema must be2; regenerate from current artifacts.' }
    $expectedRows = @($catalog.components | ForEach-Object {
        $component = $_
        foreach ($artifact in $component.artifacts) {
            foreach ($configuration in $artifact.configurations) {
                "$($component.coordinate)|$($artifact.name)|$configuration|$($artifact.sha256)"
            }
        }
    } | Sort-Object -CaseSensitive -Unique)
    $actualRows = @(Get-Content -LiteralPath $InventoryPath -Raw | ConvertFrom-Json | ForEach-Object {
        $hash = (Get-FileHash -LiteralPath $_.file -Algorithm SHA256).Hash.ToLowerInvariant()
        "$($_.group):$($_.name):$($_.version)|$($_.artifact)|$($_.configuration)|$hash"
    } | Sort-Object -CaseSensitive -Unique)
    if ($actualRows.Count -ne $expectedRows.Count -or ($actualRows -join "`n") -cne ($expectedRows -join "`n")) {
        throw 'Runtime inventory differs from recorded coordinates/artifacts/configurations/hashes; review and regenerate notices.'
    }
    if ($ApkPaths.Count -ne $Variants.Count) { throw 'APK/variant counts must match; supply debug or release for each APK.' }
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
    for ($apkIndex = 0; $apkIndex -lt $ApkPaths.Count; $apkIndex++) {
        $apkPath = $ApkPaths[$apkIndex]
        $configuration = $Variants[$apkIndex] + 'RuntimeClasspath'
        $nativeLibraries = [Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
        foreach ($component in $catalog.components) {
            foreach ($artifact in $component.artifacts | Where-Object { $_.configurations -ccontains $configuration }) {
                foreach ($library in $artifact.nativeLibraries) {
                    if ($nativeLibraries.ContainsKey($library.apkEntry) -and
                        $nativeLibraries[$library.apkEntry].sha256 -cne $library.sha256) { throw "Native library catalog collision: $($library.apkEntry)" }
                    $nativeLibraries[$library.apkEntry] = $library
                }
            }
        }
        $archive = [IO.Compression.ZipFile]::OpenRead([IO.Path]::GetFullPath($apkPath))
        try {
            $actualNativeNames = @($archive.Entries.FullName | Where-Object { $_ -cmatch '^lib/[^/]+/[^/]+\.so$' } | Sort-Object)
            $expectedNativeNames = @($nativeLibraries.Keys | Sort-Object)
            if ($actualNativeNames.Count -ne $expectedNativeNames.Count -or
                ($actualNativeNames -join "`n") -cne ($expectedNativeNames -join "`n")) {
                throw "Native library list differs (missing, unexpected or duplicate entry): $apkPath"
            }
            foreach ($name in $expectedNativeNames) {
                $entry = $archive.GetEntry($name)
                $stream = $entry.Open()
                try { $actual = [Convert]::ToHexString($sha.ComputeHash($stream)).ToLowerInvariant() }
                finally { $stream.Dispose() }
                if ($entry.Length -ne $nativeLibraries[$name].bytes -or $actual -cne $nativeLibraries[$name].sha256) {
                    throw "Native library bytes differ from recorded artifact: $name in $apkPath"
                }
            }
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
                variant = $Variants[$apkIndex]
                runtimeArtifactRowsMatched = $actualRows.Count
                nativeLibrariesMatched = $expectedNativeNames.Count
                completeNativeProvenance = $native.completeNativeInventory
            }
        } finally { $archive.Dispose() }
    }
    # Packaging equality does not establish completeness of upstream native notices/source provenance.
} finally { $sha.Dispose() }
