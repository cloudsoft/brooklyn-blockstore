package brooklyn.location.blockstore;

import brooklyn.location.blockstore.api.MountedBlockDevice;
import brooklyn.location.blockstore.api.VolumeManager;
import com.google.common.base.Optional;
import org.apache.brooklyn.location.jclouds.BasicJcloudsLocationCustomizer;
import org.apache.brooklyn.location.jclouds.JcloudsMachineLocation;
import org.jclouds.compute.domain.NodeMetadata;

import java.util.Map;

public abstract class NewVolumeCustomizer extends BasicJcloudsLocationCustomizer {
    protected Map<BlockDeviceOptions, FilesystemOptions> volumes;

    protected MountedBlockDevice mountedBlockDevice;

    public abstract Map<BlockDeviceOptions, FilesystemOptions> getVolumes();

    public abstract MountedBlockDevice getMountedBlockDevice();

    protected abstract VolumeManager getVolumeManager();

    protected void createAndAttachDisks(JcloudsMachineLocation machine) {
        for (Map.Entry<BlockDeviceOptions, FilesystemOptions> entry : volumes.entrySet()) {
            BlockDeviceOptions blockOptions = entry.getKey();
            FilesystemOptions filesystemOptions = entry.getValue();
            if (filesystemOptions != null) {
                BlockDeviceOptions blockOptionsCopy = BlockDeviceOptions.copy(blockOptions);
                Optional<NodeMetadata> node = machine.getOptionalNode();
                if (node.isPresent()) {
                    blockOptionsCopy.zone(node.get().getLocation().getId());
                }
                mountedBlockDevice = getVolumeManager().createAttachAndMountVolume(machine, blockOptionsCopy, filesystemOptions);
            }
        }
    }
}
