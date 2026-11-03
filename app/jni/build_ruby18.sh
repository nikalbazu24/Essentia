#!/bin/bash
# build_ruby18.sh — Essentia (GPLv2)
#
# Builds Ruby 1.8.7 (the Ruby that RPG Maker XP itself used) for Android arm64,
# as static libraries for Essentia's "classic" engine (libmkxp-z-legacy.so).
#
# Output: ruby18-out/{include,lib}
#   lib/libruby18.a      the interpreter
#   lib/libruby18ext.a   built-in extensions (zlib, stringio, thread, digest/md5)
#
# Needs: ANDROID_NDK_HOME, git, autoconf, gcc, bison.

set -e

JNI="$(cd "$(dirname "$0")" && pwd)"
WORK="$JNI/ruby18-work"
OUT="$JNI/ruby18-out"
API=21
TC="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64"
NPROC=$(nproc)

# Old C code + modern compilers: signed overflow must wrap (Integer math),
# no strict aliasing, and old-style declarations are only warnings.
COMMON="-O2 -fwrapv -fno-strict-aliasing -std=gnu89 -Wno-error"
QUIET="-Wno-implicit-int -Wno-int-conversion -Wno-incompatible-pointer-types -Wno-return-type \
-Wno-deprecated-declarations -Wno-pointer-sign -Wno-unused-value -Wno-parentheses"

# Everything goes to a temporary folder first, so a failed build never leaves
# half a Ruby behind (the app build only uses ruby18-out when it's complete)
FINAL="$OUT"
OUT="$JNI/ruby18-out.tmp"
rm -rf "$OUT" "$FINAL"
mkdir -p "$WORK" "$OUT/lib" "$OUT/include"
cd "$WORK"

# ------------------------------------------------------------ source
if [ ! -d src ]; then
    git clone -q --depth 1 --branch v1_8_7_374 https://github.com/ruby/ruby.git src
fi
cd src
[ -f configure ] || autoconf
cp /usr/share/misc/config.sub /usr/share/misc/config.guess . 2>/dev/null || true

# Android: FILE is opaque on 64-bit bionic, but its first fields are the BSD ones.
if ! grep -q rb_android_sFILE io.c; then
    python3 - <<'EOF'
import re
p = 'io.c'
s = open(p).read()
anchor = '#elif defined(FILE_COUNT)\n#  define READ_DATA_PENDING(fp) ((fp)->FILE_COUNT > 0)'
add = '''#elif defined(__ANDROID__)
struct rb_android_sFILE { unsigned char *_p; int _r; };
#  define READ_DATA_PENDING(fp) (((struct rb_android_sFILE *)(fp))->_r > 0)
#  define READ_DATA_PENDING_COUNT(fp) (((struct rb_android_sFILE *)(fp))->_r)
#  define READ_DATA_PENDING_PTR(fp) ((char *)((struct rb_android_sFILE *)(fp))->_p)
'''
assert anchor in s
s = s.replace(anchor, add + anchor, 1)
open(p, 'w').write(s)
EOF
fi
# Keep only the extensions Essentia uses (the others don't build on modern
# systems or on Android, and --with-out-ext isn't reliable in Ruby 1.8)
for e in ext/*/; do
    case "$(basename "$e")" in
        zlib|stringio|thread|digest) ;;
        *) rm -rf "$e" ;;
    esac
done
rm -rf ext/digest/rmd160 ext/digest/sha1 ext/digest/sha2 ext/digest/bubblebabble
# Missing prototype (used before its declaration; -Werror catches these)
grep -q "essentia: prototype" eval.c || \
    sed -i '0,/^#include "ruby.h"/s//#include "ruby.h"\nint rb_thread_join _((VALUE, double)); \/* essentia: prototype *\//' eval.c
cd ..

# ------------------------------------------------------------ host Ruby 1.8
# Cross-compiling Ruby 1.8 needs a Ruby 1.8 on the build machine.
if [ ! -x hostruby/bin/ruby ]; then
    rm -rf host && mkdir host && cd host
    CFLAGS="$COMMON -Wno-implicit-function-declaration $QUIET" \
        ../src/configure --prefix="$WORK/hostruby" --disable-install-doc > configure.log
    make -j"$NPROC" > make.log 2>&1 || { tail -40 make.log; exit 1; }
    make install > install.log 2>&1 || { tail -40 install.log; exit 1; }
    cd ..
fi
export PATH="$WORK/hostruby/bin:$PATH"
ruby -v

# ------------------------------------------------------------ Android Ruby 1.8
# Only the extensions games use, linked in statically.
cat > src/ext/Setup <<'EOF'
option nodynamic
zlib
stringio
thread
digest
digest/md5
EOF

rm -rf cross && mkdir cross && cd cross
export CC="$TC/bin/aarch64-linux-android$API-clang"
export AR="$TC/bin/llvm-ar"
export RANLIB="$TC/bin/llvm-ranlib"
export NM="$TC/bin/llvm-nm"
export STRIP="$TC/bin/llvm-strip"
export CFLAGS="$COMMON -fPIC $QUIET -Werror=implicit-function-declaration"
export LDFLAGS="-lz -lm -ldl"

../src/configure --host=aarch64-linux-android --build=x86_64-pc-linux-gnu \
    --prefix="$WORK/android" --disable-shared --disable-install-doc --disable-pthread \
    ac_cv_header_pwd_h=no ac_cv_func_getpwent=no ac_cv_func_crypt=no ac_cv_lib_crypt_crypt=no \
    ac_cv_func_setpgrp_void=yes ac_cv_func_getpgrp_void=yes rb_cv_stack_grow_dir=-1 \
    ac_cv_func_memcmp_working=yes rb_cv_negative_time_t=yes rb_cv_binary_elf=yes \
    ac_cv_func_getcontext=no ac_cv_func_setcontext=no > configure.log 2>&1 || { tail -60 configure.log; exit 1; }

make -k -j"$NPROC" libruby-static.a > make.log 2>&1 || { grep -n -B2 "error:" make.log | head -120; tail -20 make.log; exit 1; }
make -j"$NPROC" exts > make-ext.log 2>&1 || make -j"$NPROC" > make-ext.log 2>&1 || { grep -n "error" make-ext.log | head -40; tail -60 make-ext.log; exit 1; }

# ------------------------------------------------------------ collect
cp libruby-static.a "$OUT/lib/libruby18.a"

rm -rf extobjs && mkdir extobjs
n=0
for a in $(find ext -name '*.a'); do
    n=$((n + 1))
    mkdir -p "extobjs/$n" && (cd "extobjs/$n" && "$AR" x "../../$a")
    for o in extobjs/$n/*.o; do mv "$o" "extobjs/e${n}_$(basename "$o")"; done
done
EXTINIT=$(find . -name 'extinit.o' | head -1)
if [ -z "$EXTINIT" ]; then
    # extinit.c lists the static extensions; build it if make didn't
    EXTINIT_C=$(find . -name 'extinit.c' | head -1)
    $CC $CFLAGS -c "$EXTINIT_C" -o extobjs/extinit.o
else
    cp "$EXTINIT" extobjs/extinit.o
fi
rm -f "$OUT/lib/libruby18ext.a"
"$AR" rcs "$OUT/lib/libruby18ext.a" extobjs/*.o
echo "Extensions:"; "$NM" "$OUT/lib/libruby18ext.a" | grep " T Init_" || true

cp ../src/*.h "$OUT/include/"
cp config.h "$OUT/include/config.h"
ls -la "$OUT/lib"
mv "$OUT" "$FINAL"
echo "Ruby 1.8.7 for Android ready."
