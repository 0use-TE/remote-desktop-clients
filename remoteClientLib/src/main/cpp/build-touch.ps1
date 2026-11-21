param([Parameter(Mandatory=$true)][string]$NdkRoot)
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path "$PSScriptRoot/../../../..").Path
$prebuilts = "$repoRoot/remoteClientLib/jni/libs/deps/FreeRDP/client/Android/Studio/freeRDPCore/src/main/jniLibs"
$compiler = "$NdkRoot/toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe"
$targets = @{ 'arm64-v8a'='aarch64-linux-android21'; 'armeabi-v7a'='armv7a-linux-androideabi21'; 'x86'='i686-linux-android21'; 'x86_64'='x86_64-linux-android21' }
foreach ($abi in $targets.Keys) {
    $lib = "$prebuilts/$abi"
    $output = "$repoRoot/remoteClientLib/src/main/jniLibs/$abi"
    New-Item -ItemType Directory -Force $output | Out-Null
    & $compiler "--target=$($targets[$abi])" -shared -fPIC -O2 -Wall -Wextra -Wno-unused-parameter -Werror '-Wl,--no-undefined' '-Wl,-z,max-page-size=16384' "-I$lib/include/freerdp2" "-I$lib/include/winpr2" "$PSScriptRoot/ouse_rdpei.c" "-L$lib" -lfreerdp2 -lwinpr2 -llog -o "$output/libouse-rdpei.so"
    if ($LASTEXITCODE -ne 0) { throw "Touch JNI build failed for $abi" }
}
