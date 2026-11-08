# 下载 Noto Serif SC 到 app/src/main/assets/fonts/NotoSerifSC.otf
# 用途：标题宋体。没有这个文件时，代码会退回系统衬线（国产 ROM 上会变黑体）。
# 许可：SIL OFL 1.1，可随 App 分发，保留版权声明即可。

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$dest = Join-Path $root "android\app\src\main\assets\fonts"
$file = Join-Path $dest "NotoSerifSC.otf"

# 官方 noto-cjk 仓库的 SC 子集 OTF（约 8 MB）
$urls = @(
    "https://raw.githubusercontent.com/notofonts/noto-cjk/main/Serif/SubsetOTF/SC/NotoSerifSC-SemiBold.otf",
    "https://raw.githubusercontent.com/googlefonts/noto-cjk/main/Serif/SubsetOTF/SC/NotoSerifSC-SemiBold.otf"
)

New-Item -ItemType Directory -Force $dest | Out-Null

if (Test-Path $file) {
    Write-Host "已存在：$file（如需重下请先删除）" -ForegroundColor Yellow
    exit 0
}

foreach ($u in $urls) {
    try {
        Write-Host "下载 $u"
        Invoke-WebRequest -Uri $u -OutFile $file -UseBasicParsing -TimeoutSec 120
        $size = [math]::Round((Get-Item $file).Length / 1MB, 1)
        Write-Host "完成：$file（$size MB）" -ForegroundColor Green
        exit 0
    } catch {
        Write-Host "失败：$($_.Exception.Message)" -ForegroundColor DarkYellow
        if (Test-Path $file) { Remove-Item $file -Force }
    }
}

Write-Host "两个源都失败。可手动下载后放到：$file" -ForegroundColor Red
exit 1
