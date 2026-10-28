#!/system/bin/sh
# SPDX-License-Identifier: AGPL-3.0-only

HERE="$(cd "$(dirname "$0")" && pwd)"
ASAN_LIB="$(find "$HERE" -name 'libclang_rt.asan-*-android.so' | head -n 1)"
if [ -z "$ASAN_LIB" ]; then
    echo "GPSx ASan runtime missing" >&2
    exit 1
fi
export ASAN_OPTIONS="detect_leaks=0:new_delete_type_mismatch=0:halt_on_error=1:log_to_syslog=false"
export LD_PRELOAD="$ASAN_LIB"
exec "$@"
