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

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

import javax.xml.parsers.SAXParserFactory;

import com.sun.xml.xsom.*;
import com.sun.xml.xsom.impl.ComplexTypeImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.*;

import com.github.tranchis.xsd2thrift.marshal.ProtobufMarshaller;
import com.sun.xml.xsom.parser.XSOMParser;
import com.sun.xml.xsom.util.DomAnnotationParserFactory;

public class XSDParser implements ErrorHandler {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(XSDParser.class);

	private File f;
	private TreeMap<String, Message> map;
	private Map<String, Enumeration> enums;
	private Map<String, String> simpleTypes;
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
		map = new TreeMap<String, Message>();
		enums = new HashMap<String, Enumeration>();
		simpleTypes = new HashMap<String, String>();
		documentation = new HashMap<String, String>();
		keywords = new TreeSet<String>();
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
		Iterator<Message> its;
		Message st;
		Set<Message> ss;
		Set<String> declared;
		Iterator<String> ite;
		boolean bModified;

		if (!marshaller.isNestedEnums() || !isNestEnums()) {
			ite = enums.keySet().iterator();
			while (ite.hasNext()) {
				writeEnum(ite.next());
			}
		}

		ss = new TreeSet<Message>(map.values());
		declared = new TreeSet<String>(basicTypes);
		declared.addAll(enums.keySet());
		declared.addAll(simpleTypes.keySet());

		bModified = true;
		while (bModified && !ss.isEmpty()) {
			bModified = false;
			its = map.values().iterator();
			while (its.hasNext()) {
				st = its.next();
				writeStruct(st, declared);
				ss.remove(st);
				bModified = true;
			}
		}

