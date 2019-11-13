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
package com.github.tranchis.xsd2thrift.marshal;

import com.github.tranchis.xsd2thrift.NamespaceConverter;
import com.google.common.base.CaseFormat;
import com.sun.xml.xsom.XSFacet;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ProtobufMarshaller {
	private HashMap<Pattern, String> typeMapping;
	private HashMap<Pattern, String> nameMapping;
	private String indent = "";
	public HashMap<String, String> imports;
	private Map<String, Object> options;

	public ProtobufMarshaller() {
		typeMapping = new HashMap<>();
		typeMapping.put(Pattern.compile("^positiveInteger$"), "int64");
		typeMapping.put(Pattern.compile("^nonPositiveInteger$"), "sint64");
		typeMapping.put(Pattern.compile("^negativeInteger$"), "sint64");
		typeMapping.put(Pattern.compile("^nonNegativeInteger$"), "int64");
		typeMapping.put(Pattern.compile("^int$"), "int32");
		typeMapping.put(Pattern.compile("^integer$"), "int64");

		typeMapping.put(Pattern.compile("^unsignedLong$"), "uint64");
		typeMapping.put(Pattern.compile("^unsignedInt$"), "uint32");
		typeMapping.put(Pattern.compile("^unsignedShort$"), "uint32"); // No 16-bit int in protobuf
		typeMapping.put(Pattern.compile("^unsignedByte$"), "uint32"); // No 8-bit int in protobuf

		typeMapping.put(Pattern.compile("^short$"), "int32"); // No 16-bit int in protobuf
		typeMapping.put(Pattern.compile("^long$"), "int64");
		typeMapping.put(Pattern.compile("^decimal$"), "double");
		typeMapping.put(Pattern.compile("^ID$"), "string");
		typeMapping.put(Pattern.compile("^Name$"), "string");
		typeMapping.put(Pattern.compile("^IDREF$"), "string");
		typeMapping.put(Pattern.compile("^NMTOKEN$"), "string");
		typeMapping.put(Pattern.compile("^NMTOKENS$"), "string"); // TODO: Fix this
		typeMapping.put(Pattern.compile("^anySimpleType$"), "string");
		typeMapping.put(Pattern.compile("^anyType$"), "string");
		typeMapping.put(Pattern.compile("^anyURI$"), "string");
		typeMapping.put(Pattern.compile("^normalizedString$"), "string");
		typeMapping.put(Pattern.compile("^boolean$"), "bool");
		typeMapping.put(Pattern.compile("^binary$"), "bytes"); // UnspecifiedType.object is
		// declared binary
		typeMapping.put(Pattern.compile("^hexBinary$"), "bytes");
		typeMapping.put(Pattern.compile("^base64Binary$"), "bytes");
		typeMapping.put(Pattern.compile("^byte$"), "bytes");
		typeMapping.put(Pattern.compile("^date$"), "int32"); // Number of days since January 1st),
		// 1970
		typeMapping.put(Pattern.compile("^dateTime$"), "google.protobuf.Timestamp"); // Number of milliseconds since
		// January 1st), 1970

		typeMapping.put(Pattern.compile("^time$"), "google.protobuf.Timestamp");
		typeMapping.put(Pattern.compile("^duration$"), "google.protobuf.Duration");

		nameMapping = new HashMap<>();

		imports = new HashMap<String, String>();
		imports.put("google.protobuf.Timestamp", "google/protobuf/timestamp");
		imports.put("google.protobuf.Duration", "google/protobuf/duration");
	}

	public String writeHeader(String namespace) {

		StringBuilder b = new StringBuilder();

		// Syntax
		b.append("syntax = \"proto3\";\n\n");

		if (namespace != null) {
			b.append("package ");
			b.append(escapeNamespace(namespace));
			b.append(";\n\n");
		}

		if (options != null) {
			for (String s : options.keySet()) {

				b.append("option ").append(s).append(" = ");

				Object value = options.get(s);

				if (value instanceof String) {
					b.append("\"").append(value).append("\"");
				} else {
					b.append(value);
				}

				b.append(";\n");
			}
		}

		b.append("import \"validate/validate.proto\";\n\n");

		return b.toString();
	}

	public String escapeNamespace(String namespace) {
		if (namespace == null) {
			return null;
		}
		return namespace.replaceAll("\\.([0-9])", "_$1");
	}

	public String writeEnumHeader(String name) {
		final String result = writeIndent() + "enum " + name + "\n" + writeIndent() + "{\n";
		increaseIndent();
		return result;
	}

	public String writeEnumValue(int order, String value) {
		return (writeIndent() + CaseFormat.UPPER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, value) + " = " + order + ";\n");
	}

	public String writeEnumFooter() {
		decreaseIndent();
		return writeIndent() + "}\n";
	}

	public String writeStructHeader(String name) {
		final String result = writeIndent() + "message " + name + "\n" + writeIndent() + "{\n";
		increaseIndent();
		return result;
	}

	public String writeStructParameter(int order, boolean required, boolean repeated, String name, String type, String fieldDocumentation,
									   boolean splitByNamespace, List<XSFacet> facets) {
		String sRequired = "";

		if (fieldDocumentation != null) {
			fieldDocumentation = fieldDocumentation.replaceAll("[\n\r\t\\s]+", " ").trim();
		}


		if (repeated) {
			sRequired = "repeated ";
		}
		if (StringUtils.isAllUpperCase(name)) {
			name = StringUtils.lowerCase(name);
		}

		String fieldName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, name);
		fieldName = fieldName.replaceAll("_i_d", "_id");
		fieldName = fieldName.replaceAll("_m_s", "_ms");


		String convertedType = NamespaceConverter.convertFromSchema(type);

		if (imports.containsKey(type)) {
			convertedType = type;
		} else if (!splitByNamespace) {
			convertedType = convertedType.substring(convertedType.lastIndexOf(".") + 1);
		}

		String validation =  "";
		if (facets  != null && facets.size() > 0) {
			LinkedHashMap<String,XSFacet> consolidated = new LinkedHashMap<>();
			for (XSFacet facet : facets) {
				XSFacet previous = consolidated.get(facet.getName());
				if (previous != null) {
					switch (facet.getName()) {
						case "maxLength":
							if (Integer.valueOf(facet.getValue().value) < Integer.valueOf(previous.getValue().value)) {
								consolidated.put(facet.getName(), facet);
							}
							break;
						case "minLength":
							if (Integer.valueOf(facet.getValue().value) > Integer.valueOf(previous.getValue().value)) {
								consolidated.put(facet.getName(), facet);
							}
							break;
					}
				} else {
					consolidated.put(facet.getName(), facet);
				}
			}

			validation = " [(validate.rules)." + ((repeated) ? "repeated.items." : "") + type + " = {";
			validation += consolidated.values().stream()
					.map(f -> {
						switch (f.getName()) {
							case "length":
								return "len: " + f.getValue();
							case "pattern":
								return "pattern: \"" + f.getValue().toString().replaceAll("\\\\","\\\\\\\\") + "\"";
							case "maxLength":
								return "max_len: " + f.getValue();
							case "minLength":
								return "min_len: " + f.getValue();
							case "totalDigits":
								int digits = Integer.parseInt(f.getValue().value);
								switch (type) {
									case "int64":
										return "gte: -" + StringUtils.repeat('9', digits) + ",lte: " + StringUtils.repeat('9', digits);
									default:
										throw new UnsupportedOperationException("not yet supported " + f + " on " + type);
								}
							case "minInclusive":
								switch (type) {
									case "int64":
										int value = Integer.parseInt(f.getValue().value);
										return "gte: " + value;
									default:
										throw new UnsupportedOperationException("not yet supported " + f + " on " + type);
								}
							case "maxInclusive":
								switch (type) {
									case "int64":
										int value = Integer.parseInt(f.getValue().value);
										return "lte: " + value;
									default:
										throw new UnsupportedOperationException("not yet supported " + f + " on " + type);
								}
							default:
								throw new UnsupportedOperationException("not yet supported" + f);
						}

					}).collect(Collectors.joining(", "));
			validation += "}]";
		}

		if (StringUtils.isNotBlank(validation)) {
			validation = "\t\t" + validation;
		}

		return writeIndent() + sRequired + convertedType + " " + fieldName + " = " + order + validation + ";"
				+ (StringUtils.isNotBlank(fieldDocumentation) ? " // " + fieldDocumentation : "") + "\n";
	}

	public String writeStructFooter() {
		decreaseIndent();
		return writeIndent() + "}\n";
	}

	public String getTypeMapping(String type) {
		for (Pattern p : typeMapping.keySet()) {
			Matcher m = p.matcher(type);
			if (m.matches()) {
				return m.replaceAll(typeMapping.get(p));
			}
		}

		return null;
	}

	public String getNameMapping(String type) {
		for (Pattern p : nameMapping.keySet()) {
			Matcher m = p.matcher(type);
			if (m.matches()) {
				return m.replaceAll(nameMapping.get(p));
			}
		}

		return null;
	}

	public boolean isNestedEnums() {
		return true;
	}

	public boolean isCircularDependencySupported() {
		return true;
	}

	private void increaseIndent() {
		indent += "  ";
	}

	private void decreaseIndent() {
		indent = indent.substring(0, indent.length() > 0 ? indent.length() - 2 : 0);
	}

	private String writeIndent() {
		return indent;
	}

	public String writeInclude(String namespace) {
		String res;

		if (namespace != null && !namespace.isEmpty()) {
			res = "import \"" + namespace + ".proto\";\n";
		} else {
			res = "";
		}

		return res;
	}

	public void setCustomTypeMappings(Map<Pattern, String> customTypeMappings) {
		if (customTypeMappings != null) {
			for (Entry<Pattern, String> entry : customTypeMappings.entrySet()) {
				typeMapping.put(entry.getKey(), entry.getValue());
			}
		}
	}

	public void setCustomNameMappings(Map<Pattern, String> customNameMappings) {
		if (customNameMappings != null) {
			for (Entry<Pattern, String> entry : customNameMappings.entrySet()) {
				nameMapping.put(entry.getKey(), entry.getValue());
			}
		}
	}

	public String getImport(String fullTypeName) {
		if (imports != null) {
			return imports.get(fullTypeName);
		}
		return null;
	}

	public void setOptions(Map<String, Object> options) {
		this.options = options;
	}

	public void setCustomImports(Map<String, String> customImports) {
		this.imports.putAll(customImports);
	}
}
