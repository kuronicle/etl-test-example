package net.kuronicle.etl.test.util;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.dbunit.IDatabaseTester;
import org.dbunit.JdbcDatabaseTester;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.excel.XlsDataSet;
import org.dbunit.operation.DatabaseOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import difflib.Delta;
import difflib.DiffUtils;
import difflib.Patch;

public class EtlTester {

    private static final Logger log = LoggerFactory.getLogger(EtlTester.class);

    protected static final String FILENAME_SEPARATOR = "_";

    protected static final String BEFORE_FILENAME_FLAG = "Before";

    protected static final String AFTER_FILENAME_FLAG = "After";

    private static final CharSequence ACTUAL_FILENAME_FALG = "Actual";

    private String testcaseRootDir = "./src/test/resources";

    private boolean saveAfterEvidence = true;

    private String evidenceRootDir = "./target/test/evidence";

    private String datastoreInfoFilePath;

    private Workbook datastoreInfoWorkbook;

    private Map<String, DatastoreInfo> datastoreMap = new HashMap<String, DatastoreInfo>();

    private Map<String, IDatabaseTester> databaseTesterMap = new HashMap<String, IDatabaseTester>();

    public EtlTester(String datastoreInfoFilePath) {
        this.datastoreInfoFilePath = datastoreInfoFilePath;
        try {
            datastoreInfoWorkbook = WorkbookFactory.create(new File(datastoreInfoFilePath));
        } catch (InvalidFormatException e) {
            throw new RuntimeException("Invalid datastore file. file=" + datastoreInfoFilePath, e);
        } catch (IOException e) {
            throw new RuntimeException("Error occerd when reading datastore file. file=" + datastoreInfoFilePath, e);
        }

    }

    public void prepareDatastores(final String ifId, final String testCaseId) throws Exception {
        log.debug("Start preparing datastores. ifId=" + ifId + ", testCaseId=" + testCaseId);

        String testcaseFileDir = FilenameUtils.concat(FilenameUtils.concat(testcaseRootDir, ifId), testCaseId);

        File ifIdDir = new File(testcaseFileDir);
        String[] datastoreFiles = ifIdDir.list(new FilenameFilter() {

            public boolean accept(File dir, String name) {
                return (name.startsWith(testCaseId + FILENAME_SEPARATOR + BEFORE_FILENAME_FLAG + FILENAME_SEPARATOR))
                        ? true : false;
            }
        });

        for (String datastoreFileName : datastoreFiles) {
            String datastoreName = datastoreFileName.substring(
                    (testCaseId + FILENAME_SEPARATOR + BEFORE_FILENAME_FLAG + FILENAME_SEPARATOR).length(),
                    datastoreFileName.lastIndexOf("."));

            DatastoreInfo datastore = getDatastoreInfo(datastoreName);

            if (datastore instanceof DatabaseInfo) {
                IDatabaseTester databaseTester = getDatabaseTester(datastoreName);
                databaseTester
                        .setDataSet(new XlsDataSet(new File(FilenameUtils.concat(testcaseFileDir, datastoreFileName))));
                databaseTester.setSetUpOperation(DatabaseOperation.CLEAN_INSERT);
                databaseTester.onSetup();
                log.debug(String.format("Clean and Insert data to database. schema=%s, dataFile=%s",
                        ((DatabaseInfo) datastore).getSchema(), datastoreFileName));
            } else if (datastore instanceof FlatFileInfo) {

                File srcFile = new File(FilenameUtils.concat(testcaseFileDir, datastoreFileName));

                File destFile = new File(((FlatFileInfo) datastore).getFilePath());

                if (destFile.exists()) {
                    destFile.delete();
                    log.debug("Delete file. file=" + destFile.getPath());
                }

                FileUtils.copyFile(srcFile, destFile);
                log.debug("Copy flat file before test. srcFile=" + srcFile.getPath() + ", destFile="
                        + destFile.getPath());
            }
        }

        log.debug("Finish preparing datastores. ifId=" + ifId + ", testCaseId=" + testCaseId);
    }

