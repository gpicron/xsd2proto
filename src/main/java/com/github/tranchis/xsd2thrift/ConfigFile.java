package com.github.tranchis.xsd2thrift;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigFile {
    public int protobufVersion = 3;
    public String filename;
    public String directory;
    public String namespace;
    public boolean splitBySchema = false;
    public Map<String, String> customTypeMappingsMap = new HashMap<>();
    public Map<String, String> customNameMappingsMap = new HashMap<>();
    public boolean nestEnums;
    public boolean typeInEnums = true;
    public boolean includeMessageDocs = true;
    public boolean includeFieldDocs = true;
    public String xsd;

    public void setCustomTypeMappings(List<String> customTypeMappings) {

        for (String mapping : customTypeMappings) {
            int colon = mapping.indexOf(':');
            if (colon > -1) {
                this.customTypeMappingsMap.put(mapping.substring(0, colon),
                        mapping.substring(colon + 1));
            }
        }
    }

    public void setCustomNameMappings(List<String> customNameMappings) {

        for (String mapping : customNameMappings) {
            int colon = mapping.indexOf(':');
            if (colon > -1) {
                this.customNameMappingsMap.put(mapping.substring(0, colon),
                        mapping.substring(colon + 1));
            }
        }
    }
}
