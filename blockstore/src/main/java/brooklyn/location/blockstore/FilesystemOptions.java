package brooklyn.location.blockstore;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;

import com.google.common.base.MoreObjects;

public class FilesystemOptions {

    private String mountPoint;
    private String filesystemType;

    // For more convenient yaml input
    public static FilesystemOptions fromMap(Map<String, ?> map) {
        FilesystemOptions result = new FilesystemOptions();
        result.mountPoint = (String) map.get("mountPoint");
        result.filesystemType = (String) map.get("filesystemType");
        return result;
    }

    public FilesystemOptions() {
        // for reflection, e.g. using $brooklyn:object
    }
    
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
        return MoreObjects.toStringHelper(this)
                .add("mountPoint", mountPoint)
                .add("filesystemType", filesystemType)
                .toString();
    }
}
