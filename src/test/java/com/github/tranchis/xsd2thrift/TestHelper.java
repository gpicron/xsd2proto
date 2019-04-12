package com.github.tranchis.xsd2thrift;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;

public class TestHelper {

	public static void compareExpectedAndGenerated(String expected, String generated) {
		try {
			List<String> exlines = linesFromFile(expected);
			List<String> genlines = linesFromFile(generated);

			for (int i = 0; i < exlines.size(); i++) {
				String exline = exlines.get(i);
				String genline = null;
				if (genlines.size() > i)
					genline = genlines.get(i);
				Assert.assertEquals("Unexpected difference between " + expected + " and " + generated + " on line " + i, exline, genline);
			}
		} catch (Exception e) {
			Assert.fail("Failure while comparing " + expected + " and " + generated);
			e.printStackTrace();
		}
	}

	public static String generateProtobuf(String name, String typeMappings, String nameMappings) {
		return generate(name, "protobuf", "proto", typeMappings, nameMappings);
	}

	public static String generateProtobuf(String name) {
		return generate(name, "protobuf", "proto", "a:a", null);
	}

	private static String generate(String name, String type, String extension, String typeMappings, String nameMappings) {
		File dir = new File("target/generated-proto/");
		if (!dir.exists())
			dir.mkdir();
		String filename = "target/generated-proto/" + name + "." + extension;
		
		List<String> args = new ArrayList<>();
		args.add( "--filename=" + filename);
		args.add("--package=default");
		if(typeMappings != null) {
			args.add("--customTypeMappings=" + typeMappings);
		}
		if(nameMappings != null) {
			args.add("--customNameMappings=" + nameMappings);
		}
		args.add("src/test/resources/xsd/" + name + ".xsd");
		
		Main.main(args.toArray(new String[0]));

		return filename;
	}

	private static List<String> linesFromFile(String file) throws IOException {
		List<String> lines = new ArrayList<String>();
		BufferedReader reader = new BufferedReader(new FileReader(file));
		String line = null;
		while ((line = reader.readLine()) != null) {
			lines.add(line);
		}
		reader.close();
		return lines;
	}

}
