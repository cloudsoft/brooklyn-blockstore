package brooklyn.location.blockstore;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Objects;

public class FilesystemOptions {

    private final String mountPoint;
    private final String filesystemType;

    /**
     * Uses the given mountPoint and "auto" for filesystemType.
     */
    public FilesystemOptions(String mountPoint) {
        this(mountPoint, "auto");
    }

    public FilesystemOptions(String mountPoint, String filesystemType) {
        this.filesystemType = checkNotNull(filesystemType, "filesystemType");
        this.mountPoint = checkNotNull(mountPoint, "mountPoint");
    }

    public String getMountPoint() {
        return mountPoint;
    }

    public String getFilesystemType() {
        return filesystemType;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("mountPoint", mountPoint)
                .add("filesystemType", filesystemType)
                .toString();
    }
}
