package com.github.tranchis.xsd2thrift;

import com.sun.xml.xsom.XSFacet;

import java.util.List;

/**
 * @author gpicron.
 */
public class SimpleTypeDesc {
    private final String name;
    private final String baseType;
    private String documentation;
    private List<XSFacet> facets;

    public SimpleTypeDesc(String name, String baseType) {
        this.name = name;
        this.baseType = baseType;
    }


    public String getBaseType() {
        return baseType;
    }

    public String getDocumentation() {
        return documentation;
    }

    public void setDocumentation(String documentation) {
        this.documentation = documentation;
    }

    public String getName() {
        return this.name;
    }

    public void setFacets(List<XSFacet> facets) {
        this.facets = facets;
    }

    public List<XSFacet> getFacets() {
        return facets;
    }
}
