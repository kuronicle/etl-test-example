package net.kuronicle.etl;

import java.nio.charset.Charset;

import org.h2.engine.Constants;
import org.h2.tools.RunScript;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import net.kuronicle.etl.test.util.EtlTester;

public class IF0001Test {

    private String ifId = "IF0001";

    private EtlTester etlTester = new EtlTester("src/test/resources/DatastoreInfo.xlsx");

    @Rule
    public TestName testName = new TestName();

    @Test
    public void UT0001() {
    }

    /**
     * Test for failure.
     */
    @Test
    public void UT0002() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        // set up H2 database schema for test.
        String url = "jdbc:h2:mem:db001;DB_CLOSE_DELAY=-1";
        String user = "sa";
        String password = "";
        String fileName = "src/test/resources/IF0001/create_test_tables_DB001.sql";
        Charset charset = Constants.UTF8;
        RunScript.execute(url, user, password, fileName, charset, false);

        url = "jdbc:h2:mem:db002;DB_CLOSE_DELAY=-1";
        user = "sa";
        password = "";
        fileName = "src/test/resources/IF0001/create_test_tables_DB002.sql";
        charset = Constants.UTF8;
        RunScript.execute(url, user, password, fileName, charset, false);
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
    }
}
