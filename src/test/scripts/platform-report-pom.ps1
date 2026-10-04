# Dedicated verification POM adapter only. No Maven, network or database operations.
# Request JSON arrives on stdin. Only allowlisted configuration metadata is emitted.
$ErrorActionPreference = 'Stop'
[Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$script:Stage = 'REQUEST'
$script:PomNamespace = 'http://maven.apache.org/POM/4.0.0'

function Require-AbsolutePath([string]$value) {
    if ([string]::IsNullOrWhiteSpace($value) -or -not [System.IO.Path]::IsPathRooted($value) -or
        $value -match '^[\\/]{2}' -or $value -notmatch '^[A-Za-z]:[\\/]') { throw 'INVALID_PATH' }
    $full = [System.IO.Path]::GetFullPath($value)
    # Reject links in all existing ancestors; a new destination cannot escape through a junction.
    $cursor = $full
    while (-not [string]::IsNullOrEmpty($cursor)) {
        if ([System.IO.File]::Exists($cursor) -or [System.IO.Directory]::Exists($cursor)) {
            if (([System.IO.File]::GetAttributes($cursor) -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw 'LINK_PATH_REFUSED'
            }
        }
        $parent = [System.IO.Path]::GetDirectoryName($cursor)
        if ($parent -eq $cursor) { break }
        $cursor = $parent
    }
    return $full
}

function Get-Sha256([byte[]]$bytes) {
    $algorithm = [System.Security.Cryptography.SHA256]::Create()
    try { return ([System.BitConverter]::ToString($algorithm.ComputeHash($bytes))).Replace('-', '').ToLowerInvariant() }
    finally { $algorithm.Dispose() }
}

function Read-SafeXml([byte[]]$bytes) {
    if ($bytes.Length -eq 0 -or $bytes.Length -gt 8388608) { throw 'INVALID_XML_SIZE' }
    $settings = [System.Xml.XmlReaderSettings]::new()
    $settings.DtdProcessing = [System.Xml.DtdProcessing]::Prohibit
    $settings.XmlResolver = $null
    $settings.MaxCharactersInDocument = 8388608
    $settings.MaxCharactersFromEntities = 1024
    $settings.IgnoreComments = $false
    $settings.IgnoreWhitespace = $false
    $stream = [System.IO.MemoryStream]::new($bytes, $false)
    $reader = [System.Xml.XmlReader]::Create($stream, $settings)
    try {
        $document = [System.Xml.XmlDocument]::new()
        $document.XmlResolver = $null
        $document.PreserveWhitespace = $true
        $document.Load($reader)
        if ($document.DocumentElement.get_LocalName() -ne 'project' -or
            $document.DocumentElement.get_NamespaceURI() -ne $script:PomNamespace) { throw 'INVALID_POM_ROOT' }
        return ,$document
    } finally { $reader.Dispose(); $stream.Dispose() }
}

function Get-NamespaceManager([System.Xml.XmlDocument]$document) {
    $manager = [System.Xml.XmlNamespaceManager]::new($document.NameTable)
    $manager.AddNamespace('p', $script:PomNamespace)
    return ,$manager
}

function Single-Node([System.Xml.XmlNode]$node, [string]$xpath, [System.Xml.XmlNamespaceManager]$manager, [bool]$required = $true) {
    $matches = $node.SelectNodes($xpath, $manager)
    if ($matches.Count -gt 1 -or ($required -and $matches.Count -ne 1)) { throw 'AMBIGUOUS_POM_CONFIGURATION' }
    if ($matches.Count -eq 0) { return $null }
    return ,$matches.Item(0)
}

function Node-Text([System.Xml.XmlNode]$node, [string]$xpath, [System.Xml.XmlNamespaceManager]$manager, [bool]$required = $true) {
    $selected = Single-Node $node $xpath $manager $required
    if ($null -eq $selected) { return $null }
    if (@($selected.get_ChildNodes() | Where-Object { $_.get_NodeType() -eq [System.Xml.XmlNodeType]::Element }).Count -ne 0) {
        throw 'NON_SCALAR_CONFIGURATION'
    }
    $value = $selected.InnerText.Trim()
    if ($required -and [string]::IsNullOrWhiteSpace($value)) { throw 'EMPTY_CONFIGURATION' }
    return $value
}

function Assert-SameStructure([System.Xml.XmlNode]$left, [System.Xml.XmlNode]$right) {
    if ($left.get_NodeType() -ne $right.get_NodeType() -or $left.get_LocalName() -ne $right.get_LocalName() -or
        $left.get_NamespaceURI() -ne $right.get_NamespaceURI() -or $left.get_Prefix() -ne $right.get_Prefix()) {
        throw 'UNAUTHORIZED_POM_STRUCTURE_CHANGE'
    }
    if ($left.get_NodeType() -eq [System.Xml.XmlNodeType]::Element) {
        if ($left.Attributes.Count -ne $right.Attributes.Count) { throw 'UNAUTHORIZED_POM_ATTRIBUTE_CHANGE' }
        foreach ($attribute in $left.Attributes) {
            $other = $right.Attributes.GetNamedItem($attribute.get_LocalName(), $attribute.get_NamespaceURI())
            if ($null -eq $other -or $attribute.Value -cne $other.Value -or $attribute.get_Prefix() -cne $other.get_Prefix()) {
                throw 'UNAUTHORIZED_POM_ATTRIBUTE_CHANGE'
            }
        }
    } elseif ($left.Value -cne $right.Value) { throw 'UNAUTHORIZED_POM_VALUE_CHANGE' }
    # Formatting whitespace and the XML encoding declaration are not Maven model fields.
    $ignored = @([System.Xml.XmlNodeType]::Whitespace, [System.Xml.XmlNodeType]::SignificantWhitespace, [System.Xml.XmlNodeType]::XmlDeclaration)
    $leftChildren = @($left.get_ChildNodes() | Where-Object { $_.get_NodeType() -notin $ignored })
    $rightChildren = @($right.get_ChildNodes() | Where-Object { $_.get_NodeType() -notin $ignored })
    if ($leftChildren.Count -ne $rightChildren.Count) { throw 'UNAUTHORIZED_POM_CHILD_CHANGE' }
    for ($i = 0; $i -lt $leftChildren.Count; $i++) { Assert-SameStructure $leftChildren[$i] $rightChildren[$i] }
}

function Get-Surefire([System.Xml.XmlDocument]$document, [System.Xml.XmlNamespaceManager]$manager) {
    $plugin = Single-Node $document '/p:project/p:build/p:plugins/p:plugin[p:artifactId="maven-surefire-plugin"]' $manager
    $group = Node-Text $plugin 'p:groupId' $manager $false
    if ($null -ne $group -and $group -ne 'org.apache.maven.plugins') { throw 'UNEXPECTED_SUREFIRE_GROUP' }
    return ,$plugin
}

function Resource-Metadata([System.Xml.XmlDocument]$document, [string]$xpath, [System.Xml.XmlNamespaceManager]$manager) {
    $rows = @()
    foreach ($resource in $document.SelectNodes($xpath, $manager)) {
        $rows += @{
            directory = Node-Text $resource 'p:directory' $manager
            targetPath = Node-Text $resource 'p:targetPath' $manager $false
            filtering = Node-Text $resource 'p:filtering' $manager $false
            includes = @($resource.SelectNodes('p:includes/p:include', $manager) | ForEach-Object { $_.InnerText })
            excludes = @($resource.SelectNodes('p:excludes/p:exclude', $manager) | ForEach-Object { $_.InnerText })
        }
    }
    return ,$rows
}

try {
    $requestText = [Console]::In.ReadToEnd()
    if ($requestText.Length -gt 32768) { throw 'REQUEST_TOO_LARGE' }
    $request = $requestText | ConvertFrom-Json
    if ($request.mode -notin @('create', 'inspect')) { throw 'INVALID_MODE' }
    if ($request.mode -eq 'create') {
        if (@($request.PSObject.Properties.Name | Where-Object { $_ -notin @('mode', 'rootPom', 'destination') }).Count -ne 0 -or
            $request.rootPom -isnot [string] -or $request.destination -isnot [string]) { throw 'INVALID_CREATE_REQUEST' }
        $script:Stage = 'CREATE_PATH_GUARD'
        $rootPom = Require-AbsolutePath $request.rootPom
        $destination = Require-AbsolutePath $request.destination
        if ([System.IO.Path]::GetFileName($rootPom) -cne 'pom.xml' -or -not [System.IO.File]::Exists($rootPom) -or
            -not [string]::Equals([System.IO.Path]::GetDirectoryName($rootPom), [System.IO.Path]::GetDirectoryName($destination), [System.StringComparison]::OrdinalIgnoreCase) -or
            [string]::Equals($rootPom, $destination, [System.StringComparison]::OrdinalIgnoreCase) -or
            [System.IO.Path]::GetFileName($destination) -notmatch '^[A-Za-z0-9][A-Za-z0-9._-]*\.xml$' -or
            [System.IO.File]::Exists($destination) -or [System.IO.Directory]::Exists($destination)) { throw 'UNSAFE_POM_DESTINATION' }
        $script:Stage = 'CREATE_SAFE_XML'
        $originalBytes = [System.IO.File]::ReadAllBytes($rootPom)
        $originalHash = Get-Sha256 $originalBytes
        $original = Read-SafeXml $originalBytes
        $document = $original.CloneNode($true)
        $manager = Get-NamespaceManager $document
        $plugin = Get-Surefire $document $manager
        if ($document.SelectNodes('//p:plugin[p:artifactId="maven-surefire-plugin"]', $manager).Count -ne 1 -or
            $plugin.SelectNodes('p:version|.//p:reportsDirectory|.//p:redirectTestOutputToFile', $manager).Count -ne 0) {
            throw 'EXISTING_SUREFIRE_OVERRIDE_REFUSED'
        }
        $configuration = Single-Node $plugin 'p:configuration' $manager $false
        $createdConfiguration = $null -eq $configuration
        if ($createdConfiguration) {
            $configuration = $document.CreateElement('configuration', $script:PomNamespace)
            [void]$plugin.AppendChild($configuration)
        }
        $version = $document.CreateElement('version', $script:PomNamespace)
        $version.InnerText = '3.1.2'
        [void]$plugin.InsertBefore($version, $configuration)
        $reports = $document.CreateElement('reportsDirectory', $script:PomNamespace)
        $reports.InnerText = '${platform.verification.reportsDirectory}'
        [void]$configuration.AppendChild($reports)
        $redirect = $document.CreateElement('redirectTestOutputToFile', $script:PomNamespace)
        $redirect.InnerText = 'true'
        [void]$configuration.AppendChild($redirect)

        $script:Stage = 'CREATE_STRUCTURE_VERIFICATION'
        $memory = [System.IO.MemoryStream]::new()
        $writerSettings = [System.Xml.XmlWriterSettings]::new()
        $writerSettings.Encoding = [System.Text.UTF8Encoding]::new($false)
        $writerSettings.Indent = $false
        $writerSettings.NewLineHandling = [System.Xml.NewLineHandling]::None
        $writerSettings.CloseOutput = $false
        $writer = [System.Xml.XmlWriter]::Create($memory, $writerSettings)
        try { $document.Save($writer); $writer.Flush(); $newBytes = $memory.ToArray() }
        finally { $writer.Dispose(); $memory.Dispose() }
        $roundTrip = Read-SafeXml $newBytes
        $roundTripManager = Get-NamespaceManager $roundTrip
        $roundTripPlugin = Get-Surefire $roundTrip $roundTripManager
        if ((Node-Text $roundTripPlugin 'p:version' $roundTripManager) -cne '3.1.2' -or
            (Node-Text $roundTripPlugin 'p:configuration/p:reportsDirectory' $roundTripManager) -cne '${platform.verification.reportsDirectory}' -or
            (Node-Text $roundTripPlugin 'p:configuration/p:redirectTestOutputToFile' $roundTripManager) -cne 'true') {
            throw 'GENERATED_BINDING_MISMATCH'
        }
        $comparison = $roundTrip.CloneNode($true)
        $comparisonManager = Get-NamespaceManager $comparison
        $comparisonPlugin = Get-Surefire $comparison $comparisonManager
        foreach ($xpath in @('p:version', 'p:configuration/p:reportsDirectory', 'p:configuration/p:redirectTestOutputToFile')) {
            $added = Single-Node $comparisonPlugin $xpath $comparisonManager
            [void]$added.ParentNode.RemoveChild($added)
        }
        if ($createdConfiguration) {
            $addedConfiguration = Single-Node $comparisonPlugin 'p:configuration' $comparisonManager
            [void]$comparisonPlugin.RemoveChild($addedConfiguration)
        }
        Assert-SameStructure $original $comparison
        if ((Get-Sha256 ([System.IO.File]::ReadAllBytes($rootPom))) -cne $originalHash) { throw 'PUBLIC_POM_DRIFT' }
        $script:Stage = 'CREATE_EXCLUSIVE_WRITE'
        $file = [System.IO.FileStream]::new($destination, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::Write, [System.IO.FileShare]::Read)
        try { $file.Write($newBytes, 0, $newBytes.Length); $file.Flush($true) } finally { $file.Dispose() }
        $newHash = Get-Sha256 $newBytes
        if ((Get-Sha256 ([System.IO.File]::ReadAllBytes($destination))) -cne $newHash -or
            (Get-Sha256 ([System.IO.File]::ReadAllBytes($rootPom))) -cne $originalHash) { throw 'POM_WRITE_VERIFICATION_FAILED' }
        @{
            ok = $true; mode = 'create'; rootPom = $rootPom; destination = $destination
            rootPomSha256 = $originalHash; destinationSha256 = $newHash; sha256 = $newHash
            surefireVersion = '3.1.2'; reportsDirectoryExpression = '${platform.verification.reportsDirectory}'
            redirectTestOutputToFile = $true; onlyThreeAuthorizedFieldsChanged = $true; publicPomUnchanged = $true
        } | ConvertTo-Json -Depth 8 -Compress
    } else {
        if (@($request.PSObject.Properties.Name | Where-Object { $_ -notin @('mode', 'effectivePom') }).Count -ne 0 -or
            $request.effectivePom -isnot [string]) { throw 'INVALID_INSPECT_REQUEST' }
        $script:Stage = 'INSPECT_SAFE_XML'
        $effectivePom = Require-AbsolutePath $request.effectivePom
        $bytes = [System.IO.File]::ReadAllBytes($effectivePom)
        $document = Read-SafeXml $bytes
        $manager = Get-NamespaceManager $document
        $plugin = Get-Surefire $document $manager
        $version = Node-Text $plugin 'p:version' $manager
        $reportsDirectory = Node-Text $plugin 'p:configuration/p:reportsDirectory' $manager
        $redirect = Node-Text $plugin 'p:configuration/p:redirectTestOutputToFile' $manager
        if ($version -cne '3.1.2' -or $redirect -cne 'true' -or $reportsDirectory.Contains('${') -or
            -not [System.IO.Path]::IsPathRooted($reportsDirectory)) { throw 'UNEXPECTED_EFFECTIVE_SUREFIRE_CONFIGURATION' }
        $script:Stage = 'INSPECT_ALLOWLISTED_METADATA'
        @{
            ok = $true; mode = 'inspect'; effectivePom = $effectivePom; effectivePomSha256 = Get-Sha256 $bytes
            surefireVersion = $version; reportsDirectory = $reportsDirectory; redirectTestOutputToFile = $true
            classesDirectory = Node-Text $document '/p:project/p:build/p:outputDirectory' $manager
            testClassesDirectory = Node-Text $document '/p:project/p:build/p:testOutputDirectory' $manager
            sourceDirectory = Node-Text $document '/p:project/p:build/p:sourceDirectory' $manager
            testSourceDirectory = Node-Text $document '/p:project/p:build/p:testSourceDirectory' $manager
            groupId = Node-Text $document '/p:project/p:groupId' $manager
            artifactId = Node-Text $document '/p:project/p:artifactId' $manager
            version = Node-Text $document '/p:project/p:version' $manager
            build = @{
                directory = Node-Text $document '/p:project/p:build/p:directory' $manager
                sourceDirectory = Node-Text $document '/p:project/p:build/p:sourceDirectory' $manager
                testSourceDirectory = Node-Text $document '/p:project/p:build/p:testSourceDirectory' $manager
                outputDirectory = Node-Text $document '/p:project/p:build/p:outputDirectory' $manager
                testOutputDirectory = Node-Text $document '/p:project/p:build/p:testOutputDirectory' $manager
                resources = Resource-Metadata $document '/p:project/p:build/p:resources/p:resource' $manager
                testResources = Resource-Metadata $document '/p:project/p:build/p:testResources/p:testResource' $manager
            }
            surefire = @{
                groupId = 'org.apache.maven.plugins'; artifactId = 'maven-surefire-plugin'; version = $version
                reportsDirectory = $reportsDirectory; redirectTestOutputToFile = $true
                includes = @($plugin.SelectNodes('p:configuration/p:includes/p:include', $manager) | ForEach-Object { $_.InnerText })
                excludes = @($plugin.SelectNodes('p:configuration/p:excludes/p:exclude', $manager) | ForEach-Object { $_.InnerText })
            }
        } | ConvertTo-Json -Depth 12 -Compress
    }
} catch {
    # No raw XML, exception messages, environment values or authentication material leave this adapter.
    @{ ok = $false; code = 'INVALID_OR_UNSAFE_TEST_POM'; stage = $script:Stage } | ConvertTo-Json -Compress
    exit 2
}
