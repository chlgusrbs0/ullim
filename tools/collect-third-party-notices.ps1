#requires -Version 7.2
param(
    [Parameter(Mandatory=$true)][string]$InventoryPath,
    [Parameter(Mandatory=$true)][string]$GradleCachePath,
    [string]$OutputDirectory = 'app/src/main/assets/notices'
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$noticeRoot = [IO.Path]::GetFullPath($OutputDirectory)
$cacheRoot = [IO.Path]::GetFullPath($GradleCachePath)
[IO.Directory]::CreateDirectory((Join-Path $noticeRoot 'licenses')) | Out-Null
$rows = Get-Content -LiteralPath $InventoryPath -Raw | ConvertFrom-Json
$sha = [Security.Cryptography.SHA256]::Create()

function Get-BytesHash([byte[]]$Bytes) {
    return [Convert]::ToHexString($sha.ComputeHash($Bytes)).ToLowerInvariant()
}
function Get-XmlChildText($Node, [string]$Child) {
    $value = $Node.SelectSingleNode("*[local-name()='$Child']")
    if ($null -eq $value) { return $null }
    return $value.InnerText
}
function Get-PomChain([string]$Group, [string]$Name, [string]$Version) {
    $chain = @()
    $seen = @{}
    while ($Group -and $Name -and $Version) {
        $coordinate = $Group + ':' + $Name + ':' + $Version
        if ($seen.ContainsKey($coordinate)) { throw "Cyclic parent POM: $coordinate" }
        $seen[$coordinate] = $true
        $cachePath = Join-Path $cacheRoot ($Group + '/' + $Name + '/' + $Version)
        $files = @(Get-ChildItem -LiteralPath $cachePath -Recurse -Filter '*.pom')
        if ($files.Count -ne 1) { throw "Expected one cached POM for $coordinate, found $($files.Count)" }
        [xml]$document = Get-Content -LiteralPath $files[0].FullName -Raw
        $repository = if ($Group.StartsWith('androidx.') -or $Group -eq 'com.google.ai.edge.litert') { 'https://dl.google.com/dl/android/maven2/' }
            else { 'https://repo.maven.apache.org/maven2/' }
        $chain += [pscustomobject]@{ project=$document.DocumentElement; coordinate=$coordinate;
            url=($repository + $Group.Replace('.','/') + '/' + $Name + '/' + $Version + '/' + $Name + '-' + $Version + '.pom');
            sha256=(Get-FileHash -LiteralPath $files[0].FullName -Algorithm SHA256).Hash.ToLowerInvariant() }
        $parent = $document.DocumentElement.SelectSingleNode("*[local-name()='parent']")
        if ($null -eq $parent) { break }
        $Group = Get-XmlChildText $parent 'groupId'
        $Name = Get-XmlChildText $parent 'artifactId'
        $Version = Get-XmlChildText $parent 'version'
    }
    return $chain
}
function Save-LicenseBytes([byte[]]$Bytes, [string]$EntryName) {
    $hash = Get-BytesHash $Bytes
    # Hex-only names avoid trusting paths inside third-party archives.
    $relative = 'licenses/' + $hash + '.txt'
    [IO.File]::WriteAllBytes((Join-Path $noticeRoot $relative), $Bytes)
    return [ordered]@{ sourceEntry=$EntryName; file=$relative; sha256=$hash; bytes=$Bytes.Length }
}
function Read-ArchiveNotices($Archive, [string]$Prefix, [bool]$AllowNested) {
    $found = @()
    foreach ($entry in $Archive.Entries) {
        if ($entry.FullName -match '(?i)(^|/)(LICENSE|LICENCE|NOTICE|COPYING|DEPENDENCIES|THIRD.PARTY|AL2\.0|LGPL2\.1)([._-][^/]*)?$') {
            $stream = [IO.MemoryStream]::new()
            $inputStream = $entry.Open()
            try { $inputStream.CopyTo($stream) } finally { $inputStream.Dispose() }
            try { $found += Save-LicenseBytes $stream.ToArray() ($Prefix + $entry.FullName) }
            finally { $stream.Dispose() }
        } elseif ($AllowNested -and $entry.FullName -match '(^classes\.jar$|^libs/[^/]+\.jar$)') {
            $stream = [IO.MemoryStream]::new()
            $inputStream = $entry.Open()
            try { $inputStream.CopyTo($stream) } finally { $inputStream.Dispose() }
            $stream.Position = 0
            $nested = [IO.Compression.ZipArchive]::new($stream, [IO.Compression.ZipArchiveMode]::Read, $false)
            try { $found += @(Read-ArchiveNotices $nested ($Prefix + $entry.FullName + '!/') $false) }
            finally { $nested.Dispose() }
        }
    }
    return $found
}

$components = @()
foreach ($group in ($rows | Group-Object { $_.group + ':' + $_.name + ':' + $_.version } | Sort-Object Name)) {
    $component = $group.Group[0]
    $pomChain = @(Get-PomChain $component.group $component.name $component.version)
    $project = $pomChain[0].project
    $licenseProject = $pomChain | Where-Object { $_.project.SelectNodes("*[local-name()='licenses']/*[local-name()='license']").Count } | Select-Object -First 1
    $developerProject = $pomChain | Where-Object { $_.project.SelectNodes("*[local-name()='developers']/*[local-name()='developer']").Count } | Select-Object -First 1
    $licenses = @($licenseProject.project.SelectNodes("*[local-name()='licenses']/*[local-name()='license']") | ForEach-Object {
        [ordered]@{ name=(Get-XmlChildText $_ 'name'); url=(Get-XmlChildText $_ 'url') }
    })
    if ($licenses.Count -eq 0 -or @($licenses | Where-Object { !$_.name -or !$_.url }).Count) {
        throw "Missing license metadata: $($group.Name)"
    }
    $developers = @($developerProject.project.SelectNodes("*[local-name()='developers']/*[local-name()='developer']") | ForEach-Object {
        [ordered]@{ name=(Get-XmlChildText $_ 'name'); organization=(Get-XmlChildText $_ 'organization');
            url=(Get-XmlChildText $_ 'url') }
    })
    $artifacts = @()
    $notices = @()
    foreach ($fileGroup in ($group.Group | Group-Object file | Sort-Object Name)) {
        $row = $fileGroup.Group[0]
        $artifact = [ordered]@{ name=$row.artifact;
            sha256=(Get-FileHash -LiteralPath $row.file -Algorithm SHA256).Hash.ToLowerInvariant();
            configurations=@($fileGroup.Group.configuration | Sort-Object -Unique); nativeLibraries=@() }
        $zip = [IO.Compression.ZipFile]::OpenRead($row.file)
        try {
            $notices += @(Read-ArchiveNotices $zip '' $true)
            foreach ($entry in $zip.Entries | Where-Object { $_.FullName -cmatch '^jni/[^/]+/[^/]+\.so$' } | Sort-Object FullName) {
                $stream = $entry.Open()
                try { $hash = [Convert]::ToHexString($sha.ComputeHash($stream)).ToLowerInvariant() }
                finally { $stream.Dispose() }
                $artifact.nativeLibraries += [ordered]@{ sourceEntry=$entry.FullName;
                    apkEntry=('lib/' + $entry.FullName.Substring(4)); bytes=$entry.Length; sha256=$hash }
            }
        } finally { $zip.Dispose() }
        $artifacts += $artifact
    }
    $components += [ordered]@{ coordinate=$group.Name; project=(Get-XmlChildText $project 'name');
        projectUrl=(Get-XmlChildText $project 'url'); developers=$developers; declaredLicenses=$licenses;
        pomChain=@($pomChain | ForEach-Object { [ordered]@{coordinate=$_.coordinate;url=$_.url;sha256=$_.sha256} });
        artifacts=$artifacts; embeddedNotices=@($notices | Sort-Object { $_.sourceEntry + $_.sha256 } -Unique) }
}
$result = [ordered]@{ schema=2; source='Actual Debug/Release resolved artifacts and cached upstream POM/ZIP notices';
    components=$components }
$nativeManifestPath = Join-Path $noticeRoot 'native-components.json'
if ([IO.File]::Exists($nativeManifestPath)) {
    $nativeManifest = Get-Content -LiteralPath $nativeManifestPath -Raw | ConvertFrom-Json
    $nativeParent = $components | Where-Object { $_.coordinate -eq $nativeManifest.parentCoordinate }
    if ($null -eq $nativeParent -or !(@($nativeParent.artifacts.sha256) -contains $nativeManifest.parentArtifactSha256)) {
        throw 'Native parent artifact changed: review linked components and source/notice provenance before regenerating.'
    }
    foreach ($component in $nativeManifest.components) {
        $licenseFile = Join-Path $noticeRoot $component.file
        if (!(Test-Path -LiteralPath $licenseFile) -or
            (Get-FileHash -LiteralPath $licenseFile -Algorithm SHA256).Hash.ToLowerInvariant() -ne $component.copiedLicenseSha256) {
            throw "Native notice bytes differ or are missing: $($component.name)"
        }
    }
}
$json = ConvertTo-Json -InputObject $result -Depth 12
[IO.File]::WriteAllText((Join-Path $noticeRoot 'components.json'), $json + "`n", [Text.UTF8Encoding]::new($false))
$tableLines = @('| Component | Upstream developer (POM) | Declared license | Scope |', '|---|---|---|---|')
foreach ($component in $components) {
    $authors = ($component.developers | ForEach-Object { $_.name }) -join '; '
    $authors = $authors.Replace('|','\|') -replace '[\r\n]+',' '
    $licenseLabels = @($component.declaredLicenses | ForEach-Object {
        if ($_.name -match 'Apache') { 'Apache-2.0' } else { $_.name }
    } | Sort-Object -Unique) -join ', '
    $configurations = @($component.artifacts.configurations | ForEach-Object { $_ } | Sort-Object -Unique)
    $scope = @()
    if ($configurations -contains 'debugRuntimeClasspath') { $scope += 'Debug' }
    if ($configurations -contains 'releaseRuntimeClasspath') { $scope += 'Release' }
    $tableLines += '| `' + $component.coordinate + '` | ' + $authors + ' | ' + $licenseLabels + ' | ' + ($scope -join ', ') + ' |'
}
$table = $tableLines -join "`n"
$documentPath = Join-Path (Split-Path -Parent $PSScriptRoot) 'THIRD_PARTY_NOTICES.md'
$document = [IO.File]::ReadAllText($documentPath)
$markerPattern = '(?s)<!-- runtime-components:start -->.*?<!-- runtime-components:end -->'
if (![regex]::IsMatch($document, $markerPattern)) { throw 'Runtime notice table markers missing' }
$replacement = [Text.RegularExpressions.MatchEvaluator]{ param($match)
    '<!-- runtime-components:start -->' + "`n" + $table + "`n" + '<!-- runtime-components:end -->'
}
[IO.File]::WriteAllText($documentPath, [regex]::Replace($document, $markerPattern, $replacement), [Text.UTF8Encoding]::new($false))
$index = @(
    'Third-party notices for Ullim. These notices do not grant a project-wide license for Ullim.',
    '',
    'YAMNet classification version 1 and 521-label map: Google / TensorFlow authors, Apache-2.0.',
    'Source: https://storage.googleapis.com/download.tensorflow.org/models/tflite/task_library/audio_classification/android/lite-model_yamnet_classification_tflite_1.tflite',
    'yamnet.tflite SHA-256: 10c95ea3eb9a7bb4cb8bddf6feb023250381008177ac162ce169694d05c317de',
    'yamnet-labels.txt SHA-256: 8e1267a120c1932b7273c0d0e0c5529edbb9a35512b437b1c8982baa59047051 (521 labels, unchanged embedded map).',
    'See YAMNet.txt and Apache-2.0.txt. Further model provenance is in repository docs/models.md.',
    '',
    'Runtime artifact versions, upstream developers, POM chains and SHA-256 values: components.json.',
    'Full LICENSE/NOTICE entries supplied by runtime artifacts are preserved byte-for-byte under licenses/.',
    'Each components.json embeddedNotices entry maps the original ZIP entry to its preserved file.',
    'The LiteRT LICENSE includes the Caffe Regents/contributors copyright and BSD conditions; keep the entire text.',
    'DataStore external-protobuf 1.1.7 repackages protobuf-javalite 4.28.2. See Protobuf-4.28.2-LICENSE.txt (Google Inc., BSD-3-Clause).',
    'Known LiteRT native components have additional full license texts under native/ and provenance in native-components.json.',
    'That native list is incomplete and its reference commits identify license texts, not exact linked binary revisions.',
    'In particular Eigen/MPL-2.0 source-form correspondence and remaining native provenance must be resolved before final distribution.',
    '',
    $table,
    '',
    'GameHaptic and google/audio-to-tactile were references, not copied runtime components. Their provenance and the source audit scope are in repository THIRD_PARTY_NOTICES.md.',
    'Build/test tools are not part of this runtime inventory. Gradle Wrapper notices are retained separately in repository third_party/build-tools/.',
    ''
) -join "`n"
[IO.File]::WriteAllText((Join-Path $noticeRoot 'THIRD_PARTY_NOTICES.txt'), $index, [Text.UTF8Encoding]::new($false))
$sha.Dispose()
Write-Output "Collected $($components.Count) components and $((Get-ChildItem -LiteralPath (Join-Path $noticeRoot 'licenses') -File).Count) distinct embedded notice texts."
