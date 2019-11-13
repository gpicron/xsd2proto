package com.github.tranchis.xsd2thrift;

import java.util.Map;

public class ConfigFile {
	public String filename;
	public String directory;
	public String namespace;
	public boolean splitBySchema = false;
	public Map<String, String> customTypeMappings;
	public Map<String, String> customNameMappings;
	public Map<String, String> customImports;
	public Map<String, Object> options;
	public boolean nestEnums;
	public boolean typeInEnums = true;
	public boolean includeMessageDocs = true;
	public boolean includeFieldDocs = true;
	public String xsd;
}
