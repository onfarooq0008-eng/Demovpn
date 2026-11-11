#!/usr/bin/env bash
# Usage: scripts/fetch_engine.sh [--ics] [--ovpn3]   (no flag = both)
# --ics   : ics-openvpn (OpenVPN for Android, GPLv2)  -> ./ics-openvpn
# --ovpn3 : OpenVPN 3 core (AGPLv3) + asio            -> ./third_party
# Pin ICS_REF / OPENVPN3_REF / ASIO_REF to commits you have tested.
set -euo pipefail
cd "$(dirname "$0")/.."
ics=0; o3=0
for a in "$@"; do case $a in --ics) ics=1;; --ovpn3) o3=1;; esac; done
[ $ics = 0 ] && [ $o3 = 0 ] && ics=1 && o3=1
if [ $ics = 1 ] && [ ! -d ics-openvpn ]; then
  git clone --recurse-submodules https://github.com/schwabe/ics-openvpn.git ics-openvpn
  [ -z "${ICS_REF:-}" ] || { git -C ics-openvpn checkout "$ICS_REF"; git -C ics-openvpn submodule update --init --recursive; }
fi
if [ $o3 = 1 ]; then
  mkdir -p third_party
  [ -d third_party/openvpn3 ] || git clone https://github.com/OpenVPN/openvpn3.git third_party/openvpn3
  [ -d third_party/asio ] || git clone https://github.com/OpenVPN/asio.git third_party/asio
  [ -z "${OPENVPN3_REF:-}" ] || git -C third_party/openvpn3 checkout "$OPENVPN3_REF"
  [ -z "${ASIO_REF:-}" ] || git -C third_party/asio checkout "$ASIO_REF"
fi
echo "Engine sources ready."
