package net.kuronicle.etl.test.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;

@Data
@AllArgsConstructor
public class DatabaseInfo implements DatastoreInfo {

    @NonNull
    private String driverClass;

    @NonNull
    private String connectionUrl;

    @NonNull
    private String userName;

    @NonNull
    private String userPassword;

    private String schema;

}
