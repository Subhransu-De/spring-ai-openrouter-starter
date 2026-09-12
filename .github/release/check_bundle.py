"""Check the actual publication files before allowing an upload."""

import argparse
import hashlib
from pathlib import Path, PurePosixPath
import xml.etree.ElementTree as ET
from zipfile import ZipFile


def check(bundle, version, destination, signed):
    modules = {
        "openrouter-spring-ai-parent": "pom",
        "openrouter-spring-ai": "jar",
        "openrouter-spring-ai-autoconfigure": "jar",
        "openrouter-spring-ai-starter": "jar",
    }
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    with ZipFile(bundle) as archive:
        names = [entry.filename for entry in archive.infolist() if not entry.is_dir()]
        assert len(names) == len(set(names)), "Duplicate bundle entries"
        expected = set()
        for module, packaging in modules.items():
            base = f"de/subhransu/{module}/{version}/{module}-{version}"
            artifacts = [base + ".pom"]
            if packaging == "jar":
                artifacts += [
                    base + suffix for suffix in (".jar", "-sources.jar", "-javadoc.jar")
                ]
            for artifact in artifacts:
                expected.add(artifact)
                data = archive.read(artifact)
                assert data, f"Empty artifact: {artifact}"
                if signed:
                    signature = artifact + ".asc"
                    assert b"BEGIN PGP SIGNATURE" in archive.read(signature)
                    expected.add(signature)
                for candidate in [artifact] + ([artifact + ".asc"] if signed else []):
                    for algorithm in ("md5", "sha1", "sha256", "sha512"):
                        checksum = candidate + "." + algorithm
                        if checksum in names:
                            digest = hashlib.new(
                                algorithm, archive.read(candidate)
                            ).hexdigest()
                            assert archive.read(checksum).decode().strip() == digest
                            expected.add(checksum)
                    assert candidate + ".sha1" in names, (
                        f"Missing checksum: {candidate}"
                    )
            pom = ET.fromstring(archive.read(base + ".pom"))
            assert pom.findtext("m:artifactId", namespaces=ns) == module
            parent = pom.find("m:parent", ns)
            identity = pom if parent is None else parent
            assert identity.findtext("m:groupId", namespaces=ns) == "de.subhransu"
            assert identity.findtext("m:version", namespaces=ns) == version
            for node in pom.findall(".//m:version", ns):
                assert "SNAPSHOT" not in (node.text or "")
                assert "${revision}" not in (node.text or "")
            if packaging == "pom":
                for field in (
                    "name",
                    "description",
                    "url",
                    "licenses/license/name",
                    "developers/developer/name",
                    "scm/url",
                ):
                    path = "/".join("m:" + part for part in field.split("/"))
                    assert pom.findtext(path, namespaces=ns), (
                        f"Missing metadata: {field}"
                    )
        assert set(names) == expected, (
            "Unexpected files in bundle (including samples or missing signatures)"
        )
        destination = Path(destination).resolve()
        assert not destination.exists(), "Use a fresh consumer repository directory"
        for name in names:
            path = PurePosixPath(name)
            assert not path.is_absolute() and ".." not in path.parts
        archive.extractall(destination)
    print(
        "Bundle contains only the parent and three library modules; publication checks passed."
    )


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bundle")
    parser.add_argument("version")
    parser.add_argument("destination")
    parser.add_argument("--signed", choices=("true", "false"), default="true")
    args = parser.parse_args()
    check(args.bundle, args.version, args.destination, args.signed == "true")
