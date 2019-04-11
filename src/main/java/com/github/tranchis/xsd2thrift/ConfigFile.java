package com.github.tranchis.xsd2thrift;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigFile {
    public String marshaller = "protobuf";
    public int protobufVersion = 3;
    public String filename;
    public String directory;
    public String namespace;
    public boolean splitBySchema = false;
    public Map<String, String> customMappingsMap = new HashMap<>();
    public boolean nestEnums;
    public boolean typeInEnums = true;
    public boolean includeMessageDocs = true;
    public boolean includeFieldDocs = true;
    public String xsd;

    public void setCustomMappings(List<String> customMappings) {

        for (String mapping : customMappings) {
            int colon = mapping.indexOf(':');
            if (colon > -1) {
                this.customMappingsMap.put(mapping.substring(0, colon),
                        mapping.substring(colon + 1));
            }
        }
    }
}
