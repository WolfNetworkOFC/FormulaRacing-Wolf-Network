package dev.EfraGroup.formulaRacing.Utils;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Manages plugin version info read from version.properties (generated at build time).
 * Format: major.minor.patch.build  (e.g. 2.0.0.42)
 */
public class VersionManager {

    private final String version;
    private final String buildNumber;
    private final String fullVersion;

    public VersionManager(FormulaRacing plugin) {
        Properties props = new Properties();
        try (InputStream is = plugin.getResource("version.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (IOException e) {
            // ignore
        }
        this.version = props.getProperty("version", "0.2");
        this.buildNumber = props.getProperty("build", "0");
        this.fullVersion = version + "." + buildNumber;
    }

    public String getVersion() {
        return version;
    }

    public String getBuildNumber() {
        return buildNumber;
    }

    /** Full version string: "2.0.0.42" */
    public String getFullVersion() {
        return fullVersion;
    }
}