		if (!ss.isEmpty()) {
			// Check if we are missing a type or it's a circular dependency
			Set<String> requiredTypes = new TreeSet<String>();
			Set<String> notYetDeclaredTypes = new TreeSet<String>();
			for (Message s : ss) {
				requiredTypes.addAll(s.getTypes());
				notYetDeclaredTypes.add(s.getName());
			}
			requiredTypes.removeAll(declared);
			requiredTypes.removeAll(notYetDeclaredTypes);
			if (requiredTypes.isEmpty()) {
				// Circular dependencies have been detected
				if (marshaller.isCircularDependencySupported()) {
					// Just dump the rest
					for (Message s : ss) {
						writeStruct(s, declared);
					}
				} else {
					// Report circular dependency
					LOGGER.error("Source schema contains circular dependencies and the target marshaller does not support them. Refer to the reduced dependency graph below.");
					for (Message s : ss) {
						s.getTypes().removeAll(declared);
						LOGGER.error(s.getName() + ": " + s.getTypes());
					}
					throw new InvalidXSDException();
				}
			} else {
				// Missing types have been detected
				LOGGER.error("Source schema contains references missing types");
				for (Message s : ss) {
					s.getTypes().retainAll(requiredTypes);
					if (!s.getTypes().isEmpty()) {
						LOGGER.error(s.getName() + ": " + s.getTypes());
					}
				}
				throw new InvalidXSDException();
			}
		}
	}

	private void writeStruct(Message st, Set<String> declared)
			throws IOException {
		Iterator<Field> itf;
		Field f;
		String fname, type;
		Set<String> usedInEnums;
		int order;

		writeMessageDocumentation(st.getDoc(), st.getNamespace());

		String messageName = st.getName();

		if(marshaller.getNameMapping(messageName) != null) {
			messageName = marshaller.getNameMapping(messageName);
		}

		os(st.getNamespace()).write(marshaller.writeStructHeader(escape(messageName)).getBytes());

		itf = orderedIteratorForFields(st.getFields());
		usedInEnums = new TreeSet<String>();
		order = 1;
		while (itf.hasNext()) {
			f = itf.next();
			fname = f.getName();
			if(marshaller.getNameMapping(fname) != null) {
				fname = marshaller.getNameMapping(fname);
			}
			
			type = f.getType();
			if (type == null) {
				type = f.getName();
			}
			if (isNestEnums() && marshaller.isNestedEnums()
					&& enums.containsKey(type) && !usedInEnums.contains(type)) {
				usedInEnums.add(type);
				writeEnum(type);
			}

			if (simpleTypes.containsKey(type)) {
				type = simpleTypes.get(type);
			}

			if (!map.keySet().contains(type) && !basicTypes.contains(type)
					&& !enums.containsKey(type)) {
				type = "binary";
			}
			if (type.equals(fname)) {
				fname = "_" + fname;
			}

			String typeNameSpace = "";
			if (marshaller.getTypeMapping(type) != null) {
				type = marshaller.getTypeMapping(type);
				int qualifyingDot = type.lastIndexOf('.');
				if (qualifyingDot > -1) {
					typeNameSpace = type.substring(0, qualifyingDot + 1);
					String inclusionPath;
					if (marshaller.getImport(type) != null) {
						inclusionPath = marshaller.getImport(type);
					} else {
						inclusionPath = type.substring(0, qualifyingDot);
					}
					writer.addInclusion(st.getNamespace(),
							inclusionPath);
					type = type.substring(qualifyingDot + 1);
				}
			} else if (!basicTypes.contains(type)
					&& f.getTypeNamespace() != null
					&& !f.getTypeNamespace().equals(st.getNamespace())) {
				typeNameSpace = f.getTypeNamespace() + ".";
				writer.addInclusion(st.getNamespace(), f.getTypeNamespace());
			}

			if (marshaller.getTypeMapping(type) != null) {
				//Message-type has been overridden, need to override all usage
				type = marshaller.getTypeMapping(type);
			}

			type = typeNameSpace + escapeType(type);

            String doc = null;
			if (map.get(type) != null) {
			    doc = map.get(type).getDoc();
            }

            os(st.getNamespace()).write(
					marshaller.writeStructParameter(order, f.isRequired(),
							f.isRepeat(), escape(fname), type, doc, writer.isSplitBySchema()).getBytes());
			order = order + 1;
		}
		os(st.getNamespace()).write(marshaller.writeStructFooter().getBytes());
		declared.add(st.getName());
	}

	private void writeMessageDocumentation(String doc, String namespace) throws IOException {
		if (includeMessageDocs && doc != null) {
			StringBuilder sb = new StringBuilder();
			sb.append("\n/*\n");
			// Handling possible multiline-comments
			sb.append(" * ");
			sb.append(doc.trim().replaceAll("\n", "\n * "));
			sb.append("\n */\n");

			os(namespace).write(sb.toString().getBytes());
		}
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

		os(en.getNamespace()).write(
				marshaller.writeEnumHeader(enumValue).getBytes());
		itg = en.iterator();
		int enumOrder = this.enumOrderStart;
		String typePrefix;
		if (typeInEnums) {
			typePrefix = en.getName() + "_";
		} else {
			typePrefix = "";
		}

		//Adding a default-value as "NOT_SET"
		os(en.getNamespace()).write(
				marshaller.writeEnumValue(enumOrder,
						escape(typePrefix + "notSet"))
						.getBytes());
		enumOrder++;

		if (itg.hasNext()) {
			while (itg.hasNext()) {
				os(en.getNamespace()).write(
						marshaller.writeEnumValue(enumOrder,
								escape(typePrefix + itg.next()))
								.getBytes());
				enumOrder++;
			}
		} else {
			os(en.getNamespace()).write(
					marshaller.writeEnumValue(enumOrder,
							escape(typePrefix + "UnspecifiedValue"))
							.getBytes());
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

		if (!Character.isJavaIdentifierStart(nameChars[0])
				|| keywords.contains(res)) {
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
					processModelGroup(modelGroup, xs.getRoot());
				}
				itt = xs.iterateElementDecls();
				while (itt.hasNext()) {
					el = itt.next();
					interpretElement(el, sset);
				}
				final Iterator<XSComplexType> ict = xs.iterateComplexTypes();
				while (ict.hasNext()) {
					processComplexType(ict.next(), null, sset);
				}
				final Iterator<XSSimpleType> ist = xs.iterateSimpleTypes();
				while (ist.hasNext()) {
					processSimpleType(ist.next(), null);
				}
			}
		}
	}

	private List<Field> processModelGroup(XSModelGroup modelGroup, XSSchemaSet xsset) {

		List<Field> groupFields = new ArrayList<>();

		for (XSParticle child : modelGroup.getChildren()) {
			if (child.getTerm().asModelGroupDecl() != null) {
				XSModelGroupDecl xsModelGroupDecl = child.getTerm().asModelGroupDecl();
				groupFields.addAll(processModelGroup(xsModelGroupDecl.getModelGroup(), xsset));
			} else if(child.getTerm().asModelGroup() != null) {
				XSModelGroup xsModelGroup = child.getTerm().asModelGroup();
				if (xsModelGroup.getCompositor().toString().equals("choice")) {
					// TODO: define oneof here?
				}
				groupFields.addAll(processModelGroup(xsModelGroup, xsset));
			} else {
				XSElementDecl term = child.getTerm().asElementDecl();
				if (term != null) {
					Field f;
					String doc = resolveDocumentationAnnotation(term);
					if (term.getType() != null && term.getType().asComplexType() != null) {
						XSComplexType xsComplexType = term.getType().asComplexType();
						String typeName = processComplexType(xsComplexType.asComplexType(), xsComplexType.getName(), xsset);

						f = new Field(term.getName(), xsComplexType.getTargetNamespace(), typeName, child.isRepeated(), null, doc, child.getMinOccurs().intValue() > 0);

					} else {
						f = new Field(term.getName(), term.getTargetNamespace(), term.getType().getName(), child.isRepeated(), null, doc, child.getMinOccurs().intValue() > 0);
					}

					f.setDoc(resolveDocumentationAnnotation(term));
					groupFields.add(f);
				}
			}
		}

		return groupFields;
	}

	private void interpretElement(XSElementDecl el, XSSchemaSet sset) {
		XSComplexType cType;
		XSSimpleType xs;

		if (el.getType() instanceof XSComplexType
				&& el.getType() != sset.getAnyType()) {
			cType = (XSComplexType) el.getType();
			processComplexType(cType, el.getName(), sset);
		} else if (el.getType() instanceof XSSimpleType
				&& el.getType() != sset.getAnySimpleType()) {
			xs = el.getType().asSimpleType();
			processSimpleType(xs, el.getName());
		}
	}

	private String processType(XSType type, String elementName, XSSchemaSet sset) {
		if (type instanceof XSComplexType) {
			return processComplexType(type.asComplexType(), elementName, sset);
		} else {
			return processSimpleType(type.asSimpleType(), elementName);
		}
	}

	/**
	 * @param xs
	 * @param elementName
	 */
	private String processSimpleType(XSSimpleType xs, String elementName) {

		if(elementName != null && marshaller.getNameMapping(elementName) != null) {
			elementName = marshaller.getNameMapping(elementName);
		}

		
		String typeName = xs.getName();
		String namespace = xs.getTargetNamespace();

		if (typeName == null) {
			if (xs.getFacet("enumeration") != null) {
				typeName = elementName != null ? elementName + "Type"
						: generateAnonymousName();
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
			String baseTypeName = typeName;
			while (xs != null && !basicTypes.contains(baseTypeName)) {
				xs = xs.getBaseType().asSimpleType();
				if (xs != null) {
					baseTypeName = xs.getName();
				}
			}
			simpleTypes.put(typeName, xs != null ? xs.getName() : "string");
			String doc = resolveDocumentationAnnotation(xs);
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
	private String processComplexType(XSComplexType cType, String elementName,
			XSSchemaSet sset) {
		Message st = null;
		XSType parent;
		String typeName = cType.getName();
		String nameSpace = cType.getTargetNamespace();

		if(elementName != null && marshaller.getNameMapping(elementName) != null) {
			elementName = marshaller.getNameMapping(elementName);
		} else if (cType.getScope() != null){
            elementName = cType.getScope().getName();
        }

		if (typeName == null) {
			typeName = elementName != null ? elementName + "Type"
					: generateAnonymousName();
		}
		String doc = resolveDocumentationAnnotation(cType);

		st = map.get(typeName);
		if (st == null && !basicTypes.contains(typeName)) {

			st = new Message(typeName,
					NamespaceConverter.convertFromSchema(nameSpace));
			st.setDoc(doc);

			map.put(typeName, st);
			if (cType.getContentType() != null) {
				if (cType.getContentType().asParticle() != null) {
					XSParticle particle = cType.getContentType().asParticle();
					if (particle.getTerm() != null && particle.getTerm().asModelGroup() != null) {
						List<Field> fields = processModelGroup(particle.getTerm().asModelGroup(), sset);
						st.addFields(fields, xsdMapping);
					} else if (particle.getTerm() != null && particle.getTerm().asModelGroupDecl() != null) {
						List<Field> fields = processModelGroup(particle.getTerm().asModelGroupDecl().getModelGroup(), sset);
						st.addFields(fields, xsdMapping);
					}
				} else if (cType.getContentType().asSimpleType() != null) {
					XSSimpleType xsSimpleType = cType.getContentType().asSimpleType();
					if (basicTypes.contains(xsSimpleType.getName())) {
						st.addField(xsSimpleType.getName(), xsSimpleType.getName(), true, false, null, resolveDocumentationAnnotation(cType), xsdMapping);
					} else {
						XSSimpleType primitiveType = xsSimpleType.getPrimitiveType();
						if (primitiveType != null) {
							st.addField(primitiveType.getName(), primitiveType.getTargetNamespace(), primitiveType.getName(), true, false, null, resolveDocumentationAnnotation(cType), xsdMapping);
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
                        	st.addField(fieldName, createEnum(fieldName, decl.getTargetNamespace(), decl.getType().asRestriction()), false, false, null, null, xsdMapping);
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
			if (xsComponent.getAnnotation().getAnnotation() instanceof Node){
				Node annotationEl = (Node) xsComponent.getAnnotation().getAnnotation();
                NodeList annotations = annotationEl.getChildNodes();

                for (int i = 0; i < annotations.getLength(); i++) {
                    Node annotation = annotations.item(i);
                    if ("documentation".equals(annotation.getLocalName())) {

                        NodeList childNodes = annotation.getChildNodes();
                        for (int j = 0; j < childNodes.getLength(); j++) {
                            if (childNodes.item(j) != null &&
                                    childNodes.item(j) instanceof Text) {
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

//	private void write(Struct st, XSComplexType type, boolean goingup,
//			XSSchemaSet xss) {
//		XSParticle particle;
//		Iterator<? extends XSAttributeUse> it;
//		XSAttributeUse att;
//		XSAttributeDecl decl;
//		Iterator<? extends XSAttGroupDecl> itt;
//
//		particle = type.getContentType().asParticle();
//		if (particle != null) {
//			write(st, particle.getTerm(), true, xss);
//		}
//
//		itt = type.getAttGroups().iterator();
//		while (itt.hasNext()) {
//			write(st, itt.next(), true);
//		}
//
//		it = type.getAttributeUses().iterator();
//		while (it.hasNext()) {
//			att = it.next();
//			decl = att.getDecl();
//			write(st, decl, goingup && att.isRequired());
//		}
//	}

//	private void write(Struct st, XSAttributeDecl decl, boolean goingup) {
//		if (decl.getType().isRestriction()
//				&& (decl.getType().getName() == null || !basicTypes
//						.contains(decl.getType().getName()))) {
//			String typeName = processSimpleType(decl.getType(), decl.getName());
//			st.addField(decl.getName(), typeName, goingup, false,
//					decl.getFixedValue(), resolveDocumentationAnnotation(decl), xsdMapping);
//		} else if (decl.getType().isList()) {
//			st.addField(decl.getName(), decl.getType().asList().getItemType()
//					.getName(), goingup, true, null, resolveDocumentationAnnotation(decl), xsdMapping);
//		} else {
//			st.addField(decl.getName(), decl.getType().getName(), goingup,
//					false, decl.getFixedValue(), resolveDocumentationAnnotation(decl), xsdMapping);
//		}
//	}

	private String createEnum(String typeName, String namespace,
							  XSRestrictionSimpleType type) {
		Enumeration en;
		Iterator<? extends XSFacet> it;

		if (!enums.containsKey(typeName)) {
			type = type.asRestriction();

			if (type.getName() == null) {
				typeName += "Type";
			}

			en = new Enumeration(typeName,
					NamespaceConverter.convertFromSchema(namespace));
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

//	private void write(Struct st, XSAttGroupDecl attGroup, boolean goingup) {
//		Iterator<? extends XSAttributeUse> it;
//		Iterator<? extends XSAttGroupDecl> itg;
//		XSAttributeUse att;
//		XSAttributeDecl decl;
//
//		itg = attGroup.getAttGroups().iterator();
//
//		while (itg.hasNext()) {
//			write(st, itg.next(), goingup);
//		}
//
//		it = attGroup.getDeclaredAttributeUses().iterator();
//		while (it.hasNext()) {
//			att = it.next();
//			decl = att.getDecl();
//			if (decl.getType().getName() == null) {
//				if (decl.getType().isRestriction()) {
//					String typeName = processSimpleType(decl.getType(),
//							decl.getName());
//					st.addField(decl.getName(), typeName,
//							(goingup && att.isRequired()), false,
//							decl.getFixedValue(), resolveDocumentationAnnotation(decl), xsdMapping);
//				}
//			} else {
//				write(st, decl, att.isRequired());
//			}
//		}
//	}

//	private void processInheritance(Struct st, XSComplexType cType,
//			XSSchemaSet sset) {
//		Iterator<XSType> ity;
//		XSType xt;
//		XSParticle particle;
//		XSComplexType type;
//		Iterator<? extends XSAttributeUse> itau;
//		XSAttributeUse att;
//		XSAttributeDecl decl;
//		Iterator<? extends XSAttGroupDecl> itagd;
//
//		ity = sset.iterateTypes();
//		while (ity.hasNext()) {
//			xt = ity.next();
//			if (xt.getBaseType() == cType) {
//				// NOTE: the below code is almost identical to the contents of
//				// the write() function which takes an XSComplexType argument -
//				// the only difference is in the "goingup" variable passed to
//				// the other write() functions internally
//
//				type = xt.asComplexType();
//
//				particle = type.getContentType().asParticle();
//				if (particle != null) {
//					write(st, particle.getTerm(), false, sset);
//				}
//
//				itagd = type.getAttGroups().iterator();
//				while (itagd.hasNext()) {
//					write(st, itagd.next(), false);
//				}
//
//				itau = type.getAttributeUses().iterator();
//				while (itau.hasNext()) {
//					att = itau.next();
//					decl = att.getDecl();
//					write(st, decl, false);
//				}
//
//				processInheritance(st, xt.asComplexType(), sset);
//			}
//		}
//	}

//	private void write(Struct st, XSTerm term, boolean goingup, XSSchemaSet xss) {
//		XSModelGroup modelGroup;
//		XSParticle[] ps;
//		XSParticle p;
//
//		if (term != null && term.isModelGroup()) {
//			modelGroup = term.asModelGroup();
//			if (XSModelGroup.CHOICE.equals(modelGroup.getCompositor())) {
//				goingup = false;
//			}
//
//			ps = modelGroup.getChildren();
//			for (int i = 0; i < ps.length; i++) {
//				p = ps[i];
//				term = p.getTerm();
//				if (term.isModelGroup()) {
//					write(st, term, goingup, xss);
//				} else if (term.isElementDecl()) {
//					if (term.asElementDecl().getType().getName() == null) {
//						final String typeName = processType(term
//								.asElementDecl().getType(), term
//								.asElementDecl().getName(), xss);
//						String ns = null;
//						if (map.containsKey(typeName)) {
//							ns = map.get(typeName).getNamespace();
//						}
//						st.addField(term.asElementDecl().getName(), ns,
//								typeName, (goingup && p.getMinOccurs()
//										.intValue() != 0), p.getMaxOccurs()
//										.intValue() != 1, term.asElementDecl()
//										.getFixedValue(), resolveDocumentationAnnotation(term),
//                                xsdMapping);
//					} else {
//						st.addField(term.asElementDecl().getName(),
//								term.asElementDecl().getType()
//										.getTargetNamespace(), term
//										.asElementDecl().getType().getName(),
//								(goingup && p.getMinOccurs().intValue() != 0),
//								p.getMaxOccurs().intValue() != 1, term
//										.asElementDecl().getFixedValue(),
//                                resolveDocumentationAnnotation(term),
//								xsdMapping);
//					}
//				}
//			}
//		}
//	}

	@Override
	public void error(SAXParseException exception) throws SAXException {
		LOGGER.error(exception.getMessage() + " at "
				+ exception.getSystemId());
		exception.printStackTrace();
	}

	@Override
	public void fatalError(SAXParseException exception) throws SAXException {
		LOGGER.error(exception.getMessage() + " at "
				+ exception.getSystemId());
		exception.printStackTrace();
	}

	@Override
	public void warning(SAXParseException exception) throws SAXException {
		LOGGER.warn(exception.getMessage() + " at "
				+ exception.getSystemId());
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
