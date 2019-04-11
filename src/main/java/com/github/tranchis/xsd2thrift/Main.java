/*
 * ============================================================================
 * GNU Lesser General Public License
 * ============================================================================
 *
 * XSD2Thrift
 * 
 * Copyright (C) 2009 Sergio Alvarez-Napagao http://www.sergio-alvarez.com
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307, USA.
 */
package com.github.tranchis.xsd2thrift;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import com.github.tranchis.xsd2thrift.marshal.IMarshaller;
import com.github.tranchis.xsd2thrift.marshal.ProtobufMarshaller;
import org.yaml.snakeyaml.Yaml;

public class Main {
	private static boolean correct;
	private static String usage = ""
			+ "Usage: java xsd2proto.jar [--output=FILENAME]\n"
			+ "                           [--package=NAME] filename.xsd\n"
			+ "\n"
			+ "  --configFile=FILENAME           : path to configuration file\n"
			+ "\nOR\n"
			+ "\n"
			+ "  --filename=FILENAME             : store the result in FILENAME instead of standard output\n"
			+ "  --package=NAME                  : set namespace/package of the output file\n"
			+ "  --nestEnums=true|false          : nest enum declaration within messages that reference them, only supported by protobuf, defaults to true\n"
			+ "  --splitBySchema=true|false      : split output into namespace-specific files, defaults to false\n"
			+ "  --customMappings=a:b,x:y        : represent schema types as specific output types\n"
			+ "  --protobufVersion=2|3           : if generating protobuf, choose the version (2 or 3)\n"
			+ "  --typeInEnums=true|false        : include type as a prefix in enums, defaults to true\n"
			+ "  --includeMessageDocs=true|false : include documentation of messages in output, defaults to true\n"
			+ "  --includeFieldDocs=true|false   : include documentation for fields in output, defaults to true\n"
			+ "";

	private static void usage(String error) {
		System.err.println(error);
		usage();
	}

	private static void usage() {
		System.err.print(usage);
		correct = false;
	}

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		XSDParser xp;
		TreeMap<String, String> map;
		String xsd, param;
		int i;
		int protobufVersion = 2;
		ProtobufMarshaller pbm = null;
		IMarshaller im;
		OutputWriter writer;
		correct = true;
		im = null;

		map = new TreeMap<String, String>();
		map.put("schema_._type", "binary");
		map.put("EString", "string");
		map.put("EBoolean", "boolean");
		map.put("EInt", "integer");
		map.put("EDate", "long");
		map.put("EChar", "byte");
		map.put("EFloat", "decimal");
		map.put("EObject", "binary");
		map.put("Extension", "binary");

		if (args.length == 0 || args[args.length - 1].startsWith("--")) {
			usage();
		} else {
			xsd = args[args.length - 1];
			xp = new XSDParser(xsd, map);
			writer = new OutputWriter();
			xp.setWriter(writer);


			pbm = new ProtobufMarshaller();
			im = pbm;
			xp.addMarshaller(im);
			writer.setMarshaller(im);
			writer.setDefaultExtension("proto");

			Map<String, String> customMappings = null;

			if (args.length == 2 && args[0].startsWith("--configFile=")) {
				Yaml yaml = new Yaml();
				try (InputStream in = Files.newInputStream(Paths.get(args[0].split("=")[1]))) {
					ConfigFile config = yaml.loadAs(in, ConfigFile.class);

					pbm.setProtobufVersion(config.protobufVersion);

					writer.setFilename(config.filename);
					writer.setDirectory(config.directory);
					writer.setDefaultNamespace(config.namespace);
					writer.setSplitBySchema(config.splitBySchema);

					customMappings = new HashMap<>();
					customMappings.putAll(config.customMappingsMap);

					xp.setNestEnums(config.nestEnums);
					xp.setEnumOrderStart(0);
					xp.setTypeInEnums(config.typeInEnums);
					xp.setIncludeMessageDocs(config.includeMessageDocs);
					xp.setIncludeFieldDocs(config.includeFieldDocs);
				}
			} else {
				i = 0;
				while (correct && i < args.length - 1) {
					if (args[i].startsWith("--filename=")) {
						param = args[i].split("=")[1];
						writer.setFilename(param);
					} else if (args[i].startsWith("--directory=")) {
						param = args[i].split("=")[1];
						writer.setDirectory(param);
					} else if (args[i].startsWith("--package=")) {
						param = args[i].split("=")[1];
						writer.setDefaultNamespace(param);
					} else if (args[i].startsWith("--splitBySchema=")) {
						param = args[i].split("=")[1];
						writer.setSplitBySchema("true".equals(param));
					} else if (args[i].startsWith("--customMappings=")) {
						param = args[i].split("=")[1];
						customMappings = new HashMap<String, String>();
						for (String mapping : param.split(",")) {
							int colon = mapping.indexOf(':');
							if (colon > -1) {
								customMappings.put(mapping.substring(0, colon),
										mapping.substring(colon + 1));
							} else {
								usage(mapping
										+ " is not a valid custom mapping - use schematype:outputtype");
							}
						}
					} else if (args[i].startsWith("--nestEnums=")) {
						param = args[i].split("=")[1];
						boolean nestEnums = Boolean.valueOf(param);
						xp.setNestEnums(nestEnums);
					} else if (args[i].startsWith("--protobufVersion=")) {
						protobufVersion = Integer.parseInt(args[i].split("=")[1]);
						xp.setEnumOrderStart(0);
						pbm.setProtobufVersion(protobufVersion);
						
					} else if (args[i].startsWith("--typeInEnums=")) {
						xp.setTypeInEnums(Boolean.parseBoolean(args[i].split("=")[1]));
					} else if (args[i].startsWith("--includeMessageDocs=")) {
						xp.setIncludeMessageDocs(Boolean.parseBoolean(args[i].split("=")[1]));
					} else if (args[i].startsWith("--includeFieldDocs=")) {
						xp.setIncludeFieldDocs(Boolean.parseBoolean(args[i].split("=")[1]));
					} else {
						usage();
					}

					i = i + 1;
				}
			}

			
			 if (customMappings != null) {
				im.setCustomMappings(customMappings);
			}

			if (correct) {
				xp.parse();
			}
		}
	}
}
