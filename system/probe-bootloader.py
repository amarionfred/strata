#!/usr/bin/env python3
"""Read selected fastboot capabilities. Does not reboot, unlock, flash, or erase."""

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import subprocess


# Keep these explicit: never accept arbitrary OEM or flashing commands.
QUERIES = (
    ("product", ("getvar", "product")),
    ("is_userspace", ("getvar", "is-userspace")),
    ("unlocked", ("getvar", "unlocked")),
    ("secure", ("getvar", "secure")),
    ("current_slot", ("getvar", "current-slot")),
    ("init_boot_has_slot", ("getvar", "has-slot:init_boot")),
    ("init_boot_a_size", ("getvar", "partition-size:init_boot_a")),
    ("unlock_ability", ("flashing", "get_unlock_ability")),
)


def run_query(command):
    try:
        result = subprocess.run(command, capture_output=True, text=True, timeout=8)
        return {
            "exit_code": result.returncode,
            "stdout": result.stdout.strip(),
            "stderr": result.stderr.strip(),
        }
    except subprocess.TimeoutExpired:
        return {"exit_code": None, "error": "timed_out"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fastboot", default="fastboot", help="Path to Android fastboot")
    parser.add_argument("--serial", help="Select a device when more than one is connected")
    parser.add_argument("--output", type=Path, help="Write the JSON report to this local file")
    args = parser.parse_args()

    try:
        listing = run_query([args.fastboot, "devices"])
        if listing["exit_code"] != 0:
            parser.exit(2, "Cannot enumerate fastboot devices. Check USB access.\n")
        devices = [line.split()[0] for line in listing["stdout"].splitlines()
                   if len(line.split()) >= 2 and line.split()[1] == "fastboot"]
        if args.serial:
            if args.serial not in devices:
                parser.exit(2, "The selected device is not connected in fastboot mode.\n")
            selected = args.serial
        elif len(devices) == 1:
            selected = devices[0]
        else:
            parser.exit(2, f"Expected one fastboot device; found {len(devices)}. "
                          "No device command was sent.\n")

        observations = {}
        for name, command in QUERIES:
            observations[name] = run_query([args.fastboot, "-s", selected, *command])
            # Avoid continuing to wait on an unplugged or unresponsive device.
            if observations[name]["exit_code"] is None:
                break
    except OSError as error:
        parser.exit(2, f"Cannot execute fastboot: {error}\n")

    report = {
        "report_version": 1,
        "collected_at_utc": datetime.now(timezone.utc).isoformat(),
        "device_serial_recorded": False,
        "observations": observations,
        "qualification": "unqualified",
        "notes": [
            "Only the listed information queries were sent. No reboot or partition write was requested.",
            "A successful unlock-ability query does not prove an unlock implementation works.",
            "An unknown command means that query is unsupported; it does not prove every unlock route is absent.",
            "fastbootd (is-userspace=yes) is not the bootloader; its answers do not qualify a bootloader unlock.",
            "Queries after the first timeout are omitted. A partial report is not a successful qualification.",
        ],
    }
    encoded = json.dumps(report, indent=2) + "\n"
    if args.output:
        # Exclusive creation protects earlier evidence from accidental overwrites.
        with args.output.open("x") as destination:
            destination.write(encoded)
        print(args.output)
    else:
        print(encoded, end="")
    return 0 if all(item["exit_code"] == 0 for item in observations.values()) else 1


if __name__ == "__main__":
    raise SystemExit(main())
