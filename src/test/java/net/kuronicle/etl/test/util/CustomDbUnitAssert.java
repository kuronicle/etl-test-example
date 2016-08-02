package net.kuronicle.etl.test.util;

import org.dbunit.assertion.DbUnitAssert;

public class CustomDbUnitAssert extends DbUnitAssert {

    private static final String SIGN_IGNORE = "[ignore]";

    protected boolean skipCompare(String columnName, Object expectedValue, Object actualValue) {

        if (expectedValue instanceof String) {
            if (SIGN_IGNORE.equals(expectedValue)) {
                return true;
            }
        }

        return false;
    }
}