package com.iexec.core.utils.version;

import org.springframework.stereotype.Service;

@Service
public class VersionService {

    private String version = Version.PROJECT_VERSION;

    public String getVersion() {
        return version;
    }

    public boolean isSnapshot() {
        return version.contains("SNAPSHOT");
    }

}