    private DatastoreInfo readDatastoreInfo(String datastoreName) {
        Sheet datastoreInfoSheet = datastoreInfoWorkbook.getSheet(datastoreName);

        if (datastoreInfoSheet == null) {
            throw new RuntimeException("Datastore sheet does not found. datastoreName=" + datastoreName
                    + ", datastoreFile=" + datastoreInfoFilePath);
        }

        String datastoreType = datastoreInfoSheet.getRow(0).getCell(1).getStringCellValue();

        if ("Database".equals(datastoreType)) {
            String driverClass = datastoreInfoSheet.getRow(1).getCell(1).getStringCellValue();
            String connectionUrl = datastoreInfoSheet.getRow(2).getCell(1).getStringCellValue();
            String username = datastoreInfoSheet.getRow(3).getCell(1).getStringCellValue();
            String password = datastoreInfoSheet.getRow(4).getCell(1).getStringCellValue();
            String schema = datastoreInfoSheet.getRow(5).getCell(1).getStringCellValue();
            if ("-".equals(schema)) {
                schema = null;
            }
            return new DatabaseInfo(driverClass, connectionUrl, username, password, schema);

        } else if ("FlatFile".equals(datastoreType)) {
            String filePath = datastoreInfoSheet.getRow(6).getCell(1).getStringCellValue();
            String charsetName = datastoreInfoSheet.getRow(7).getCell(1).getStringCellValue();
            return new FlatFileInfo(filePath, charsetName);
        }
        throw new RuntimeException("Illegal datastore type. datastoreType=" + datastoreType);
    }

