package com.github.tranchis.xsd2thrift;

import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;

import static com.github.tranchis.xsd2thrift.TestHelper.*;

public class MultipleNamespaceTest {
    
    /*
     * For regeneration of expected data
     * public static void main(String args[]){
        try {
            //Main.main(new String[]{"--protobuf","--splitBySchema=true","--directory=src/test/resources/expectedproto/","--package=schemas.com.domain.common","contrib/ns-person.xsd"});
            //Main.main(new String[]{"--thrift","--splitBySchema=true","--directory=src/test/resources/expectedproto/","--package=schemas.com.domain.common","contrib/ns-person.xsd"});
        } catch (Exception e) {
            e.printStackTrace();
        }
    }*/

    @BeforeClass
    public static void generateProtobufForTests(){
        try {
        	new File("target/generated-proto").mkdirs();
        	Main.main(new String[]{"--protobuf","--splitBySchema=true","--directory=target/generated-proto/","--package=schemas.com.domain.common","src/test/resources/xsd/ns-person.xsd"});
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.toString());
        }
    }
    @Test
    public void shouldCreateANamespacedProtobufPersonFile(){
        compareExpectedAndGenerated(
                "src/test/resources/expectedproto/schemas_com_domain_person.proto",
                "target/generated-proto/schemas_com_domain_person.proto");
    }
    @Test
    public void shouldCreateANamespacedProtobufCommonFile(){
        compareExpectedAndGenerated(
                "src/test/resources/expectedproto/schemas_com_domain_common.proto",
                "target/generated-proto/schemas_com_domain_common.proto");
    }
    @Test
    public void shouldCreateANamespacedProtobufAddressFile(){
        compareExpectedAndGenerated(
                "src/test/resources/expectedproto/schemas_com_domain_address.proto",
                "target/generated-proto/schemas_com_domain_address.proto");
    }
}
