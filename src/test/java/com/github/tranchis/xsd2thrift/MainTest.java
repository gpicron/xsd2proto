package com.github.tranchis.xsd2thrift;

import static com.github.tranchis.xsd2thrift.TestHelper.*;

import java.io.File;

import org.junit.Ignore;
import org.junit.Test;

public class MainTest {

	public static void main(String args[]) {
		// regenerateTestData("expected");
		try {
			Main.main(new String[] { "--protobuf",
					"--filename=src/test/data/expected/recipe.proto",
					"--package=default", "contrib/recipeml.xsd" });
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void regenerateTestData(String destination) {

		File directory = new File("contrib");
		for (String name : directory.list()) {
			if (!name.endsWith(".xsd"))
				continue;
			try {
				Main.main(new String[] {
						"--protobuf",
						"--filename=src/test/data/" + destination + "/"
								+ name.replace(".xsd", ".proto"),
						"--package=default", "contrib/" + name });
			} catch (Exception e) {
				System.out.println("Failed on " + name);
				e.printStackTrace();
			}
		}
	}

	@Test
	public void compareAtomProtobuf() {
		compareExpectedAndGenerated("src/test/data/expected/atom.proto",
				generateProtobuf("atom"));
	}

	@Ignore
	public void compareRecipeProtobuf() {
		compareExpectedAndGenerated("src/test/data/expected/recipe.proto",
				generateProtobuf("recipe"));
	}

	@Test
	public void compareShiporderProtobuf() {
		compareExpectedAndGenerated("src/test/data/expected/shiporder.proto",
				generateProtobuf("shiporder"));
	}

	@Test
	public void compareTestChoiceProtobuf() {
		compareExpectedAndGenerated("src/test/data/expected/test-choice.proto",
				generateProtobuf("test-choice"));
	}

	@Test
	public void compareTestDatatypesProtobuf() {
		compareExpectedAndGenerated(
				"src/test/data/expected/test-datatypes.proto",
				generateProtobuf("test-datatypes"));
	}

	@Test
	@Ignore
	public void compareTestDatatypesStringDatesProtobuf() {
		compareExpectedAndGenerated(
				"src/test/data/expected/test-datatypes-string-dates.proto",
				generateProtobuf("test-datatypes-string-dates",
						"date:string,dateTime:string"));
	}

	@Test
	public void compareTestExtensionProtobuf() {
		compareExpectedAndGenerated(
				"src/test/data/expected/test-extension.proto",
				generateProtobuf("test-extension"));
	}

	@Test
	public void compareTestExtensionAttributesProtobuf() {
		compareExpectedAndGenerated(
				"src/test/data/expected/test-extension-attributes.proto",
				generateProtobuf("test-extension-attributes"));
	}

	@Test
	public void compareTestOptionalProtobuf() {
		compareExpectedAndGenerated(
				"src/test/data/expected/test-optional.proto",
				generateProtobuf("test-optional"));
	}

	@Test
	public void compareTestRangeProtobuf() {
		compareExpectedAndGenerated("src/test/data/expected/test-range.proto",
				generateProtobuf("test-range"));
	}

	@Ignore("Order seems to change on the test run")
	public void compareXmlRecipemlProtobuf() {
		compareExpectedAndGenerated(
				"src/test/data/expected/xml-recipeml.proto",
				generateProtobuf("xml-recipeml"));
	}

	@Ignore("Order seems to change on the test run")
	public void compareRecipemlProtobuf() {
		compareExpectedAndGenerated("src/test/data/expected/recipeml.proto",
				generateProtobuf("recipeml"));
	}

}
