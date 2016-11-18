package brooklyn.location.blockstore.api;

import brooklyn.location.blockstore.BlockDeviceOptions;
import brooklyn.location.blockstore.FilesystemOptions;
import org.apache.brooklyn.util.core.flags.TypeCoercions;

import java.util.Map;

public class VolumeOptions {
    private BlockDeviceOptions blockDeviceOptions;
    private FilesystemOptions filesystemOptions;

    public static VolumeOptions fromString(String map) {
        return fromMap(TypeCoercions.coerce(map, Map.class));
    }

    public static VolumeOptions fromMap(Map<String, Map<String, ?>> map) {
        if (map.containsKey("blockDevice") && (map.containsKey("filesystem") || map.containsKey("fileSystem"))) {
            BlockDeviceOptions blockDeviceOptions = BlockDeviceOptions.fromMap(map.get("blockDevice"));
            FilesystemOptions filesystemOptions = FilesystemOptions.fromMap(map.get("filesystem") != null ? map.get("filesystem") : map.get("fileSystem"));
            VolumeOptions volumeOptions = new VolumeOptions(blockDeviceOptions, filesystemOptions);
            return volumeOptions;
        } else {
            throw new IllegalArgumentException("Tried to create volume with not appropriate parameters. " +
                    "Expected parameter of type { \"blockDevice\": {}, \"filesystem\": {} }, but found " + map);
        }
    }

    public VolumeOptions(BlockDeviceOptions blockDeviceOptions, FilesystemOptions filesystemOptions) {
        this.blockDeviceOptions = blockDeviceOptions;
        this.filesystemOptions = filesystemOptions;
    }

    public BlockDeviceOptions getBlockDeviceOptions() {
        return blockDeviceOptions;
    }

    public FilesystemOptions getFilesystemOptions() {
        return filesystemOptions;
    }

    @Override
    public String toString() {
        return "{blockDeviceOptions: " + blockDeviceOptions + ", filesystemOptions: " + filesystemOptions + "}";
    }
}
