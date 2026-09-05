#!/usr/bin/env python3
"""Fail with a readable report when Maven and Gradle library outputs drift."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODULES = (
    "openrouter-spring-ai",
    "openrouter-spring-ai-autoconfigure",
    "openrouter-spring-ai-starter",
)
NAMESPACE = {"m": "http://maven.apache.org/POM/4.0.0"}
MAVEN_TEST_GRAPH_EXCLUSIONS = frozenset({"org.apiguardian:apiguardian-api"})
GRADLE_TEST_GRAPH_EXCLUSIONS = frozenset({"org.junit.platform:junit-platform-launcher"})


@dataclass(frozen=True)
class BuildHashes:
    """JAR hashes captured before a later clean build can replace the artifacts."""

    maven: dict[str, str]
    gradle: dict[str, str]


def run(command: list[str]) -> None:
    executable = shutil.which(command[0])
    if executable is None:
        raise FileNotFoundError(
            f"Required build executable is not on PATH: {command[0]}"
        )
    command[0] = executable
    print(f"\n$ {' '.join(command)}", flush=True)
    subprocess.run(command, cwd=ROOT, check=True)


def revision() -> str:
    text = (ROOT / "pom.xml").read_text(encoding="utf-8")
    match = re.search(r"<revision>([^<]+)</revision>", text)
    if not match:
        raise AssertionError("root pom.xml has no authoritative <revision>")
    return match.group(1)


def jar_paths(module: str) -> tuple[Path, Path]:
    name = f"{module}-{revision()}.jar"
    return ROOT / module / "target" / name, ROOT / module / "build" / "libs" / name


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def maven_graph(module: str, scope: str) -> set[str]:
    dependencies: set[str] = set()
    path = ROOT / module / "target" / f"parity-{scope}.txt"
    for line in path.read_text(encoding="utf-8").splitlines():
        coordinate = line.strip().split(" -- ", maxsplit=1)[0]
        parts = coordinate.split(":")
        if len(parts) == 5:
            group, artifact, _type, version, _scope = parts
        elif len(parts) == 6:
            group, artifact, _type, _classifier, version, _scope = parts
        else:
            continue
        dependencies.add(f"{group}:{artifact}:{version}")
    return dependencies


def gradle_graph(module: str, scope: str) -> set[str]:
    path = ROOT / module / "build" / "parity" / f"{scope}-dependencies.txt"
    return set(path.read_text(encoding="utf-8").splitlines())


def dependency_diff(label: str, left: set[str], right: set[str]) -> list[str]:
    errors: list[str] = []
    if left != right:
        errors.append(f"{label}: dependency graph drift")
        errors.extend(f"  Maven only: {item}" for item in sorted(left - right))
        errors.extend(f"  Gradle only: {item}" for item in sorted(right - left))
    return errors


def maven_contract(module: str) -> set[tuple[str, str, str]]:
    root = ET.parse(ROOT / module / "pom.xml").getroot()
    result: set[tuple[str, str, str]] = set()
    dependencies = root.find("m:dependencies", NAMESPACE)
    if dependencies is None:
        return result
    for dependency in dependencies.findall("m:dependency", NAMESPACE):
        scope = dependency.findtext("m:scope", default="compile", namespaces=NAMESPACE)
        optional = (
            dependency.findtext("m:optional", default="false", namespaces=NAMESPACE)
            == "true"
        )
        if scope == "test":
            continue
        group = dependency.findtext("m:groupId", namespaces=NAMESPACE)
        artifact = dependency.findtext("m:artifactId", namespaces=NAMESPACE)
        configuration = "compileOnly" if optional or scope == "provided" else "api"
        result.add((group, artifact, configuration))
    return result


def gradle_contract(module: str) -> set[tuple[str, str, str]]:
    text = (ROOT / module / "build.gradle.kts").read_text(encoding="utf-8")
    result: set[tuple[str, str, str]] = set()
    external = re.compile(
        r'^\s*(api|implementation|compileOnly)\("([^"]+)"\)', re.MULTILINE
    )
    projects = re.compile(
        r'^\s*(api|implementation|compileOnly)\(project\(":(.+)"\)\)', re.MULTILINE
    )
    for configuration, coordinate in external.findall(text):
        group, artifact, *_ = coordinate.split(":")
        result.add((group, artifact, configuration))
    for configuration, artifact in projects.findall(text):
        result.add(("de.subhransu", artifact, configuration))
    return result


def exclude_modules(graph: set[str], modules: set[str] | frozenset[str]) -> set[str]:
    return {
        coordinate
        for coordinate in graph
        if ":".join(coordinate.split(":", maxsplit=2)[:2]) not in modules
    }


def test_graph(module: str, build: str) -> set[str]:
    """Return comparable test runtime dependencies across the two build tools."""
    if build == "maven":
        # Maven's test scope also lists provided dependencies, which are compile-only in
        # Gradle. JUnit's POM also exposes API Guardian at runtime while its Gradle module
        # metadata correctly keeps that annotation-only library off the runtime variant.
        compile_only = {
            f"{group}:{artifact}"
            for group, artifact, configuration in maven_contract(module)
            if configuration == "compileOnly"
        }
        return exclude_modules(
            maven_graph(module, "test"),
            compile_only | MAVEN_TEST_GRAPH_EXCLUSIONS,
        )
    if build == "gradle":
        # Gradle needs an explicit launcher; Maven Surefire supplies its own launcher.
        return exclude_modules(
            gradle_graph(module, "test"), GRADLE_TEST_GRAPH_EXCLUSIONS
        )
    raise ValueError(f"Unknown build tool: {build}")


def generated_pom_contract(module: str) -> set[tuple[str, str]]:
    path = ROOT / module / "build" / "publications" / "mavenJava" / "pom-default.xml"
    root = ET.parse(path).getroot()
    dependencies = root.find("m:dependencies", NAMESPACE)
    if dependencies is None:
        return set()
    return {
        (
            dependency.findtext("m:groupId", namespaces=NAMESPACE),
            dependency.findtext("m:artifactId", namespaces=NAMESPACE),
        )
        for dependency in dependencies.findall("m:dependency", NAMESPACE)
    }


def publication_errors(module: str) -> list[str]:
    errors: list[str] = []
    maven_consumer = {
        (group, artifact)
        for group, artifact, configuration in maven_contract(module)
        if configuration != "compileOnly"
    }
    gradle_pom = generated_pom_contract(module)
    if maven_consumer != gradle_pom:
        errors.append(f"{module}: published consumer metadata drift")
        errors.extend(
            f"  Maven only: {group}:{artifact}"
            for group, artifact in sorted(maven_consumer - gradle_pom)
        )
        errors.extend(
            f"  Gradle only: {group}:{artifact}"
            for group, artifact in sorted(gradle_pom - maven_consumer)
        )

    module_metadata = (
        ROOT / module / "build" / "publications" / "mavenJava" / "module.json"
    )
    data = json.loads(module_metadata.read_text(encoding="utf-8"))
    component = data["component"]
    if (
        component["group"] != "de.subhransu"
        or component["module"] != module
        or component["version"] != revision()
    ):
        errors.append(f"{module}: Gradle module metadata coordinates drift")
    return errors


def archive_errors(module: str) -> tuple[list[str], str]:
    maven_jar, gradle_jar = jar_paths(module)
    errors: list[str] = []
    with (
        zipfile.ZipFile(maven_jar) as maven_zip,
        zipfile.ZipFile(gradle_jar) as gradle_zip,
    ):
        maven_entries = maven_zip.namelist()
        gradle_entries = gradle_zip.namelist()
        if maven_entries != gradle_entries:
            errors.append(f"{module}: archive entry/order drift")
            errors.extend(
                f"  Maven only: {entry}"
                for entry in sorted(set(maven_entries) - set(gradle_entries))
            )
            errors.extend(
                f"  Gradle only: {entry}"
                for entry in sorted(set(gradle_entries) - set(maven_entries))
            )
        for entry in sorted(set(maven_entries) & set(gradle_entries)):
            if maven_zip.read(entry) != gradle_zip.read(entry):
                kind = (
                    "public API/ABI class" if entry.endswith(".class") else "resource"
                )
                errors.append(f"{module}: {kind} bytes drift at {entry}")
    maven_hash = sha256(maven_jar)
    gradle_hash = sha256(gradle_jar)
    if maven_hash != gradle_hash:
        errors.append(
            f"{module}: JAR SHA-256 drift (Maven {maven_hash}, Gradle {gradle_hash})"
        )
    return errors, maven_hash


def build_hash_errors(
    first: BuildHashes, second: BuildHashes | None = None
) -> list[str]:
    errors: list[str] = []
    runs = (("run 1", first), ("run 2", second))
    for run_label, hashes in runs:
        if hashes is None:
            continue
        for module in MODULES:
            maven_hash = hashes.maven[module]
            gradle_hash = hashes.gradle[module]
            if maven_hash != gradle_hash:
                errors.append(
                    f"{module}: {run_label} cross-tool JAR SHA-256 drift "
                    f"(Maven {maven_hash}, Gradle {gradle_hash})"
                )

    if second is None:
        return errors

    for module in MODULES:
        if first.maven[module] != second.maven[module]:
            errors.append(
                f"{module}: Maven reproducibility drift "
                f"(run 1 {first.maven[module]}, run 2 {second.maven[module]})"
            )
        if first.gradle[module] != second.gradle[module]:
            errors.append(
                f"{module}: Gradle reproducibility drift "
                f"(run 1 {first.gradle[module]}, run 2 {second.gradle[module]})"
            )
    return errors


def build_once() -> BuildHashes:
    module_list = ",".join(MODULES)
    run(
        [
            "mvn",
            "-B",
            "-DskipTests",
            "-pl",
            module_list,
            "-am",
            "clean",
            "package",
            "org.apache.maven.plugins:maven-dependency-plugin:list",
            "-DincludeScope=runtime",
            "-DoutputFile=target/parity-runtime.txt",
        ]
    )
    run(
        [
            "mvn",
            "-B",
            "-DskipTests",
            "-pl",
            module_list,
            "-am",
            "package",
            "org.apache.maven.plugins:maven-dependency-plugin:list",
            "-DincludeScope=test",
            "-DoutputFile=target/parity-test.txt",
        ]
    )
    maven_hashes = {module: sha256(jar_paths(module)[0]) for module in MODULES}
    gradle_tasks = [
        "clean",
        *(f":{module}:jar" for module in MODULES),
        "writeParityRuntimeGraph",
        "writeParityTestGraph",
        "generatePomFileForMavenJavaPublication",
        "generateMetadataFileForMavenJavaPublication",
    ]
    run(["gradle", "--no-daemon", *gradle_tasks])
    gradle_hashes = {module: sha256(jar_paths(module)[1]) for module in MODULES}
    return BuildHashes(maven=maven_hashes, gradle=gradle_hashes)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--skip-build",
        action="store_true",
        help="compare the current target/build outputs",
    )
    parser.add_argument(
        "--single-build",
        action="store_true",
        help="skip the second reproducibility build",
    )
    args = parser.parse_args()

    errors: list[str] = []
    if not args.skip_build:
        first_hashes = build_once()
        second_hashes = None
        if not args.single_build:
            second_hashes = build_once()
        errors.extend(build_hash_errors(first_hashes, second_hashes))

    for module in MODULES:
        errors.extend(
            dependency_diff(
                f"{module} runtime",
                maven_graph(module, "runtime"),
                gradle_graph(module, "runtime"),
            )
        )
        errors.extend(
            dependency_diff(
                f"{module} test runtime",
                test_graph(module, "maven"),
                test_graph(module, "gradle"),
            )
        )
        if maven_contract(module) != gradle_contract(module):
            errors.append(f"{module}: direct dependency scope/configuration drift")
            errors.extend(
                f"  Maven only: {item}"
                for item in sorted(maven_contract(module) - gradle_contract(module))
            )
            errors.extend(
                f"  Gradle only: {item}"
                for item in sorted(gradle_contract(module) - maven_contract(module))
            )
        errors.extend(publication_errors(module))
        archive_drift, artifact_hash = archive_errors(module)
        errors.extend(archive_drift)
        if not archive_drift:
            entries = len(zipfile.ZipFile(jar_paths(module)[0]).infolist())
            print(
                f"PASS {module}: {len(maven_graph(module, 'runtime'))} runtime and "
                f"{len(test_graph(module, 'maven'))} test runtime dependencies, "
                f"{entries} entries, API/ABI/resources identical, SHA-256 {artifact_hash}"
            )

    if errors:
        print("\nBuild parity FAILED:", file=sys.stderr)
        print("\n".join(errors), file=sys.stderr)
        return 1

    print(
        "\nBuild parity PASSED for the three published thin libraries. "
        "The executable openrouter-spring-ai-samples module is intentionally excluded."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
