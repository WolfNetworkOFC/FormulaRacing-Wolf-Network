Add-Type -AssemblyName System.IO.Compression.FileSystem
$jar = 'C:\Users\Efraim\.gradle\caches\modules-2\files-2.1\dev.architectury\architectury-loom\1.7-SNAPSHOT\17ae8900180a6a0081be268aa641b7ee36087edd\architectury-loom-1.7-SNAPSHOT.jar'
$zip = [System.IO.Compression.ZipFile]::OpenRead($jar)
foreach ($entry in $zip.Entries) {
    if ($entry.FullName -match 'MixinExtensionApiImpl\$') {
        $reader = New-Object System.IO.StreamReader($entry.Open())
        $text = $reader.ReadToEnd()
        $reader.Close()
        Write-Output "=== $($entry.FullName) ==="
        [regex]::Matches($text, '[\x20-\x7E]{3,}') | ForEach-Object { $_.Value }
    }
}
$zip.Dispose()