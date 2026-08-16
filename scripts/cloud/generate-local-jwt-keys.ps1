param(
    [string]$OutputDirectory = ''
)

$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'C:\Program Files\Java\jdk-21' }
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $projectRoot 'cloud\dev-keys'
}

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

$javaSource = @'
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

public class GenerateLocalJwtKeys {
    static String pem(String type, byte[] der) {
        String b64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + b64 + "\n-----END " + type + "-----\n";
    }

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        Files.writeString(dir.resolve("local-dev-public.pem"),
                pem("PUBLIC KEY", keyPair.getPublic().getEncoded()));
        Files.writeString(dir.resolve("local-dev-private.pem"),
                pem("PRIVATE KEY", keyPair.getPrivate().getEncoded()));
    }
}
'@

$sourceFile = Join-Path $env:TEMP 'GenerateLocalJwtKeys.java'
Set-Content -Path $sourceFile -Value $javaSource -Encoding ASCII
Push-Location $env:TEMP
try {
    javac GenerateLocalJwtKeys.java
    java GenerateLocalJwtKeys $OutputDirectory
} finally {
    Pop-Location
}

Write-Host "Generated local-dev JWT keys in $OutputDirectory"
Write-Host 'These keys are gitignored. Set:'
Write-Host ("  JWT_PUBLIC_KEY_LOCATION=file:{0}\local-dev-public.pem" -f $OutputDirectory)
Write-Host ("  JWT_PRIVATE_KEY_LOCATION=file:{0}\local-dev-private.pem" -f $OutputDirectory)
