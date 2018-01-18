package brooklyn.location.blockstore;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;

import com.google.common.base.MoreObjects;
import com.google.common.base.Optional;

public class FilesystemOptions {

    private String mountPoint;
    private String filesystemType;
    private String volumeLabel;

    // For more convenient yaml input
    public static FilesystemOptions fromMap(Map<String, ?> map) {
        FilesystemOptions result = new FilesystemOptions();
        result.mountPoint = (String) map.get("mountPoint");
        result.filesystemType = (String) map.get("filesystemType");
        result.volumeLabel = (String) map.get("volumeLabel");
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
        this(mountPoint, filesystemType, "");
    }

    public FilesystemOptions(String mountPoint, String filesystemType, String volumeLabel) {
        this.filesystemType = checkNotNull(filesystemType, "filesystemType");
        this.mountPoint = checkNotNull(mountPoint, "mountPoint");
        this.volumeLabel = Optional.fromNullable(volumeLabel).or("");
    }

    /*
    For Linux machines, this will be the mount point, e.g. /data. For Windows systems, this is the drive letter, e.g. "G". A colon should not be included
     */
    public String getMountPoint() {
        return mountPoint;
    }

    /*
    For Linux machines, this will be the file system type, e.g. ext3. For Windows system, this is the `-FileSystem` parameter passed to the `Format-Volume` cmdlet, e.g. NTFS
     */
    public String getFilesystemType() {
        return filesystemType;
    }

    /*
    Windows only. The volume label to be assigned to the volume, e.g. "MyDataDrive"
     */
    public String getVolumeLabel() {
        return volumeLabel;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("mountPoint", mountPoint)
                .add("filesystemType", filesystemType)
                .add("volumeLabel", volumeLabel)
                .toString();
    }
}
