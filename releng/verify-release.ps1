[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ReleaseTag,

    [string]$SourceRepository = (Join-Path $PSScriptRoot '..'),

    [string]$GeneratedRepository,

    [string]$BundleId = 'dev.lonsing.eclipse.plugins.gotoproject',

    [string]$FeatureId = 'dev.lonsing.eclipse.plugins.gotoproject.feature',

    [switch]$Publication
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Assert-Condition {
    param(
        [Parameter(Mandatory = $true)]
        [bool]$Condition,

        [Parameter(Mandatory = $true)]
        [string]$Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

function Invoke-Git {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    $output = @(& git -C $script:SourceRoot @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Arguments -join ' ') failed:`n$($output -join [Environment]::NewLine)"
    }
    return $output
}

function Get-TrackedFiles {
    param([Parameter(Mandatory = $true)][string]$Pattern)

    return @(Invoke-Git -Arguments @('ls-files', '--', $Pattern) |
        ForEach-Object { ([string]$_).Replace('\', '/') } |
        Where-Object { $_ })
}

function Test-IsFixturePath {
    param([Parameter(Mandatory = $true)][string]$Path)

    return $Path -match '(^|/)(fixture|fixtures|test-fixture|test-fixtures)(/|$)' -or
        $Path -match '(^|/)src/test/resources(/|$)'
}

function Read-XmlFile {
    param([Parameter(Mandatory = $true)][string]$Path)

    $document = [System.Xml.XmlDocument]::new()
    $document.PreserveWhitespace = $true
    $document.Load($Path)
    return $document
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

function Assert-UnsignedJar {
    param([Parameter(Mandatory = $true)][System.IO.FileInfo]$Jar)

    $archive = [System.IO.Compression.ZipFile]::OpenRead($Jar.FullName)
    try {
        $signatureEntries = @($archive.Entries | Where-Object {
                $name = $_.FullName.Replace('\', '/')
                $name -match '(?i)^META-INF/[^/]+\.(SF|RSA|DSA|EC)$' -or
                $name -match '(?i)^META-INF/SIG-[^/]+$'
            })
        Assert-Condition ($signatureEntries.Count -eq 0) `
            "$($Jar.FullName) is signed; this release process is explicitly configured for unsigned production artifacts."
    }
    finally {
        $archive.Dispose()
    }
}

Assert-Condition ($ReleaseTag -match '^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$') `
    "ReleaseTag must have exact vMAJOR.MINOR.MICRO form without leading zeroes: $ReleaseTag"
$releaseVersion = $ReleaseTag.Substring(1)
$expectedMavenVersion = "$releaseVersion-SNAPSHOT"
$expectedOsgiVersion = "$releaseVersion.qualifier"

$script:SourceRoot = (Resolve-Path -LiteralPath $SourceRepository).Path
Assert-Condition (Test-Path -LiteralPath (Join-Path $script:SourceRoot '.git')) `
    "SourceRepository is not a Git worktree: $script:SourceRoot"

$rootPomPath = Join-Path $script:SourceRoot 'pom.xml'
$rootPom = Read-XmlFile $rootPomPath
$rootProject = $rootPom.SelectSingleNode("/*[local-name()='project']")
$rootGroupId = $rootProject.SelectSingleNode("*[local-name()='groupId']").InnerText.Trim()
$rootArtifactId = $rootProject.SelectSingleNode("*[local-name()='artifactId']").InnerText.Trim()

$pomPaths = @(Get-TrackedFiles '*pom.xml' | Where-Object { -not (Test-IsFixturePath $_) })
Assert-Condition ($pomPaths.Count -gt 0) 'No tracked reactor POMs were found.'
foreach ($relativePath in $pomPaths) {
    $pom = Read-XmlFile (Join-Path $script:SourceRoot $relativePath)
    $project = $pom.SelectSingleNode("/*[local-name()='project']")
    $projectVersion = $project.SelectSingleNode("*[local-name()='version']")
    if ($null -ne $projectVersion) {
        Assert-Condition ($projectVersion.InnerText.Trim() -eq $expectedMavenVersion) `
            "$relativePath declares project version '$($projectVersion.InnerText.Trim())'; expected '$expectedMavenVersion'."
    }

    $parent = $project.SelectSingleNode("*[local-name()='parent']")
    if ($null -ne $parent) {
        $parentGroupId = $parent.SelectSingleNode("*[local-name()='groupId']").InnerText.Trim()
        $parentArtifactId = $parent.SelectSingleNode("*[local-name()='artifactId']").InnerText.Trim()
        if ($parentGroupId -eq $rootGroupId -and $parentArtifactId -eq $rootArtifactId) {
            $parentVersion = $parent.SelectSingleNode("*[local-name()='version']").InnerText.Trim()
            Assert-Condition ($parentVersion -eq $expectedMavenVersion) `
                "$relativePath declares reactor parent version '$parentVersion'; expected '$expectedMavenVersion'."
        }
    }
}

$targetPlatformVersion = $rootProject.SelectSingleNode("*[local-name()='properties']/*[local-name()='target-platform.version']")
if ($null -ne $targetPlatformVersion) {
    Assert-Condition ($targetPlatformVersion.InnerText.Trim() -eq $expectedMavenVersion) `
        "pom.xml declares target-platform.version '$($targetPlatformVersion.InnerText.Trim())'; expected '$expectedMavenVersion'."
}

$manifestPaths = @(Get-TrackedFiles '*/META-INF/MANIFEST.MF')
Assert-Condition ($manifestPaths.Count -gt 0) 'No tracked bundle manifests were found.'
foreach ($relativePath in $manifestPaths) {
    $text = [System.IO.File]::ReadAllText((Join-Path $script:SourceRoot $relativePath))
    $match = [regex]::Match($text, '(?m)^Bundle-Version:\s*([^\r\n]+)\s*$')
    Assert-Condition $match.Success "$relativePath has no Bundle-Version header."
    $actualVersion = $match.Groups[1].Value.Trim()
    Assert-Condition ($actualVersion -eq $expectedOsgiVersion) `
        "$relativePath declares Bundle-Version '$actualVersion'; expected '$expectedOsgiVersion'."
}

$featurePaths = @(Get-TrackedFiles '*/feature.xml' | Where-Object { -not (Test-IsFixturePath $_) })
Assert-Condition ($featurePaths.Count -gt 0) 'No tracked production features were found.'
foreach ($relativePath in $featurePaths) {
    $featureXml = Read-XmlFile (Join-Path $script:SourceRoot $relativePath)
    $feature = $featureXml.SelectSingleNode("/*[local-name()='feature']")
    Assert-Condition ($null -ne $feature) "$relativePath has no feature root element."
    $actualVersion = $feature.GetAttribute('version')
    Assert-Condition ($actualVersion -eq $expectedOsgiVersion) `
        "$relativePath declares feature version '$actualVersion'; expected '$expectedOsgiVersion'."
}

if ($GeneratedRepository) {
    $generatedRoot = (Resolve-Path -LiteralPath $GeneratedRepository).Path
    foreach ($metadataFile in @('content.jar', 'artifacts.jar', 'p2.index')) {
        Assert-Condition (Test-Path -LiteralPath (Join-Path $generatedRoot $metadataFile) -PathType Leaf) `
            "Generated repository is missing ${metadataFile}: $generatedRoot"
    }

    $featureJars = @(Get-ChildItem -LiteralPath (Join-Path $generatedRoot 'features') -File |
        Where-Object { $_.Name -match ('^' + [regex]::Escape($FeatureId) + '_' + [regex]::Escape($releaseVersion) + '\.[A-Za-z0-9_-]+\.jar$') })
    $pluginJars = @(Get-ChildItem -LiteralPath (Join-Path $generatedRoot 'plugins') -File |
        Where-Object { $_.Name -match ('^' + [regex]::Escape($BundleId) + '_' + [regex]::Escape($releaseVersion) + '\.[A-Za-z0-9_-]+\.jar$') })
    Assert-Condition ($featureJars.Count -eq 1) `
        "Expected exactly one $FeatureId $releaseVersion feature JAR in $generatedRoot; found $($featureJars.Count)."
    Assert-Condition ($pluginJars.Count -eq 1) `
        "Expected exactly one $BundleId $releaseVersion plug-in JAR in $generatedRoot; found $($pluginJars.Count)."

    $featureQualifiedVersion = $featureJars[0].BaseName.Substring(($FeatureId + '_').Length)
    $pluginQualifiedVersion = $pluginJars[0].BaseName.Substring(($BundleId + '_').Length)
    Assert-Condition ($featureQualifiedVersion -eq $pluginQualifiedVersion) `
        "Generated feature version '$featureQualifiedVersion' and plug-in version '$pluginQualifiedVersion' differ."

    $artifactsXml = Read-ZipXml (Join-Path $generatedRoot 'artifacts.jar') 'artifacts.xml'
    $artifactNodes = @($artifactsXml.SelectNodes("/*[local-name()='repository']/*[local-name()='artifacts']/*[local-name()='artifact']"))
    $bundleArtifacts = @($artifactNodes | Where-Object {
            $_.GetAttribute('classifier') -eq 'osgi.bundle' -and $_.GetAttribute('id') -eq $BundleId
        })
    $featureArtifacts = @($artifactNodes | Where-Object {
            $_.GetAttribute('classifier') -eq 'org.eclipse.update.feature' -and $_.GetAttribute('id') -eq $FeatureId
        })
    Assert-Condition ($bundleArtifacts.Count -eq 1 -and $bundleArtifacts[0].GetAttribute('version') -eq $pluginQualifiedVersion) `
        "artifacts.jar does not contain exactly the expected $BundleId/$pluginQualifiedVersion artifact."
    Assert-Condition ($featureArtifacts.Count -eq 1 -and $featureArtifacts[0].GetAttribute('version') -eq $featureQualifiedVersion) `
        "artifacts.jar does not contain exactly the expected $FeatureId/$featureQualifiedVersion artifact."

    $contentXml = Read-ZipXml (Join-Path $generatedRoot 'content.jar') 'content.xml'
    $unitNodes = @($contentXml.SelectNodes("/*[local-name()='repository']/*[local-name()='units']/*[local-name()='unit']"))
    $expectedUnits = @(
        @{ Id = $BundleId; Version = $pluginQualifiedVersion },
        @{ Id = "$FeatureId.feature.jar"; Version = $featureQualifiedVersion },
        @{ Id = "$FeatureId.feature.group"; Version = $featureQualifiedVersion }
    )
    foreach ($expectedUnit in $expectedUnits) {
        $matchingUnits = @($unitNodes | Where-Object {
                $_.GetAttribute('id') -eq $expectedUnit.Id -and $_.GetAttribute('version') -eq $expectedUnit.Version
            })
        Assert-Condition ($matchingUnits.Count -eq 1) `
            "content.jar does not contain exactly one IU $($expectedUnit.Id)/$($expectedUnit.Version)."
    }

    Assert-UnsignedJar $featureJars[0]
    Assert-UnsignedJar $pluginJars[0]
}

if ($Publication) {
    $head = ([string](@(Invoke-Git -Arguments @('rev-parse', 'HEAD'))[0])).Trim()
    $tagCommit = ([string](@(Invoke-Git -Arguments @('rev-parse', '--verify', "refs/tags/$ReleaseTag^{commit}"))[0])).Trim()
    Assert-Condition ($tagCommit -eq $head) `
        "Publication requires $ReleaseTag to point exactly at HEAD ($head); it points at $tagCommit."

    & git -C $script:SourceRoot merge-base --is-ancestor HEAD origin/master
    Assert-Condition ($LASTEXITCODE -eq 0) `
        'Publication requires HEAD to be an ancestor of origin/master.'
}

$scope = if ($GeneratedRepository) { 'source, p2 metadata, artifact names, and unsigned JARs' } else { 'source versions' }
Write-Host "Verified $scope for $ReleaseTag."
$global:LASTEXITCODE = 0
