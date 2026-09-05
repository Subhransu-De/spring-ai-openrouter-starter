import unittest

from scripts.check_build_parity import BuildHashes, MODULES, build_hash_errors


def hashes(value: str) -> dict[str, str]:
    return dict.fromkeys(MODULES, value)


class BuildHashErrorsTests(unittest.TestCase):
    def test_reports_gradle_only_nondeterminism_even_when_second_run_matches_maven(
        self,
    ) -> None:
        first = BuildHashes(maven=hashes("stable"), gradle=hashes("gradle-run-one"))
        second = BuildHashes(maven=hashes("stable"), gradle=hashes("stable"))

        errors = build_hash_errors(first, second)

        for module in MODULES:
            self.assertIn(
                f"{module}: run 1 cross-tool JAR SHA-256 drift "
                "(Maven stable, Gradle gradle-run-one)",
                errors,
            )
            self.assertIn(
                f"{module}: Gradle reproducibility drift "
                "(run 1 gradle-run-one, run 2 stable)",
                errors,
            )
            self.assertFalse(
                any(
                    error.startswith(f"{module}: Maven reproducibility drift")
                    for error in errors
                )
            )


if __name__ == "__main__":
    unittest.main()
