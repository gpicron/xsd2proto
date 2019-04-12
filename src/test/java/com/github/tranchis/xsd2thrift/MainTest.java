package com.github.tranchis.xsd2thrift;

import static com.github.tranchis.xsd2thrift.TestHelper.compareExpectedAndGenerated;
import static com.github.tranchis.xsd2thrift.TestHelper.generateProtobuf;

import org.junit.Ignore;
import org.junit.Test;

public class MainTest {

	
	@Test
	public void compareAtomProtobuf() {
		compareExpectedAndGenerated("src/test/resources/expectedproto/atom.proto",
				generateProtobuf("atom"));
	}

	@Ignore
	public void compareRecipeProtobuf() {
		compareExpectedAndGenerated("src/test/resources/expectedproto/recipe.proto",
				generateProtobuf("recipe"));
	}

	@Test
	public void compareShiporderProtobuf() {
		compareExpectedAndGenerated("src/test/resources/expectedproto/shiporder.proto",
				generateProtobuf("shiporder"));
	}

	@Test
	public void compareTestChoiceProtobuf() {
		compareExpectedAndGenerated("src/test/resources/expectedproto/test-choice.proto",
				generateProtobuf("test-choice"));
	}

	@Test
	public void compareTestDatatypesProtobuf() {
		compareExpectedAndGenerated(
				"src/test/resources/expectedproto/test-datatypes.proto",
				generateProtobuf("test-datatypes"));
	}

	@Test
	public void compareTestDatatypesStringDatesProtobuf() {
		compareExpectedAndGenerated(
				"src/test/resources/expectedproto/test-datatypes-string-dates.proto",
				generateProtobuf("test-datatypes-string-dates",
						"date:string,dateTime:string",null));
	}

	@Test
	public void compareTestExtensionProtobuf() {
		compareExpectedAndGenerated(
				"src/test/resources/expectedproto/test-extension.proto",
				generateProtobuf("test-extension"));
	}

	@Test
	public void compareTestExtensionAttributesProtobuf() {
		compareExpectedAndGenerated(
				"src/test/resources/expectedproto/test-extension-attributes.proto",
				generateProtobuf("test-extension-attributes"));
	}

	@Test
	public void compareTestOptionalProtobuf() {
		compareExpectedAndGenerated(
				"src/test/resources/expectedproto/test-optional.proto",
				generateProtobuf("test-optional"));
	}

	@Test
	public void compareTestRangeProtobuf() {
		compareExpectedAndGenerated("src/test/resources/expectedproto/test-range.proto",
				generateProtobuf("test-range"));
	}

	@Ignore("Order seems to change on the test run")
	public void compareXmlRecipemlProtobuf() {
		compareExpectedAndGenerated(
				"src/test/resources/expectedproto/xml-recipeml.proto",
				generateProtobuf("xml-recipeml"));
	}

	@Ignore("Order seems to change on the test run")
	public void compareRecipemlProtobuf() {
		compareExpectedAndGenerated("src/test/resources/expectedproto/recipeml.proto",
				generateProtobuf("recipeml"));
	}

	@Test
	@Ignore("Implementation not yet complete")
	public void fieldAndMessageRenaming() {
		compareExpectedAndGenerated(
				"src/test/resources/expectedproto/complexTypeRenaming.proto",
				generateProtobuf("complexTypeRenaming",null,
						"ElementListOriginalName:ElementListNewName,ElementInListOfComplexTypeOriginalName:ElementInListOfComplexTypeNewName,ComplexTypeOriginalName:ComplexTypeNewName,ElementInListOriginalName:ElementInListNewName"));
	}

}
