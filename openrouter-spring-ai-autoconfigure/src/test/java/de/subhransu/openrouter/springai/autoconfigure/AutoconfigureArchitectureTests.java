package de.subhransu.openrouter.springai.autoconfigure;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "de.subhransu.openrouter.springai.autoconfigure",
		importOptions = ImportOption.DoNotIncludeTests.class)
class AutoconfigureArchitectureTests {

	private AutoconfigureArchitectureTests() {
	}

	@ArchTest
	static final ArchRule auto_configuration_must_not_depend_on_wire_dtos = noClasses().that()
		.resideInAPackage("..autoconfigure..")
		.should()
		.dependOnClassesThat()
		.resideInAPackage("..api.dto..");

	@ArchTest
	static final ArchRule auto_configuration_must_not_depend_on_mappers = noClasses().that()
		.resideInAPackage("..autoconfigure..")
		.should()
		.dependOnClassesThat()
		.resideInAPackage("..chat.mapper..");

}
