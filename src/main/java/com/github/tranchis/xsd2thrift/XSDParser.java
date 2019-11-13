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

import com.github.tranchis.xsd2thrift.marshal.ProtobufMarshaller;
import com.sun.xml.xsom.*;
import com.sun.xml.xsom.impl.ComplexTypeImpl;
import com.sun.xml.xsom.parser.XSOMParser;
import com.sun.xml.xsom.util.DomAnnotationParserFactory;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

public class XSDParser implements ErrorHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(XSDParser.class);

	private File f;
	private LinkedHashMap<String, Message> map;
	private LinkedHashMap<String, Enumeration> enums;
	private LinkedHashMap<String, SimpleTypeDesc> simpleTypes;
	private Map<String, String> documentation;
	private Set<String> keywords, basicTypes;
	private HashMap<String, String> xsdMapping;
	private ProtobufMarshaller marshaller;
	private OutputWriter writer;
	private boolean nestEnums = true;
	private int enumOrderStart = 0;
	private boolean typeInEnums = true;
	private boolean includeMessageDocs = true;
	private boolean includeFieldDocs = true;

	public XSDParser(String stFile) {
		this.xsdMapping = new HashMap<String, String>();
		init(stFile);
	}

	private void init(String stFile) {

		this.f = new File(stFile);
		map = new LinkedHashMap<>();
		enums = new LinkedHashMap<>();
		simpleTypes = new LinkedHashMap<>();
		documentation = new HashMap<>();
		keywords = new TreeSet<>();
		keywords.add("interface");
		keywords.add("is");
		keywords.add("class");
		keywords.add("optional");
		keywords.add("yield");
		keywords.add("abstract");
		keywords.add("required");
		keywords.add("volatile");
		keywords.add("transient");
		keywords.add("service");
		keywords.add("else");
		keywords.add("descriptor");

		basicTypes = new TreeSet<String>();
		basicTypes.add("string");
		basicTypes.add("normalizedString");
		basicTypes.add("anyType");
		basicTypes.add("anyURI");
		basicTypes.add("anySimpleType");

		basicTypes.add("integer");
		basicTypes.add("positiveInteger");
		basicTypes.add("nonPositiveInteger");
		basicTypes.add("negativeInteger");
		basicTypes.add("nonNegativeInteger");

		basicTypes.add("unsignedLong");
		basicTypes.add("unsignedInt");
		basicTypes.add("unsignedShort");
		basicTypes.add("unsignedByte");

		basicTypes.add("base64Binary");
		basicTypes.add("hexBinary");
		// binary is not a valid XSD type, but used as a placeholder internally
		basicTypes.add("binary");
		basicTypes.add("boolean");
		basicTypes.add("date");
		basicTypes.add("dateTime");
		basicTypes.add("time");
		basicTypes.add("duration");
		basicTypes.add("decimal");
		basicTypes.add("float");
		basicTypes.add("double");
		basicTypes.add("byte");
		basicTypes.add("short");
		basicTypes.add("long");
		basicTypes.add("int");
		basicTypes.add("ID");
		basicTypes.add("IDREF");
		basicTypes.add("NMTOKEN");
		basicTypes.add("NMTOKENS");
		basicTypes.add("Name");

		// basicTypes.add("BaseObject");
	}

	public XSDParser(String stFile, HashMap<String, String> xsdMapping) {
		this.xsdMapping = xsdMapping;
		init(stFile);
	}

	public void parse() throws Exception {

		SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
		saxParserFactory.setNamespaceAware(true);

		XSOMParser parser = new XSOMParser(saxParserFactory);
		parser.setErrorHandler(this);

		parser.setAnnotationParser(new DomAnnotationParserFactory());

		parser.parse(f);

		interpretResult(parser.getResult());

		// TODO: Add optimizations/cleanup/check for duplicates/renaming etc.

		writeMap();

		writer.postProcessNamespacedFilesForIncludes();
	}

	private void writeMap() throws Exception {
		Iterator<Message> messageIterator;
		Message message;
		Set<Message> messageSet;
		Set<String> declared;
		
		boolean bModified;

		if (!marshaller.isNestedEnums() || !isNestEnums()) {
			Iterator<String> ite = enums.keySet().iterator();
			while (ite.hasNext()) {
				writeEnum(ite.next());
			}
		}

		messageSet = new TreeSet<Message>(map.values());
		declared = new TreeSet<String>(basicTypes);
		declared.addAll(enums.keySet());
		declared.addAll(simpleTypes.keySet());

		bModified = true;
		while (!messageSet.isEmpty()) {
			message = ((TreeSet<Message>) messageSet).first();
			writeMessage(message, declared, "");
			messageSet.removeIf(m -> declared.contains(m.getName()));
		}

		if (!messageSet.isEmpty()) {
			// Check if we are missing a type or it's a circular dependency
			Set<String> requiredTypes = new TreeSet<String>();
			Set<String> notYetDeclaredTypes = new TreeSet<String>();
			for (Message s : messageSet) {
				requiredTypes.addAll(s.getTypes());
				notYetDeclaredTypes.add(s.getName());
			}
			requiredTypes.removeAll(declared);
			requiredTypes.removeAll(notYetDeclaredTypes);
			if (requiredTypes.isEmpty()) {
				// Circular dependencies have been detected
				if (marshaller.isCircularDependencySupported()) {
					// Just dump the rest
					for (Message s : messageSet) {
						writeMessage(s, declared, "");
					}
				} else {
					// Report circular dependency
					LOGGER.error(
							"Source schema contains circular dependencies and the target marshaller does not support them. Refer to the reduced dependency graph below.");
					for (Message s : messageSet) {
						s.getTypes().removeAll(declared);
						LOGGER.error(s.getName() + ": " + s.getTypes());
					}
					throw new InvalidXSDException();
				}
			} else {
				// Missing types have been detected
				LOGGER.error("Source schema contains references missing types");
				for (Message s : messageSet) {
					s.getTypes().retainAll(requiredTypes);
					if (!s.getTypes().isEmpty()) {
						LOGGER.error(s.getName() + ": " + s.getTypes());
					}
				}
				throw new InvalidXSDException();
			}
		}
	}

	private void writeMessage(Message message, Set<String> declared, String messageSubScope) throws IOException {
		Iterator<Field> itf;
		Field field;
		String fieldName, fieldType;
		Set<String> usedInEnums;
		int order;

		writeMessageDocumentation(message.getDoc(), message.getNamespace());

		String messageName = message.getName();

		if (marshaller.getNameMapping(messageName) != null) {
			messageName = marshaller.getNameMapping(messageName);
		}

		os(message.getNamespace()).write(marshaller.writeStructHeader(escape(messageName.substring(messageSubScope.length()))).getBytes());

		itf = message.getFields().iterator();
		usedInEnums = new TreeSet<String>();
		LinkedHashSet<Message> subMessages = new LinkedHashSet<>();
		order = 1;
		while (itf.hasNext()) {
			field = itf.next();
			fieldName = field.getName();
			if (marshaller.getNameMapping(fieldName) != null) {
				fieldName = marshaller.getNameMapping(fieldName);
			}

			fieldType = field.getType();
			if (fieldType == null) {
				fieldType = field.getName();
			}
			if (isNestEnums() && marshaller.isNestedEnums() && enums.containsKey(fieldType) && !usedInEnums.contains(fieldType)) {
				usedInEnums.add(fieldType);
				writeEnum(fieldType);
			}

			boolean customTypeMapping = false;
			String typeNameSpace = "";
			if (marshaller.getTypeMapping(fieldType) != null) {
				fieldType = marshaller.getTypeMapping(fieldType);
				customTypeMapping = true;
				int qualifyingDot = fieldType.lastIndexOf('.');
				if (qualifyingDot > -1) {
					typeNameSpace = fieldType.substring(0, qualifyingDot + 1);
					String inclusionPath;
					if (marshaller.getImport(fieldType) != null) {
						inclusionPath = marshaller.getImport(fieldType);
					} else {
						inclusionPath = fieldType.substring(0, qualifyingDot);
					}
					writer.addInclusion(message.getNamespace(), inclusionPath);
					fieldType = fieldType.substring(qualifyingDot + 1);
				}
			} else if (!basicTypes.contains(fieldType) && field.getTypeNamespace() != null && !field.getTypeNamespace().equals(message.getNamespace())) {
				typeNameSpace = field.getTypeNamespace() + ".";
				writer.addInclusion(message.getNamespace(), field.getTypeNamespace());
			}

			SimpleTypeDesc specialisedSimpleFieldType = null;
			if (!customTypeMapping) {

				if (simpleTypes.containsKey(fieldType)) {
					specialisedSimpleFieldType = simpleTypes.get(fieldType);
					fieldType = specialisedSimpleFieldType.getBaseType();
				}

				if (!map.keySet().contains(fieldType) && !basicTypes.contains(fieldType) && !enums.containsKey(fieldType)) {
					fieldType = "binary";
				}


				if (marshaller.getTypeMapping(fieldType) != null) {
					// Message-type has been overridden, need to override all usage
					fieldType = marshaller.getTypeMapping(fieldType);
				}
			}

			if (fieldType.equals(fieldName)) {
				fieldName = "_" + fieldName;
			}

			String doc = field.getDoc();
			if (doc == null) {
				doc = "";
			}

			// nested type
			Message subMessage = map.get(fieldType);
			if (fieldType.startsWith(messageName) && !declared.contains(fieldType)) {
				subMessages.add(subMessage);
				fieldType = fieldType.substring(messageName.length());
			} else {
				fieldType = typeNameSpace + escapeType(fieldType);


				if (specialisedSimpleFieldType != null) {
					if (StringUtils.isNotBlank(doc)) {
						doc += " | ";
					}

					if (!specialisedSimpleFieldType.getName().startsWith("Anonymous")) {
						doc += "XSD type: " + specialisedSimpleFieldType.getName();
					}
					if (specialisedSimpleFieldType.getDocumentation() != null) {
						doc += " | " + specialisedSimpleFieldType.getDocumentation();
					}
				}
			}



			os(message.getNamespace())
					.write(marshaller.writeStructParameter(order, field.isRequired(), field.isRepeat(), escape(fieldName), fieldType, doc, writer.isSplitBySchema(),
							(specialisedSimpleFieldType != null) ? specialisedSimpleFieldType.getFacets() : null).getBytes());


			order = order + 1;
		}

		for (Message subMessage : subMessages) {
			writeMessage(subMessage, declared, messageName + '.');
		}

		os(message.getNamespace()).write(marshaller.writeStructFooter().getBytes());
		declared.add(message.getName());
	}

	private void writeMessageDocumentation(String doc, String namespace) throws IOException {
		StringBuilder sb = new StringBuilder();
		if (includeMessageDocs && doc != null) {
			sb.append("\n/*\n");
			// Handling possible multiline-comments
			sb.append(" * ");
			sb.append(doc.trim().replaceAll("\n", "\n * "));
			sb.append("\n */\n");

		} else {
			sb.append("\n");
		}
		os(namespace).write(sb.toString().getBytes());
	}

	private Iterator<Field> orderedIteratorForFields(List<Field> fields) {
		Collections.sort(fields, new Comparator<Field>() {
			@Override
			public int compare(Field o1, Field o2) {
				if (o1 == null) {
					if (o2 == null)
						return 0;
					return 1;
				}
				return o1.getName().compareTo(o2.getName());
			}
		});
		return fields.iterator();
	}

	private void writeEnum(String type) throws IOException {
		String enumValue;
		Enumeration en;
		Iterator<String> itg;
		en = enums.get(type);
		enumValue = escape(en.getName());

		writeMessageDocumentation(en.getDoc(), en.getNamespace());

		os(en.getNamespace()).write(marshaller.writeEnumHeader(enumValue).getBytes());
		itg = en.iterator();
		int enumOrder = this.enumOrderStart;
		String typePrefix;
		if (typeInEnums) {
			typePrefix = en.getName() + "_";
		} else {
			typePrefix = "";
		}

		// Adding a default-value as "UNSPECIFIED"
		os(en.getNamespace()).write(marshaller.writeEnumValue(enumOrder, escape(typePrefix + "unspecified")).getBytes());
		enumOrder++;

		if (itg.hasNext()) {
			while (itg.hasNext()) {
				os(en.getNamespace()).write(marshaller.writeEnumValue(enumOrder, escape(typePrefix + itg.next())).getBytes());
				enumOrder++;
			}
		} else {
			os(en.getNamespace()).write(marshaller.writeEnumValue(enumOrder, escape(typePrefix + "UnspecifiedValue")).getBytes());
		}

		os(en.getNamespace()).write(marshaller.writeEnumFooter().getBytes());
	}

	private String escape(String name) {
		String res = escapeType(name);

		if (basicTypes.contains(res)) {
			res = "_" + res;
		}

		return res;
	}

	private String escapeType(String name) {
		String res;

		final char[] nameChars = name.toCharArray();

		for (int i = 0; i < nameChars.length; i++) {
			if (!Character.isJavaIdentifierPart(nameChars[i])) {
				nameChars[i] = '_';
			}
		}

		res = String.valueOf(nameChars);

		if (!Character.isJavaIdentifierStart(nameChars[0]) || keywords.contains(res)) {
			res = res + "Value";
		}

		return res;
	}

	private void interpretResult(XSSchemaSet sset) {
		XSSchema xs;
		Iterator<XSSchema> it;
		Iterator<XSElementDecl> itt;
		XSElementDecl el;

		it = sset.iterateSchema();
		while (it.hasNext()) {
			xs = it.next();
			if (!xs.getTargetNamespace().endsWith("/XMLSchema")) {
				Iterator<XSModelGroupDecl> xsModelGroupDeclIterator = xs.iterateModelGroupDecls();
				while (xsModelGroupDeclIterator.hasNext()) {
					XSModelGroupDecl modelGroupDecl = xsModelGroupDeclIterator.next();
					XSModelGroup modelGroup = modelGroupDecl.getModelGroup();
					processModelGroup(modelGroup, null, xs.getRoot());
				}
				itt = xs.iterateElementDecls();
				while (itt.hasNext()) {
					el = itt.next();
					interpretElement(el, sset);
				}
				final Iterator<XSComplexType> ict = xs.iterateComplexTypes();
				while (ict.hasNext()) {
					processComplexType(ict.next(), null, null, sset);
				}
				final Iterator<XSSimpleType> ist = xs.iterateSimpleTypes();
				while (ist.hasNext()) {
					processSimpleType(ist.next(), null);
				}
			}
		}
	}

	private List<Field> processModelGroup(XSModelGroup modelGroup, String ownerTypeName, XSSchemaSet xsset) {

		List<Field> groupFields = new ArrayList<>();

		for (XSParticle child : modelGroup.getChildren()) {
			if (child.getTerm().asModelGroupDecl() != null) {
				XSModelGroupDecl xsModelGroupDecl = child.getTerm().asModelGroupDecl();
				groupFields.addAll(processModelGroup(xsModelGroupDecl.getModelGroup(), ownerTypeName, xsset));
			} else if (child.getTerm().asModelGroup() != null) {
				XSModelGroup xsModelGroup = child.getTerm().asModelGroup();
				if (xsModelGroup.getCompositor().toString().equals("choice")) {
					// TODO: define oneof here?
				}
				groupFields.addAll(processModelGroup(xsModelGroup, ownerTypeName, xsset));
			} else {
				XSElementDecl term = child.getTerm().asElementDecl();
				if (term != null) {
					Field f;
					String doc = resolveDocumentationAnnotation(term);
					XSType type = term.getType();

					if (type != null && type.asComplexType() != null) {
						XSComplexType xsComplexType = type.asComplexType();
						String typeName = processComplexType(xsComplexType.asComplexType(), xsComplexType.getName(), ownerTypeName, xsset);

						f = new Field(term.getName(), xsComplexType.getTargetNamespace(), typeName, child.isRepeated(), null, doc,
								child.getMinOccurs().intValue() > 0);
					} else if (type != null && type.isSimpleType() && type.getName() == null) {
						XSSimpleType xsSimpleType = type.asSimpleType();
						String typeName = processSimpleType(xsSimpleType, term.getName());

						f = new Field(term.getName(), term.getTargetNamespace(), typeName, child.isRepeated(), null, doc,
								child.getMinOccurs().intValue() > 0);
					} else {

						f = new Field(term.getName(), term.getTargetNamespace(), type.getName(), child.isRepeated(), null, doc,
								child.getMinOccurs().intValue() > 0);
					}


					groupFields.add(f);
				}
			}
		}

		return groupFields;
	}

	private void interpretElement(XSElementDecl el, XSSchemaSet sset) {
		XSComplexType cType;
		XSSimpleType xs;

		if (el.getType() instanceof XSComplexType && el.getType() != sset.getAnyType()) {
			cType = (XSComplexType) el.getType();
			processComplexType(cType, el.getName(), null, sset);
		} else if (el.getType() instanceof XSSimpleType && el.getType() != sset.getAnySimpleType()) {
			xs = el.getType().asSimpleType();
			processSimpleType(xs, el.getName());
		}
	}

	/**
	 * @param xs
	 * @param elementName
	 */
	private String processSimpleType(XSSimpleType xs, String elementName) {

		if (elementName != null && marshaller.getNameMapping(elementName) != null) {
			elementName = marshaller.getNameMapping(elementName);
		}

		String typeName = xs.getName();
		String namespace = xs.getTargetNamespace();

		if (typeName == null) {
			if (xs.getFacet("enumeration") != null) {
				typeName = elementName != null ? elementName + "Type" : generateAnonymousName();
			} else {
				// can't use elementName here as it might not be unique
				// (test-range.xsd)
				typeName = generateAnonymousName();
			}
		}

		if (xs.isRestriction() && xs.getFacet("enumeration") != null) {
			createEnum(typeName, namespace, xs.asRestriction());
		} else {
			// This is just a restriction on a basic type, find parent and map
			// it to the type
			String doc = resolveDocumentationAnnotation(xs);
			String baseTypeName = typeName;
			List<XSFacet> facets = new ArrayList<>();
			while (xs != null && !basicTypes.contains(baseTypeName)) {
				if (xs.isRestriction()) {
					facets.addAll(((XSRestrictionSimpleType)xs).getDeclaredFacets());
				}
				xs = xs.getBaseType().asSimpleType();

				if (xs != null) {
					baseTypeName = xs.getName();
				}
			}
			SimpleTypeDesc  desc = new SimpleTypeDesc(typeName, xs != null ? xs.getName() : "string");
			desc.setDocumentation(doc);
			desc.setFacets(facets);

			simpleTypes.put(typeName, desc);

			addDocumentation(typeName, doc);
		}
		return typeName;
	}

	private void addDocumentation(String typeName, String doc) {
		if (doc != null) {
			documentation.put(typeName, doc);
		}
	}

	/**
	 * @param cType
	 * @param elementName
	 * @param sset
	 */
	private String processComplexType(XSComplexType cType, String elementName, String elementOwnerType, XSSchemaSet sset) {
		Message st = null;
		XSType parent;
		String typeName = cType.getName();
		String nameSpace = cType.getTargetNamespace();

		if (elementName != null && marshaller.getNameMapping(elementName) != null) {
			elementName = marshaller.getNameMapping(elementName);
		} else if (cType.getScope() != null) {
			elementName = cType.getScope().getName();
		}

		if (typeName == null) {
			typeName = elementName != null ? ((elementOwnerType != null) ? elementOwnerType + "." : "") + elementName + "Type" : generateAnonymousName();
		}
		String doc = resolveDocumentationAnnotation(cType);

		st = map.get(typeName);
		if (st == null && !basicTypes.contains(typeName)) {

			st = new Message(typeName, NamespaceConverter.convertFromSchema(nameSpace));
			st.setDoc(doc);

			map.put(typeName, st);
			XSComplexType asCtype = cType.asComplexType();
			if (cType.asComplexType() != null && cType != asCtype) {
				processComplexType(asCtype, elementName, typeName, sset);
			} else if (cType.getContentType() != null) {
				if (cType.getContentType().asParticle() != null) {
					XSParticle particle = cType.getContentType().asParticle();
					if (particle.getTerm() != null && particle.getTerm().asModelGroup() != null) {
						List<Field> fields = processModelGroup(particle.getTerm().asModelGroup(), typeName, sset);
						st.addFields(fields, xsdMapping);
					} else if (particle.getTerm() != null && particle.getTerm().asModelGroupDecl() != null) {
						List<Field> fields = processModelGroup(particle.getTerm().asModelGroupDecl().getModelGroup(), typeName, sset);
						st.addFields(fields, xsdMapping);
					}
				} else if (cType.getContentType().asSimpleType() != null) {
					XSSimpleType xsSimpleType = cType.getContentType().asSimpleType();
					if (basicTypes.contains(xsSimpleType.getName())) {
						st.addField(xsSimpleType.getName(), xsSimpleType.getName(), true, false, null, resolveDocumentationAnnotation(cType), xsdMapping);
					} else {
						XSSimpleType primitiveType = xsSimpleType.getPrimitiveType();
						if (primitiveType != null) {
							st.addField(primitiveType.getName(), primitiveType.getTargetNamespace(), primitiveType.getName(), true, false, null,
									resolveDocumentationAnnotation(cType), xsdMapping);
						}
					}
				}

			}

			if (cType.getAttributeUses() != null) {
				Collection attributes = cType.getAttributeUses();
				Iterator<XSAttributeUse> iterator = attributes.iterator();
				while (iterator.hasNext()) {
					XSAttributeUse attr = iterator.next();
					XSAttributeDecl decl = attr.getDecl();

					if (decl.getType().getPrimitiveType() != null) {
						String fieldName = decl.getName();

						if (decl.getType().isRestriction() && decl.getType().getFacet("enumeration") != null) {
							st.addField(fieldName, createEnum(fieldName, decl.getTargetNamespace(), decl.getType().asRestriction()), false, false, null, null,
									xsdMapping);
						} else {
							st.addField(fieldName, decl.getType().getPrimitiveType().getName(), false, false, null, null, xsdMapping);
						}
					}
				}
			}
			parent = cType;
			while (parent != sset.getAnyType()) {
				if (parent.isComplexType()) {
					Message parentMessage = null;
					if (parent.getName() != null) {
						parentMessage = map.get(parent.getName());
					}
					if (parentMessage == null && ((ComplexTypeImpl) parent).getScope() != null) {
						parentMessage = map.get(((ComplexTypeImpl) parent).getScope().getName());
					}

					if (parentMessage != null) {
						List<Field> parentStructFields = parentMessage.getFields();
						parentStructFields.removeIf(f -> f.getType() != null && f.getType().endsWith("/XMLSchema"));
						st.addFields(parentStructFields, xsdMapping);
					}

//					write(st, parent.asComplexType(), true, sset);
				}
				parent = parent.getBaseType();
			}

			st.setParent(cType.getBaseType().getName());
		}
		return typeName;
	}

	private String resolveDocumentationAnnotation(XSComponent xsComponent) {
		String doc = null;
		if (xsComponent.getAnnotation() != null && xsComponent.getAnnotation().getAnnotation() != null) {
			if (xsComponent.getAnnotation().getAnnotation() instanceof Node) {
				Node annotationEl = (Node) xsComponent.getAnnotation().getAnnotation();
				NodeList annotations = annotationEl.getChildNodes();

				for (int i = 0; i < annotations.getLength(); i++) {
					Node annotation = annotations.item(i);
					if ("documentation".equals(annotation.getLocalName())) {

						NodeList childNodes = annotation.getChildNodes();
						for (int j = 0; j < childNodes.getLength(); j++) {
							if (childNodes.item(j) != null && childNodes.item(j) instanceof Text) {
								doc = childNodes.item(j).getNodeValue();
							}
						}
					}
				}
			}
		}
		return doc;
	}

	private int anonymousCounter = 0;

	/**
	 * @return
	 */
	private String generateAnonymousName() {
		anonymousCounter++;
		return String.format("Anonymous%03d", anonymousCounter);
	}

	private String createEnum(String typeName, String namespace, XSRestrictionSimpleType type) {
		Enumeration en;
		Iterator<? extends XSFacet> it;

		if (!enums.containsKey(typeName)) {
			type = type.asRestriction();

			if (type.getName() == null) {
				typeName += "Type";
			}

			en = new Enumeration(typeName, NamespaceConverter.convertFromSchema(namespace));
			it = type.getDeclaredFacets().iterator();
			while (it.hasNext()) {
				en.addString(it.next().getValue().value);
			}
			String doc = resolveDocumentationAnnotation(type);
			en.setDoc(doc);

			enums.put(typeName, en);
		}
		return typeName;
	}

	@Override
	public void error(SAXParseException exception) throws SAXException {
		LOGGER.error(exception.getMessage() + " at " + exception.getSystemId());
		exception.printStackTrace();
	}

	@Override
	public void fatalError(SAXParseException exception) throws SAXException {
		LOGGER.error(exception.getMessage() + " at " + exception.getSystemId());
		exception.printStackTrace();
	}

	@Override
	public void warning(SAXParseException exception) throws SAXException {
		LOGGER.warn(exception.getMessage() + " at " + exception.getSystemId());
		exception.printStackTrace();
	}

	public void addMarshaller(ProtobufMarshaller marshaller) {
		this.marshaller = marshaller;
	}

	public void setNestEnums(boolean nestEnums) {
		this.nestEnums = nestEnums;
	}

	public boolean isNestEnums() {
		return nestEnums;
	}

	private OutputStream os(String namespace) throws IOException {
		return writer.getStream(namespace);
	}

	public void setWriter(OutputWriter writer) {
		this.writer = writer;
	}

	public void setEnumOrderStart(int enumOrderStart) {
		this.enumOrderStart = enumOrderStart;
	}

	public void setTypeInEnums(boolean typeInEnums) {
		this.typeInEnums = typeInEnums;
	}

	public void setIncludeMessageDocs(boolean includeMessageDocs) {
		this.includeMessageDocs = includeMessageDocs;
	}

	public void setIncludeFieldDocs(boolean includeFieldDocs) {
		this.includeFieldDocs = includeFieldDocs;
	}
}
