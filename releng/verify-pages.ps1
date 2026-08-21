[CmdletBinding(DefaultParameterSetName = 'ValidateOnly')]
param(
    [Parameter(Mandatory = $true)]
    [string]$PagesRepository,

    [Parameter(Mandatory = $true)]
    [string]$ExpectedBaseHead,

    [Parameter(Mandatory = $true)]
    [string]$ReleaseTag,

    [string]$BundleId = 'dev.lonsing.eclipse.plugins.gotoproject',

    [string]$FeatureId = 'dev.lonsing.eclipse.plugins.gotoproject.feature',

    [Parameter(Mandatory = $true, ParameterSetName = 'WriteManifest')]
    [string]$WriteHashManifest,

    [Parameter(Mandatory = $true, ParameterSetName = 'CompareManifest')]
    [string]$CompareHashManifest,

    [string]$PublishedSiteRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Assert-Condition {
    param(
        [Parameter(Mandatory = $true)][bool]$Condition,
        [Parameter(Mandatory = $true)][string]$Message
    )
    if (-not $Condition) {
        throw $Message
    }
}

function Invoke-PagesGit {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    $output = @(& git -C $script:PagesRoot @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Arguments -join ' ') failed:`n$($output -join [Environment]::NewLine)"
    }
    return $output
}

function Read-ZipXml {
    param(
        [Parameter(Mandatory = $true)][string]$ArchivePath,
        [Parameter(Mandatory = $true)][string]$EntryName
    )

    $archive = [System.IO.Compression.ZipFile]::OpenRead($ArchivePath)
    try {
        $entry = $archive.GetEntry($EntryName)
        Assert-Condition ($null -ne $entry) "$ArchivePath does not contain $EntryName."
        $stream = $entry.Open()
        try {
            $document = [System.Xml.XmlDocument]::new()
            $document.Load($stream)
            return $document
        }
        finally {
            $stream.Dispose()
        }
    }
    finally {
        $archive.Dispose()
    }
}

function Get-CompositeChildren {
    param(
        [Parameter(Mandatory = $true)][string]$ArchivePath,
        [Parameter(Mandatory = $true)][string]$EntryName
    )

    $document = Read-ZipXml $ArchivePath $EntryName
    return @($document.SelectNodes("/*[local-name()='repository']/*[local-name()='children']/*[local-name()='child']") |
        ForEach-Object { $_.GetAttribute('location').Replace('\', '/') })
}

function Get-UpdatesiteManifest {
    param([Parameter(Mandatory = $true)][string]$UpdatesiteRoot)

    $entries = [System.Collections.Generic.List[string]]::new()
    foreach ($file in Get-ChildItem -LiteralPath $UpdatesiteRoot -Recurse -Force -File) {
        $relativePath = [System.IO.Path]::GetRelativePath($UpdatesiteRoot, $file.FullName).Replace('\', '/')
        $hash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        $entries.Add("$hash  $relativePath")
    }
    $sorted = $entries.ToArray()
    [Array]::Sort($sorted, [System.StringComparer]::Ordinal)
    return $sorted
}

Assert-Condition ($ReleaseTag -match '^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$') `
    "ReleaseTag must have exact vMAJOR.MINOR.MICRO form without leading zeroes: $ReleaseTag"
$releaseVersion = $ReleaseTag.Substring(1)
$script:PagesRoot = (Resolve-Path -LiteralPath $PagesRepository).Path
Assert-Condition (Test-Path -LiteralPath (Join-Path $script:PagesRoot '.git')) `
    "PagesRepository is not a Git worktree: $script:PagesRoot"

$actualHead = ([string](@(Invoke-PagesGit @('rev-parse', 'HEAD'))[0])).Trim()
Assert-Condition ($actualHead -eq $ExpectedBaseHead) `
    "The gh-pages baseline moved: expected $ExpectedBaseHead, found $actualHead."

$statusLines = @(Invoke-PagesGit @('status', '--porcelain=v1', '--untracked-files=all'))
foreach ($statusLineValue in $statusLines) {
    $statusLine = [string]$statusLineValue
    if ($statusLine.Length -lt 4) {
        continue
    }
    $statusPath = $statusLine.Substring(3).Trim('"').Replace('\', '/')
    $paths = if ($statusPath -match ' -> ') { @($statusPath -split ' -> ') } else { @($statusPath) }
    foreach ($path in $paths) {
        Assert-Condition ($path -match '^updatesite(?:/|$)') `
            "Only updatesite/** may change in the Pages worktree; found '$path'."
    }
}

$protectedRootFiles = @(Invoke-PagesGit @('ls-tree', '-r', '--name-only', 'HEAD') |
    ForEach-Object { ([string]$_).Replace('\', '/') } |
    Where-Object { $_ -and $_ -notmatch '/' })
foreach ($relativePath in $protectedRootFiles) {
    $worktreePath = Join-Path $script:PagesRoot $relativePath
    Assert-Condition (Test-Path -LiteralPath $worktreePath -PathType Leaf) `
        "Protected Pages root file was removed: $relativePath"
    & git -C $script:PagesRoot diff --quiet HEAD -- $relativePath
    Assert-Condition ($LASTEXITCODE -eq 0) "Protected Pages root file changed: $relativePath"
}

& git -C $script:PagesRoot cat-file -e 'HEAD:.nojekyll' 2>$null
$noJekyllInHead = $LASTEXITCODE -eq 0
$noJekyllInWorktree = Test-Path -LiteralPath (Join-Path $script:PagesRoot '.nojekyll')
Assert-Condition ($noJekyllInHead -eq $noJekyllInWorktree) `
    'The .nojekyll presence/absence state must remain unchanged from the expected Pages baseline.'

$updatesiteRoot = Join-Path $script:PagesRoot 'updatesite'
foreach ($relativePath in @('compositeContent.jar', 'compositeArtifacts.jar', 'p2.index')) {
    Assert-Condition (Test-Path -LiteralPath (Join-Path $updatesiteRoot $relativePath) -PathType Leaf) `
        "Staged updatesite is missing $relativePath."
}

$contentChildren = @(Get-CompositeChildren (Join-Path $updatesiteRoot 'compositeContent.jar') 'compositeContent.xml')
$artifactChildren = @(Get-CompositeChildren (Join-Path $updatesiteRoot 'compositeArtifacts.jar') 'compositeArtifacts.xml')
Assert-Condition ($contentChildren.Count -gt 0) 'Composite metadata has no child repositories.'
Assert-Condition ($contentChildren.Count -eq $artifactChildren.Count) `
    'Composite content and artifact metadata have different child counts.'
for ($index = 0; $index -lt $contentChildren.Count; $index++) {
    Assert-Condition ($contentChildren[$index] -eq $artifactChildren[$index]) `
        'Composite content and artifact metadata have different child locations.'
}
$uniqueChildren = @($contentChildren | Sort-Object -Unique)
Assert-Condition ($uniqueChildren.Count -eq $contentChildren.Count) 'Composite metadata contains duplicate child locations.'

$currentChild = "releases/$releaseVersion"
Assert-Condition (@($contentChildren | Where-Object { $_ -eq 'releases/1.x' }).Count -eq 1) `
    'Composite metadata must retain releases/1.x exactly once.'
Assert-Condition (@($contentChildren | Where-Object { $_ -eq $currentChild }).Count -eq 1) `
    "Composite metadata must contain exactly one $currentChild child."
foreach ($child in $contentChildren) {
    Assert-Condition ($child -match '^releases/(1\.x|[0-9]+\.[0-9]+\.[0-9]+)$') `
        "Composite child is not a safe release-relative path: $child"
    $childRoot = Join-Path $updatesiteRoot $child
    Assert-Condition (Test-Path -LiteralPath $childRoot -PathType Container) `
        "Composite child directory is missing: $child"
    foreach ($metadataFile in @('content.jar', 'artifacts.jar')) {
        Assert-Condition (Test-Path -LiteralPath (Join-Path $childRoot $metadataFile) -PathType Leaf) `
            "Composite child $child is missing $metadataFile."
    }
}

$currentRoot = Join-Path $updatesiteRoot $currentChild
Assert-Condition (Test-Path -LiteralPath (Join-Path $currentRoot 'p2.index') -PathType Leaf) `
    "Current release child $currentChild is missing p2.index."
$featureJars = @(Get-ChildItem -LiteralPath (Join-Path $currentRoot 'features') -File |
    Where-Object { $_.Name -match ('^' + [regex]::Escape($FeatureId) + '_' + [regex]::Escape($releaseVersion) + '\.[A-Za-z0-9_-]+\.jar$') })
$pluginJars = @(Get-ChildItem -LiteralPath (Join-Path $currentRoot 'plugins') -File |
    Where-Object { $_.Name -match ('^' + [regex]::Escape($BundleId) + '_' + [regex]::Escape($releaseVersion) + '\.[A-Za-z0-9_-]+\.jar$') })
Assert-Condition ($featureJars.Count -eq 1) `
    "Current release must contain exactly one $FeatureId $releaseVersion feature JAR; found $($featureJars.Count)."
Assert-Condition ($pluginJars.Count -eq 1) `
    "Current release must contain exactly one $BundleId $releaseVersion plug-in JAR; found $($pluginJars.Count)."

$manifest = @(Get-UpdatesiteManifest $updatesiteRoot)
if ($WriteHashManifest) {
    $manifestPath = [System.IO.Path]::GetFullPath($WriteHashManifest)
    $manifestParent = Split-Path -Parent $manifestPath
    if ($manifestParent) {
        [System.IO.Directory]::CreateDirectory($manifestParent) | Out-Null
    }
    [System.IO.File]::WriteAllLines($manifestPath, $manifest, [System.Text.UTF8Encoding]::new($false))
    Write-Host "Wrote $($manifest.Count) sorted SHA-256 entries to $manifestPath."
}
elseif ($CompareHashManifest) {
    $expectedManifest = @([System.IO.File]::ReadAllLines((Resolve-Path -LiteralPath $CompareHashManifest).Path))
    Assert-Condition ($expectedManifest.Count -eq $manifest.Count) `
        "SHA-256 manifest entry count differs: expected $($expectedManifest.Count), found $($manifest.Count)."
    for ($index = 0; $index -lt $manifest.Count; $index++) {
        Assert-Condition ($manifest[$index] -ceq $expectedManifest[$index]) `
            "SHA-256 manifest mismatch at entry $($index + 1)."
    }
    Write-Host "Matched $($manifest.Count) sorted SHA-256 entries."
}

if ($PublishedSiteRoot) {
    $publishedRoot = (Resolve-Path -LiteralPath $PublishedSiteRoot).Path
    foreach ($file in Get-ChildItem -LiteralPath $updatesiteRoot -Recurse -Force -File) {
        $relativePath = [System.IO.Path]::GetRelativePath($updatesiteRoot, $file.FullName).Replace('\', '/')
        $segments = @($relativePath -split '/')
        if (@($segments | Where-Object { $_.StartsWith('.', [System.StringComparison]::Ordinal) }).Count -gt 0) {
            continue
        }
        $publishedPath = Join-Path (Join-Path $publishedRoot 'updatesite') $relativePath
        Assert-Condition (Test-Path -LiteralPath $publishedPath -PathType Leaf) `
            "Jekyll output omitted publish-relevant updatesite file: $relativePath"
        $sourceHash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
        $publishedHash = (Get-FileHash -LiteralPath $publishedPath -Algorithm SHA256).Hash
        Assert-Condition ($sourceHash -eq $publishedHash) `
            "Jekyll output changed publish-relevant updatesite file: $relativePath"
    }
    Write-Host 'Verified that Jekyll preserved every non-dot updatesite file byte-for-byte.'
}

Write-Host "Verified staged Pages repository for $ReleaseTag against baseline $ExpectedBaseHead."
$global:LASTEXITCODE = 0
