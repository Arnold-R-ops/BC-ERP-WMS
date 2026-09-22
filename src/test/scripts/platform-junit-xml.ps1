# Test infrastructure only. XML arrives as JSON on stdin. No file, network or database reads.
$ErrorActionPreference = 'Stop'
[Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
function Read-Count($element, [string]$name) {
    $value = $element.GetAttribute($name)
    $parsed = 0
    if ($value -notmatch '^(0|[1-9][0-9]*)$' -or
        -not [int]::TryParse($value, [ref]$parsed) -or $parsed -gt 1000000) {
        throw 'INVALID_COUNT'
    }
    return $parsed
}
try {
    $request = [Console]::In.ReadToEnd() | ConvertFrom-Json
    if ($request.xml -isnot [string] -or $request.xml.Length -gt 8388608) { throw 'INVALID_REQUEST' }
    $settings = [System.Xml.XmlReaderSettings]::new()
    $settings.DtdProcessing = [System.Xml.DtdProcessing]::Prohibit
    $settings.XmlResolver = $null
    $settings.MaxCharactersInDocument = 8388608
    $settings.MaxCharactersFromEntities = 1024
    $reader = [System.Xml.XmlReader]::Create([System.IO.StringReader]::new($request.xml), $settings)
    try {
        $doc = [System.Xml.XmlDocument]::new()
        $doc.XmlResolver = $null
        $doc.Load($reader)
    } finally { $reader.Dispose() }
    $root = $doc.DocumentElement
    if ($root.get_LocalName() -ne 'testsuite' -or $root.get_NamespaceURI() -ne '' -or
        [string]::IsNullOrWhiteSpace($root.GetAttribute('name'))) { throw 'INVALID_SUITE' }
    $header = @{}
    foreach ($field in @('tests', 'failures', 'errors', 'skipped')) { $header[$field] = Read-Count $root $field }
    $cases = @()
    foreach ($child in $root.get_ChildNodes()) {
        if ($child.get_NodeType() -ne [System.Xml.XmlNodeType]::Element) { continue }
        if ($child.get_NamespaceURI() -ne '' -or $child.get_LocalName() -notin @('testcase', 'properties', 'system-out', 'system-err')) {
            throw 'INVALID_SUITE_CHILD'
        }
        if ($child.get_LocalName() -ne 'testcase') { continue }
        $name = $child.GetAttribute('name')
        $class = $child.GetAttribute('classname')
        if ([string]::IsNullOrWhiteSpace($name) -or [string]::IsNullOrWhiteSpace($class)) { throw 'INVALID_CASE' }
        $outcomes = @()
        foreach ($node in $child.get_ChildNodes()) {
            if ($node.get_NodeType() -ne [System.Xml.XmlNodeType]::Element) { continue }
            if ($node.get_NamespaceURI() -ne '' -or $node.get_LocalName() -notin @('failure', 'error', 'skipped', 'system-out', 'system-err')) {
                throw 'INVALID_CASE_CHILD'
            }
            if ($node.get_LocalName() -in @('failure', 'error', 'skipped')) { $outcomes += $node.get_LocalName() }
        }
        if ($outcomes.Count -gt 1) { throw 'MULTIPLE_OUTCOMES' }
        $cases += @{
            name = $name; classname = $class
            failure = $outcomes -contains 'failure'; error = $outcomes -contains 'error'; skipped = $outcomes -contains 'skipped'
        }
    }
    # Deliberately omit environment properties, stdout/stderr, exception details and authentication material.
    @{ ok = $true; name = $root.GetAttribute('name'); header = $header; cases = @($cases) } |
        ConvertTo-Json -Depth 6 -Compress
} catch {
    # XML/PowerShell exception messages can contain input values: return only a fixed failure code.
    @{ ok = $false; code = 'INVALID_OR_UNSAFE_JUNIT_XML' } | ConvertTo-Json -Compress
    exit 2
}
