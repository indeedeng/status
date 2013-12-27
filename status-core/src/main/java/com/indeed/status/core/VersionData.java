package com.indeed.status.core;

import javax.annotation.Nonnull;

/**
 * @author matts Moved to upper level from DBStatusManager
 */
public class VersionData {
    @Nonnull
    private final String rawVersionData;
    @Nonnull
    private final String projectName;
    @Nonnull
    private final String url;
    @Nonnull
    private final String revision;

    public VersionData(
            @Nonnull final String rawData,
            @Nonnull final String projectName,
            @Nonnull final String url,
            @Nonnull final String revision
    ) {
        this.rawVersionData = rawData;
        this.projectName = projectName;
        this.url = url;
        this.revision = revision;
    }

    @Nonnull
    public String getRawData() {
        return rawVersionData;
    }

    @Nonnull
    public String getProjectName() {
        return projectName;
    }

    @Nonnull
    public String getUrl() {
        return url;
    }

    @Nonnull
    public String getRevision() {
        return revision;
    }
}
