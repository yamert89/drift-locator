package com.github.yamert89.plugin

fun crossDatabaseComparisonError(sourceConnection: DatabaseConnection, targetConnection: DatabaseConnection): String? =
    if (sourceConnection.databaseType == targetConnection.databaseType) {
        null
    } else {
        "Cannot compare ${sourceConnection.databaseType.displayName} connection '${sourceConnection.name}' " +
            "with ${targetConnection.databaseType.displayName} connection '${targetConnection.name}'. " +
            "Please select two connections of the same database engine."
    }
