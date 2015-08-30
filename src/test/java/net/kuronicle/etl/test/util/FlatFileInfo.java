package net.kuronicle.etl.test.util;

import lombok.Data;
import lombok.NonNull;

@Data
public class FlatFileInfo implements DatastoreInfo {

    @NonNull
    private String filePath;

    @NonNull
    private String charsetName;
}
