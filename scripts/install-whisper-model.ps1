$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Dest = Join-Path $Root "app/src/main/assets/whisper/ggml-base.bin"
New-Item -ItemType Directory -Force -Path (Split-Path $Dest) | Out-Null
$Url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin"
Invoke-WebRequest -Uri $Url -OutFile $Dest
Get-FileHash $Dest -Algorithm SHA256