    public void assertDatastores(final String ifId, final String testCaseId) throws Exception {

        log.debug("Start asserting datastores. ifId=" + ifId + ", testCaseId=" + testCaseId);

        String testcaseFileDir = FilenameUtils.concat(FilenameUtils.concat(testcaseRootDir, ifId), testCaseId);

        File ifIdDir = new File(testcaseFileDir);
        String[] datastoreFiles = ifIdDir.list(new FilenameFilter() {

            public boolean accept(File dir, String name) {
                return (name.startsWith(testCaseId + FILENAME_SEPARATOR + AFTER_FILENAME_FLAG + FILENAME_SEPARATOR))
                        ? true : false;
            }
        });

        String evicendeDirName = FilenameUtils.concat(FilenameUtils.concat(evidenceRootDir, ifId), testCaseId);
        if (saveAfterEvidence) {

            File dir = new File(evicendeDirName);
            if (!dir.exists()) {
                dir.mkdirs();
                log.info("Create evidence dir. dir=" + dir.getPath());
            }
        }

        for (String datastoreFileName : datastoreFiles) {
            String datastoreName = datastoreFileName.substring(
                    (testCaseId + FILENAME_SEPARATOR + AFTER_FILENAME_FLAG + FILENAME_SEPARATOR).length(),
                    datastoreFileName.lastIndexOf("."));

            DatastoreInfo datastore = getDatastoreInfo(datastoreName);

            if (datastore instanceof DatabaseInfo) {
                IDataSet expectedDataSet = new XlsDataSet(
                        new File(FilenameUtils.concat(testcaseFileDir, datastoreFileName)));

                IDatabaseTester databaseTester = getDatabaseTester(datastoreName);
                IDatabaseConnection connection = databaseTester.getConnection();
                IDataSet actualDataSet = connection.createDataSet();

                if (saveAfterEvidence) {
                    String evidenceFilePath = FilenameUtils
                            .concat(evicendeDirName,
                                    datastoreFileName.replace(AFTER_FILENAME_FLAG, ACTUAL_FILENAME_FALG))
                            .replace(".xlsx", ".xls");
                    XlsDataSet.write(actualDataSet, new FileOutputStream(new File(evidenceFilePath)));
                    log.info("Save evicence file. file=" + evidenceFilePath);
                }

                for (String tableName : expectedDataSet.getTableNames()) {
                    ITable expectedTable = expectedDataSet.getTable(tableName);
                    ITable actualTable = actualDataSet.getTable(tableName);
                    Assertion.assertEquals(expectedTable, actualTable);

                    log.info(String.format("Assert OK! schema=%s, table=%s, expectedFile=%s",
                            ((DatabaseInfo) datastore).getSchema(), tableName, datastoreFileName));
                }

            } else if (datastore instanceof FlatFileInfo) {
                FlatFileInfo flatFile = (FlatFileInfo) datastore;

                if (saveAfterEvidence) {
                    File acturalFile = new File(flatFile.getFilePath());
                    String evidenceFilePath = FilenameUtils.concat(evicendeDirName,
                            datastoreFileName.replace(AFTER_FILENAME_FLAG, ACTUAL_FILENAME_FALG));
                    File evidenceFile = new File(evidenceFilePath);
                    FileUtils.copyFile(acturalFile, evidenceFile);
                    log.info("Save evicence file. file=" + evidenceFilePath);
                }

                List<String> expectedLines = Files.readAllLines(
                        FileSystems.getDefault().getPath(FilenameUtils.concat(testcaseFileDir, datastoreFileName)),
                        Charset.forName(flatFile.getCharsetName()));
                List<String> actualLines = Files.readAllLines(FileSystems.getDefault().getPath(flatFile.getFilePath()),
                        Charset.forName(flatFile.getCharsetName()));

                Patch patch = DiffUtils.diff(expectedLines, actualLines);
                List<Delta> deltas = patch.getDeltas();

                if (deltas != null && deltas.size() > 0) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(System.getProperty("line.separator"));
                    for (Delta delta : deltas) {
                        sb.append("expected(line:" + delta.getOriginal().getPosition() + 1 + ")"
                                + System.getProperty("line.separator"));
                        for (Object expectedLine : delta.getOriginal().getLines()) {
                            sb.append("> " + expectedLine + System.getProperty("line.separator"));
                        }
                        sb.append("actural(line:" + delta.getRevised().getPosition() + 1 + ")"
                                + System.getProperty("line.separator"));
                        for (Object expectedLine : delta.getRevised().getLines()) {
                            sb.append("< " + expectedLine + System.getProperty("line.separator"));
                        }
                    }
                    String diffInfo = sb.toString();

                    log.info(String.format("Assert NG! fileName=%s, expectedFile=%s", flatFile.getFilePath(),
                            datastoreFileName));
                    log.info("Diffs=" + diffInfo);
                    fail(String.format("Files does not match. expected=%s, actural=%s, diff=%s",
                            FilenameUtils.concat(testcaseFileDir, datastoreFileName), flatFile.getFilePath(),
                            diffInfo));
                }

                log.info(String.format("Assert OK! fileName=%s, expectedFile=%s", flatFile.getFilePath(),
                        datastoreFileName));
            }

        }

        log.debug("Finish asserting datastores. ifId=" + ifId + ", testCaseId=" + testCaseId);
    }

    private IDatabaseTester getDatabaseTester(String datastoreName) throws ClassNotFoundException {

        IDatabaseTester databaseTester = databaseTesterMap.get(datastoreName);

        if (databaseTester != null) {
            return databaseTester;
        }

        DatabaseInfo database = (DatabaseInfo) getDatastoreInfo(datastoreName);

        databaseTester = new JdbcDatabaseTester(database.getDriverClass(), database.getConnectionUrl(),
                database.getUserName(), database.getUserPassword(), database.getSchema());

        databaseTesterMap.put(datastoreName, databaseTester);

        return databaseTester;
    }

    private DatastoreInfo getDatastoreInfo(String datastoreName) {

        DatastoreInfo datastore = datastoreMap.get(datastoreName);

        if (datastore != null) {
            return datastore;
        }

        datastore = readDatastoreInfo(datastoreName);
        datastoreMap.put(datastoreName, datastore);

        return datastore;
    }

}
