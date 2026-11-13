#requires -Version 7.2
param([Parameter(Mandatory=$true)][string]$InventoryPath)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$verifier = Join-Path $PSScriptRoot 'verify-packaged-notices.ps1'
$sourceApk = [IO.Path]::GetFullPath('app/build/outputs/apk/debug/app-debug.apk')
$inventory = [IO.Path]::GetFullPath($InventoryPath)
$scratch = Join-Path ([IO.Path]::GetTempPath()) ('ullim-notice-drift-' + [Guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($scratch) | Out-Null
$fixture = Join-Path $scratch 'fixture.apk'
$fixtureInventory = Join-Path $scratch 'inventory.json'
$fixtureArtifact = Join-Path $scratch 'artifact.bin'
$failures = @()
try {
    & $verifier -InventoryPath $inventory | Out-Null
    $emptyRejected = $false
    try { & $verifier -ApkPaths @() -Variants @() -InventoryPath $inventory | Out-Null }
    catch {
        if ($_.Exception.Message -notmatch 'At least one APK/variant') { throw }
        $emptyRejected = $true
    }
    if (!$emptyRejected) { $failures += 'empty-input' }
    [pscustomobject]@{ scenario='empty-input'; rejected=$emptyRejected }
    foreach ($scenario in @('changed-native','missing-native','extra-native','duplicate-native','coordinate-drift','artifact-drift')) {
        Copy-Item -LiteralPath $sourceApk -Destination $fixture -Force
        $rows = @(Get-Content -LiteralPath $inventory -Raw | ConvertFrom-Json)
        if ($scenario -eq 'coordinate-drift') { $rows[0].version += '-unreviewed' }
        elseif ($scenario -eq 'artifact-drift') {
            [IO.File]::WriteAllBytes($fixtureArtifact, [byte[]](1,2,3))
            $rows[0].file = $fixtureArtifact
        } else {
            $archive = [IO.Compression.ZipFile]::Open($fixture, [IO.Compression.ZipArchiveMode]::Update)
            try {
                $native = $archive.Entries | Where-Object { $_.FullName -match '^lib/[^/]+/libtensorflowlite_jni\.so$' } | Select-Object -First 1
                if ($null -eq $native) { throw 'Test requires the real LiteRT JNI library' }
                $name = $native.FullName
                if ($scenario -eq 'missing-native') { $native.Delete() }
                else {
                    if ($scenario -eq 'changed-native') {
                        $bytes = [IO.MemoryStream]::new(); $stream = $native.Open()
                        try { $stream.CopyTo($bytes); $replacement = $bytes.ToArray() } finally { $stream.Dispose(); $bytes.Dispose() }
                        $replacement[0] = $replacement[0] -bxor 1
                        $native.Delete()
                    } else { $replacement = [byte[]](1,2,3) }
                    if ($scenario -eq 'extra-native') { $name = 'lib/arm64-v8a/ullim_unlisted_fixture.so' }
                    $entry = $archive.CreateEntry($name)
                    $stream = $entry.Open()
                    try { $stream.Write($replacement, 0, $replacement.Length) } finally { $stream.Dispose() }
                }
            } finally { $archive.Dispose() }
        }
        [IO.File]::WriteAllText($fixtureInventory, (ConvertTo-Json -InputObject $rows -Depth 8), [Text.UTF8Encoding]::new($false))
        $rejected = $false
        try { & $verifier -ApkPaths @($fixture) -Variants @('debug') -InventoryPath $fixtureInventory | Out-Null }
        catch {
            if ($_.Exception.Message -notmatch 'Native library|Runtime inventory') { throw }
            $rejected = $true
        }
        if (!$rejected) { $failures += $scenario }
        [pscustomobject]@{ scenario=$scenario; rejected=$rejected }
    }
    if ($failures.Count) { throw "Verifier accepted invalid fixtures: $($failures -join ', ')" }
} finally {
    # Delete only the exact files created here; never recurse into a computed directory.
    foreach ($path in @($fixture,$fixtureInventory,$fixtureArtifact)) { if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path } }
    Remove-Item -LiteralPath $scratch
}
