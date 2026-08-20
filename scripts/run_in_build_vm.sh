#!/usr/bin/env bash
#
# Host-side Android build VM. Shared by dustvalve_next, calc, compass, and
# core. Gradle/GMD leak RSS into host swap if they run on the host; this
# wrapper boots a throwaway KVM guest, runs ./build.sh there, then kills
# QEMU so guest RAM/swap/page-cache die with the process.
#
# Guest: 12 GiB RAM, 16 vCPUs, KVM nested for GMD, virtio-blk/net/rng/fs.
# Only one VM at a time: the same ~/.cache/android-apps/build.lock that
# ./build.sh already uses is held on the host for the whole QEMU lifetime.
#
# Env overrides:
#   ANDROID_BUILD_ON_HOST=1     skip the VM (debug the host path)
#   ANDROID_BUILD_VM_CPUS=N     vCPU count (default 16)
#   ANDROID_BUILD_VM_RAM_MIB=N  guest RAM in MiB (default 12288)
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

CACHE_DIR="${XDG_CACHE_HOME:-$HOME/.cache}/android-apps"
LOCKFILE="$CACHE_DIR/build.lock"
VM_DIR="$CACHE_DIR/build-vm"
# Bump when the guest image layout changes so the next build recreates it.
IMAGE_VERSION=1
ROOT_IMG="$VM_DIR/root.img"
VMLINUZ="$VM_DIR/vmlinuz"
INITRD="$VM_DIR/initrd"
VERSION_STAMP="$VM_DIR/image-version"
PAYLOAD="$VM_DIR/payload.sh"
EXITCODE="$VM_DIR/exitcode"
VIRTIOFS_SOCK="$VM_DIR/virtiofs.sock"
VIRTIOFS_LOG="$VM_DIR/virtiofsd.log"
QEMU_PIDFILE="$VM_DIR/qemu.pid"

CPUS="${ANDROID_BUILD_VM_CPUS:-16}"
RAM_MIB="${ANDROID_BUILD_VM_RAM_MIB:-12288}"
RAM_QEMU="${RAM_MIB}M"

VIRTIOFSD="${ANDROID_BUILD_VIRTIOFSD:-/usr/lib/virtiofsd}"
QEMU="${ANDROID_BUILD_QEMU:-/usr/bin/qemu-system-x86_64}"

usage_and_die() {
    echo "ERROR: $*" >&2
    exit 1
}

acquire_host_lock() {
    mkdir -p "$CACHE_DIR"
    exec 9>"$LOCKFILE"
    if ! flock -n 9; then
        echo "Another Android app build/clean is already running" \
            "(dustvalve_next/calc/compass/core share $LOCKFILE). Waiting..."
        flock 9
    fi
}

# NetBird APK serving is a host service. Stop it before the guest starts so
# the next successful build can re-publish from the host after QEMU exits.
stop_host_apk_serve() {
    "$SCRIPT_DIR/apk_http_serve.sh" stop || true
}

