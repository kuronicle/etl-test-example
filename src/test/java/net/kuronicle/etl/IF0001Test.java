package net.kuronicle.etl;

import net.kuronicle.etl.test.util.EtlTester;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

public class IF0001Test {

    private String ifId = "IF0001";

    private EtlTester etlTester = new EtlTester("src/test/resources/DatastoreInfo.xlsx");
    
    @Rule
    public TestName testName = new TestName();

    @Test
    public void UT0001() {
    }

    @Test
    public void UT0002() {

    }

    @Before
    public void setUp() throws Exception {
        String testCaseId = testName.getMethodName();
        
        etlTester.prepareDatastores(ifId, testCaseId);
        
    }

    @After
    public void tearDown() throws Exception {
        String testCaseId = testName.getMethodName();
                
        etlTester.assertDatastores(ifId, testCaseId);
        
        etlTester.cleanDatastores(ifId, testCaseId);
    }
}
