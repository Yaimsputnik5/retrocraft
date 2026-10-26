#!/usr/bin/env python3
"""Build and exercise each Minecraft/loader target, preserving logs and machine-readable results."""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import signal
import subprocess
import time
import xml.etree.ElementTree as ET
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
TARGETS = json.loads((ROOT / "gradle/targets.json").read_text())


def run(command, log, timeout, environment):
    with log.open("w") as output:
        output.write("Command: " + " ".join(command) + "\n\n")
        output.flush()
        process = subprocess.Popen(command, cwd=ROOT, env=environment, stdout=output,
                                   stderr=subprocess.STDOUT, start_new_session=True)
        try:
            return process.wait(timeout=timeout)
        except (subprocess.TimeoutExpired, KeyboardInterrupt) as error:
            os.killpg(process.pid, signal.SIGTERM)
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait()
            if isinstance(error, KeyboardInterrupt):
                raise
            return 124


def test_counts(directory):
    counts = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
    for file in directory.glob("**/TEST-*.xml"):
        suite = ET.parse(file).getroot()
        for key in counts:
            counts[key] += int(suite.get(key, "0"))
    return counts


def inspect_artifact(file, version, loader):
    with ZipFile(file) as jar:
        names = jar.namelist()
        if any("/smoke/" in name or "/gametest/" in name for name in names):
            raise ValueError("Development tests were included in the production JAR")
        if "retrocraft/retrocraft-worker.jar" not in names:
            raise ValueError("Missing bundled worker")
        if loader == "fabric":
            metadata = json.loads(jar.read("fabric.mod.json"))
            if metadata["depends"]["minecraft"] != version:
                raise ValueError("Incorrect Minecraft dependency in Fabric metadata")
        elif f'[{version}]' not in jar.read("META-INF/mods.toml").decode():
            raise ValueError("Incorrect Minecraft dependency in Forge metadata")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--versions", nargs="+", choices=list(TARGETS), default=list(TARGETS))
    parser.add_argument("--loaders", nargs="+", choices=["fabric", "forge"], default=["fabric", "forge"])
    parser.add_argument("--offline", action="store_true")
    parser.add_argument("--compile-only", action="store_true")
    parser.add_argument("--no-client", action="store_true")
    parser.add_argument("--timeout", type=int, default=1200)
    args = parser.parse_args()
    directory = ROOT / "build/matrix"
    directory.mkdir(parents=True, exist_ok=True)
    result_file = directory / "results.json"
    results = json.loads(result_file.read_text()) if result_file.exists() else {}
    failed = False
    for version in args.versions:
        for loader in args.loaders:
            key = f"{version}-{loader}"
            result = {"minecraft": version, "loader": loader, "java": TARGETS[version]["java"]}
            results[key] = result
            if loader == "forge" and TARGETS[version]["forge"] is None:
                result["status"] = "unavailable"
                print(f"{key}: Forge has no release for this Minecraft version", flush=True)
            else:
                start = time.monotonic()
                wrapper = "tools/gradle-modern" if result["java"] == 25 else "./gradlew"
                base = [str(ROOT / wrapper), f"-Pmc={version}", f"-Ploader={loader}", "--console=plain"]
                if args.offline:
                    base.append("--offline")
                log = directory / f"{key}.log"
                print(f"{key}: {'compile' if args.compile_only else 'build + JUnit + GameTests'}", flush=True)
                code = run(base + ([f":{loader}:compileJava"] if args.compile_only else ["build"]),
                           log, args.timeout, os.environ.copy())
                result.update(exit_code=code, log=str(log.relative_to(ROOT)))
                result["status"] = "failed" if code else "compiled" if args.compile_only else "passed"
                if not code and not args.compile_only:
                    result["junit"] = test_counts(ROOT / f"build/targets/{version}/{loader}/test-results")
                    text = log.read_text(errors="replace")
                    passed = re.findall(r"All (\d+) required tests passed", text)
                    result["gametests"] = int(passed[-1]) if passed else 0
                    artifacts = list((ROOT / f"build/targets/{version}/{loader}/libs").glob("*.jar"))
                    artifacts = [p for p in artifacts if not p.name.endswith(("-sources.jar", "-dev.jar"))]
                    try:
                        if result["gametests"] < 10:
                            raise ValueError("The shared GameTest suite did not complete")
                        if len(artifacts) != 1:
                            raise ValueError(f"Expected one production artifact, found {len(artifacts)}")
                        inspect_artifact(artifacts[0], version, loader)
                        result["artifact"] = str(artifacts[0].relative_to(ROOT))
                    except ValueError as error:
                        result.update(status="failed", error=str(error))
                    if result["status"] == "passed" and not args.no_client:
                        smoke_log = directory / f"{key}-client.log"
                        command = base + ["-Psmoke", "runClient"]
                        if not os.environ.get("DISPLAY") and shutil.which("xvfb-run"):
                            command = ["xvfb-run", "-a"] + command
                        smoke_code = run(command, smoke_log, args.timeout, os.environ.copy())
                        result["client_log"] = str(smoke_log.relative_to(ROOT))
                        result["client_smoke"] = smoke_code == 0 and "RETROCRAFT_CLIENT_SMOKE_OK" in smoke_log.read_text(errors="replace")
                        if not result["client_smoke"]:
                            result["status"] = "failed"
                result["seconds"] = round(time.monotonic() - start, 1)
                failed |= result["status"] == "failed"
                print(f"{key}: {result['status']} ({result['seconds']}s)", flush=True)
            result_file.write_text(json.dumps(results, indent=2) + "\n")
    return int(failed)


if __name__ == "__main__":
    raise SystemExit(main())