maybe_start_host_apk_serve() {
    local serve=0 arg
    if [[ $# -eq 0 ]]; then
        serve=1
    fi
    for arg in "$@"; do
        case "$arg" in
            --debug|--build-health|--force-baseline) serve=1 ;;
            --clean|--format|--publish) serve=0 ;;
        esac
    done
    [[ "$serve" -eq 1 ]] || return 0

    local -a apks=()
    local f
    for f in app-release.apk app-release-future.apk app-debug.apk app-debug-future.apk; do
        [[ -f "$REPO_ROOT/$f" ]] && apks+=("$f")
    done
    if [[ ${#apks[@]} -eq 0 ]]; then
        return 0
    fi
    "$SCRIPT_DIR/apk_http_serve.sh" start --optional "${apks[@]}"
}

ensure_tools() {
    [[ -x "$QEMU" ]] || usage_and_die "qemu-system-x86_64 not found at $QEMU"
    [[ -x "$VIRTIOFSD" ]] || usage_and_die "virtiofsd not found at $VIRTIOFSD"
    [[ -e /dev/kvm ]] || usage_and_die "/dev/kvm missing; the build VM needs KVM"
    command -v sudo >/dev/null || usage_and_die "sudo is required to create the guest image"
}

write_guest_file() {
    local dest="$1"
    sudo tee "$dest" >/dev/null
}

configure_guest_root() {
    local mnt="$1"

    write_guest_file "$mnt/etc/hostname" <<'EOF'
android-build
EOF

    write_guest_file "$mnt/etc/locale.conf" <<'EOF'
LANG=C.UTF-8
EOF

    write_guest_file "$mnt/etc/hosts" <<'EOF'
127.0.0.1 localhost
10.0.2.15 android-build
EOF

    write_guest_file "$mnt/etc/resolv.conf" <<'EOF'
nameserver 10.0.2.3
nameserver 1.1.1.1
EOF

    write_guest_file "$mnt/etc/fstab" <<'EOF'
/dev/vda / ext4 defaults,discard 0 1
/swapfile none swap defaults 0 0
EOF

    # Force virtio + nested KVM into the initramfs. pacstrap's autodetect
    # saw the host (bare metal) and would omit virtio_blk.
    write_guest_file "$mnt/etc/mkinitcpio.conf" <<'EOF'
MODULES=(virtio_pci virtio_blk virtio_net virtiofs virtio_rng kvm kvm_amd)
BINARIES=()
FILES=()
HOOKS=(base udev autodetect microcode modconf kms keyboard keymap consolefont block filesystems fsck)
COMPRESSION="zstd"
EOF

    write_guest_file "$mnt/etc/systemd/network/20-virtio.network" <<'EOF'
[Match]
Name=eth0 en*

[Network]
DHCP=yes
DNS=10.0.2.3
EOF

    write_guest_file "$mnt/etc/udev/rules.d/99-kvm.rules" <<'EOF'
KERNEL=="kvm", GROUP="kvm", MODE="0666"
EOF

    sudo mkdir -p "$mnt/home/user" "$mnt/etc/systemd/system/multi-user.target.wants"

    write_guest_file "$mnt/etc/systemd/system/home-user.mount" <<'EOF'
[Unit]
Description=Host home via virtiofs
Before=android-build.service

[Mount]
What=home
Where=/home/user
Type=virtiofs
Options=rw

[Install]
WantedBy=multi-user.target
EOF

    write_guest_file "$mnt/usr/local/sbin/wait-android-build-payload.sh" <<'EOF'
#!/bin/bash
i=0
while [[ "$i" -lt 60 ]]; do
    if [[ -x /home/user/.cache/android-apps/build-vm/payload.sh ]]; then
        exit 0
    fi
    i=$((i + 1))
    sleep 1
done
echo "virtiofs home/payload missing" >&2
exit 1
EOF
    sudo chmod 755 "$mnt/usr/local/sbin/wait-android-build-payload.sh"

    write_guest_file "$mnt/etc/systemd/system/android-build.service" <<'EOF'
[Unit]
Description=Run the host Android build payload, then power off
After=home-user.mount local-fs.target systemd-networkd.service
Requires=home-user.mount

[Service]
Type=oneshot
KillMode=mixed
WorkingDirectory=/home/user
Environment=HOME=/home/user
Environment=USER=user
Environment=ANDROID_BUILD_IN_VM=1
Environment=ANDROID_BUILD_LOCK_HELD=1
Environment=ANDROID_HOME=/home/user/Android/Sdk
Environment=ANDROID_SDK_ROOT=/home/user/Android/Sdk
Environment=PATH=/usr/lib/jvm/java-26-openjdk/bin:/home/user/Android/Sdk/platform-tools:/home/user/Android/Sdk/emulator:/usr/local/sbin:/usr/local/bin:/usr/bin
ExecStartPre=/usr/local/sbin/wait-android-build-payload.sh
ExecStart=/bin/bash /home/user/.cache/android-apps/build-vm/payload.sh
ExecStopPost=-/usr/sbin/poweroff -f
StandardOutput=journal+console
StandardError=journal+console
TimeoutStartSec=infinity
TimeoutStopSec=15

[Install]
WantedBy=multi-user.target
EOF

    sudo arch-chroot "$mnt" /bin/bash -s <<'CHROOT'
set -euo pipefail
if [[ ! -e /sbin/init ]]; then
    ln -sf /usr/lib/systemd/systemd /sbin/init
fi
getent group kvm >/dev/null || groupadd -r kvm
getent group user >/dev/null || groupadd -g 1000 user
if ! getent passwd user >/dev/null; then
    useradd -u 1000 -g 1000 -d /home/user -s /bin/bash user
fi
usermod -u 1000 -g 1000 -d /home/user user || true
usermod -aG kvm user || true
systemctl enable systemd-networkd.service
systemctl enable android-build.service
systemctl enable home-user.mount
systemctl set-default multi-user.target
systemctl mask serial-getty@ttyS0.service >/dev/null 2>&1 || true
systemctl disable getty@tty1.service >/dev/null 2>&1 || true
systemctl mask systemd-resolved.service >/dev/null 2>&1 || true
if [[ ! -f /swapfile ]]; then
    fallocate -l 8G /swapfile
    chmod 600 /swapfile
    mkswap /swapfile
fi
mkinitcpio -P
CHROOT
}

ensure_guest_image() {
    mkdir -p "$VM_DIR"
    if [[ -f "$VERSION_STAMP" && -f "$ROOT_IMG" && -f "$VMLINUZ" && -f "$INITRD" ]]; then
        if [[ "$(cat "$VERSION_STAMP")" == "$IMAGE_VERSION" ]]; then
            return 0
        fi
    fi

    echo "Creating Android build VM image (version $IMAGE_VERSION) in $VM_DIR ..."

    local work="$VM_DIR/mnt"
    if mountpoint -q "$work" 2>/dev/null; then
        sudo umount "$work" || true
    fi
    rm -f "$ROOT_IMG" "$VMLINUZ" "$INITRD" "$VERSION_STAMP"
    mkdir -p "$work"
    # Sparse 24G: guest OS + 8G swapfile. QEMU -snapshot discards writes.
    qemu-img create -f raw "$ROOT_IMG" 24G >/dev/null
    # ext4 volume labels are 16 bytes max.
    sudo mkfs.ext4 -F -L androidbuild "$ROOT_IMG" >/dev/null
    sudo mount -o loop "$ROOT_IMG" "$work"

    ANDROID_BUILD_VM_MNT="$work"
    cleanup_mnt() {
        if [[ -n "${ANDROID_BUILD_VM_MNT:-}" ]] && mountpoint -q "$ANDROID_BUILD_VM_MNT" 2>/dev/null; then
            sudo umount "$ANDROID_BUILD_VM_MNT" 2>/dev/null || true
        fi
        ANDROID_BUILD_VM_MNT=""
    }
    trap cleanup_mnt EXIT

    sudo pacstrap -c -K "$work" \
        base linux \
        python jdk-openjdk \
        gperftools libx11 nss \
        unzip zip git which procps-ng iproute2

    configure_guest_root "$work"

    sudo cp "$work/boot/vmlinuz-linux" "$VMLINUZ"
    sudo cp "$work/boot/initramfs-linux.img" "$INITRD"
    sudo chmod a+r "$VMLINUZ" "$INITRD"
    cleanup_mnt
    trap - EXIT
    rmdir "$work" 2>/dev/null || true

    echo "$IMAGE_VERSION" >"$VERSION_STAMP"
    echo "Android build VM image ready."
}

write_payload() {
    local repo_in_guest="$1"
    shift
    mkdir -p "$VM_DIR"
    rm -f "$EXITCODE"
    # Payload lives on the host cache (virtiofs-mounted at the same path).
    {
        echo '#!/bin/bash'
        echo 'set -uo pipefail'
        echo "cd $(printf '%q' "$repo_in_guest") || { echo 99 > $(printf '%q' "$EXITCODE"); /usr/sbin/poweroff -f; exit 1; }"
        echo 'export ANDROID_BUILD_IN_VM=1'
        echo 'export ANDROID_BUILD_LOCK_HELD=1'
        echo 'export HOME=/home/user'
        echo 'export USER=user'
        echo 'export ANDROID_HOME=/home/user/Android/Sdk'
        echo 'export ANDROID_SDK_ROOT=/home/user/Android/Sdk'
        echo 'export PATH="/usr/lib/jvm/java-26-openjdk/bin:/home/user/Android/Sdk/platform-tools:/home/user/Android/Sdk/emulator:/usr/local/sbin:/usr/local/bin:/usr/bin"'
        printf 'echo "Android build VM: %s %s"\n' \
            "$(printf '%q' "$repo_in_guest")" \
            "$(printf '%q' "$*")"
        echo 'set +e'
        echo 'if [[ "$(id -u)" -eq 0 ]]; then'
        printf '  runuser -u user -- env HOME=/home/user USER=user ANDROID_BUILD_IN_VM=1 ANDROID_BUILD_LOCK_HELD=1 ANDROID_HOME=/home/user/Android/Sdk ANDROID_SDK_ROOT=/home/user/Android/Sdk PATH="$PATH" ./build.sh'
        local a
        for a in "$@"; do
            printf ' %q' "$a"
        done
        echo
        echo 'else'
        printf '  ./build.sh'
        for a in "$@"; do
            printf ' %q' "$a"
        done
        echo
        echo 'fi'
        echo "rc=\$?"
        echo "echo \"\$rc\" > $(printf '%q' "$EXITCODE")"
        echo 'if [[ "$(id -u)" -eq 0 ]]; then'
        echo '  exec /usr/sbin/poweroff -f'
        echo 'fi'
        echo 'exit 0'
    } >"$PAYLOAD"
    chmod +x "$PAYLOAD"
}

VIRTIOFSD_PID=""
QEMU_PID=""

cleanup_vm() {
    local qpid="${QEMU_PID:-}"
    if [[ -z "$qpid" && -f "$QEMU_PIDFILE" ]]; then
        qpid="$(cat "$QEMU_PIDFILE" 2>/dev/null || true)"
    fi
    if [[ -n "$qpid" ]] && kill -0 "$qpid" 2>/dev/null; then
        kill "$qpid" 2>/dev/null || true
        wait "$qpid" 2>/dev/null || true
    fi
    if [[ -n "${VIRTIOFSD_PID}" ]] && kill -0 "$VIRTIOFSD_PID" 2>/dev/null; then
        kill "$VIRTIOFSD_PID" 2>/dev/null || true
        wait "$VIRTIOFSD_PID" 2>/dev/null || true
    fi
    rm -f "$VIRTIOFS_SOCK" "$QEMU_PIDFILE"
}

start_virtiofsd() {
    rm -f "$VIRTIOFS_SOCK"
    "$VIRTIOFSD" \
        --socket-path="$VIRTIOFS_SOCK" \
        --shared-dir="$HOME" \
        --sandbox none \
        --cache always \
        --writeback \
        --thread-pool-size "$CPUS" \
        --announce-submounts \
        >"$VIRTIOFS_LOG" 2>&1 &
    VIRTIOFSD_PID=$!
    local i
    for i in $(seq 1 50); do
        if [[ -S "$VIRTIOFS_SOCK" ]]; then
            return 0
        fi
        if ! kill -0 "$VIRTIOFSD_PID" 2>/dev/null; then
            echo "ERROR: virtiofsd exited. Last log:" >&2
            tail -n 40 "$VIRTIOFS_LOG" >&2 || true
            return 1
        fi
        sleep 0.1
    done
    echo "ERROR: virtiofsd socket never appeared at $VIRTIOFS_SOCK" >&2
    return 1
}

run_qemu() {
    echo "Starting Android build VM (${RAM_QEMU} RAM, ${CPUS} vCPUs, virtio, nested KVM)."
    # Direct kernel boot + virtio-blk root. -snapshot so guest OS/swap writes
    # die with QEMU. virtiofs home keeps project/SDK/gradle cache on the host.
    "$QEMU" \
        -name android-build \
        -object memory-backend-memfd,id=mem,size="$RAM_QEMU",share=on \
        -machine q35,accel=kvm,kernel-irqchip=on,memory-backend=mem \
        -cpu host,migratable=off \
        -enable-kvm \
        -smp "$CPUS" \
        -m "$RAM_QEMU" \
        -kernel "$VMLINUZ" \
        -initrd "$INITRD" \
        -append "root=/dev/vda rootfstype=ext4 rw console=ttyS0 net.ifnames=0 panic=10 systemd.firstboot=0 systemd.hostname=android-build systemd.mask=serial-getty@ttyS0.service ip=10.0.2.15::10.0.2.2:255.255.255.0:android-build:eth0:off:10.0.2.3" \
        -object iothread,id=io1 \
        -drive file="$ROOT_IMG",if=none,id=rootdisk,format=raw,snapshot=on,cache=unsafe,discard=unmap \
        -device virtio-blk-pci,drive=rootdisk,iothread=io1,bootindex=1 \
        -netdev user,id=net0,net=10.0.2.0/24,dhcpstart=10.0.2.15 \
        -device virtio-net-pci,netdev=net0 \
        -device virtio-rng-pci \
        -chardev socket,id=fs0,path="$VIRTIOFS_SOCK" \
        -device vhost-user-fs-pci,queue-size=1024,chardev=fs0,tag=home \
        -nographic \
        -no-reboot \
        -pidfile "$QEMU_PIDFILE"
}

# --- main ---
if [[ "${1:-}" == "--ensure-image" ]]; then
    ensure_tools
    ensure_guest_image
    exit 0
fi

ensure_tools
ensure_guest_image
stop_host_apk_serve
acquire_host_lock
write_payload "$REPO_ROOT" "$@"

trap cleanup_vm EXIT
start_virtiofsd
run_qemu
QEMU_RC=$?
cleanup_vm
trap - EXIT

GUEST_RC=1
if [[ -f "$EXITCODE" ]]; then
    GUEST_RC="$(cat "$EXITCODE" 2>/dev/null || echo 1)"
fi
if [[ ! -f "$EXITCODE" ]]; then
    echo "ERROR: build VM exited without a payload status (qemu rc=$QEMU_RC)." >&2
    echo "virtiofsd log: $VIRTIOFS_LOG" >&2
    tail -n 40 "$VIRTIOFS_LOG" >&2 || true
    exit 1
fi

if [[ "$GUEST_RC" -eq 0 ]]; then
    maybe_start_host_apk_serve "$@"
fi
exit "$GUEST_RC"
