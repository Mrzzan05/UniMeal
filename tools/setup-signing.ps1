# One-time setup: creates a private release key and stores it as GitHub secrets.
# Needs: Java (keytool) and GitHub CLI (gh, already logged in: `gh auth login`).
# Run from the repository folder:  powershell -ExecutionPolicy Bypass -File tools\setup-signing.ps1
$ErrorActionPreference = "Stop"
$repo  = "Mrzzan05/UniMeal-Android"
$alias = "unimeal"
$jks   = Join-Path $HOME "unimeal-release.jks"

if (Test-Path $jks) { throw "$jks already exists. Keep using it (do NOT create a new key: updates would fail to install)." }
foreach ($c in "keytool","gh") { if (-not (Get-Command $c -ErrorAction SilentlyContinue)) { throw "$c not found in PATH" } }

function New-Pass {
  $b = New-Object byte[] 24
  [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
  [Convert]::ToBase64String($b).Replace("+","A").Replace("/","B").Replace("=","")
}
$pass = New-Pass

keytool -genkeypair -v -keystore $jks -storetype PKCS12 -alias $alias -keyalg RSA -keysize 4096 -validity 10000 `
  -storepass $pass -keypass $pass -dname "CN=UniMeal, O=UniMeal, C=IR" | Out-Null

$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($jks))
$b64  | gh secret set UNIMEAL_KEYSTORE_B64      --repo $repo
$pass | gh secret set UNIMEAL_KEYSTORE_PASSWORD --repo $repo
$alias| gh secret set UNIMEAL_KEY_ALIAS         --repo $repo
$pass | gh secret set UNIMEAL_KEY_PASSWORD      --repo $repo

Write-Host ""
Write-Host "Done. Secrets stored in $repo." -ForegroundColor Green
Write-Host "Key file : $jks"
Write-Host "Password : $pass"
Write-Host ""
Write-Host "IMPORTANT: copy the key file AND the password to a safe place (password manager / USB)." -ForegroundColor Yellow
Write-Host "If you lose them you can never publish an update that installs over the old app." -ForegroundColor Yellow
Write-Host "Never commit them to git. Next: git tag v0.4.0; git push origin v0.4.0"
