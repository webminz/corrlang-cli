package io.corrlang.cli;

public class CorrLangVersions {

    public static final String[] KNOWN_VERSIONS = {
            "1.0-snapshot-alpha",
            "1.0-snapshot-pre-alpha"
    };

    public static final String CURRENT_VERSION = "1.0-snapshot-alpha";

    public static boolean isValid(String version) {
        return version != null && (version.equals(CURRENT_VERSION) || java.util.Arrays.asList(KNOWN_VERSIONS).contains(version));
    }
}
