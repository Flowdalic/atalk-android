#!/bin/bash
export PLATFORM="android-21"
SYSROOT=$NDK/platforms/$PLATFORM/arch-x86_64/
TOOLCHAIN=$NDK/toolchains/x86_64-4.9/prebuilt/linux-x86_64
rm -f $(pwd)/compat/strtod.o

function build_target
{
./configure \
    $COMMON $CONFIGURATION \
    --prefix=$PREFIX \
    --cross-prefix=$CROSS_PREFIX \
    --nm=${CROSS_PREFIX}nm \
    --sysroot=$SYSROOT \
    --cc=${CROSS_PREFIX}gcc \
    --extra-libs="-lgcc" \
    --target-os=linux \
    --arch=x86_64 \
    --disable-asm \
    --sysroot=$SYSROOT \
    --extra-cflags="-O3 -Wall -pipe -DANDROID -DNDEBUG  -march=atom -msse3 -ffast-math -mfpmath=sse $ADDI_CFLAGS -I../x264/android/$CPU/include" \
    --extra-ldflags="-lm -lz -Wl,--no-undefined -Wl,-z,noexecstack $ADDI_LDFLAGS -L../x264/android/$CPU/lib"

make clean
make -j4
make install
}

export CPU=x86_64
PREFIX=./android/$CPU 
CROSS_PREFIX=$TOOLCHAIN/bin/x86_64-linux-android-

build_target

cd $PROJECT_JNI
export ABI=$CPU
# $NDK/ndk-build
# cp -r "$PROJECT_LIBS/$CPU" "$PROJECT_LIBS/../out" 
# cd $DIR

echo "=== Android ffmpeg for $CPU builds completed ==="
