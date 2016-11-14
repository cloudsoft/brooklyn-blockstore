package brooklyn.location.blockstore;

import brooklyn.location.blockstore.api.MountedBlockDevice;
import brooklyn.location.blockstore.api.VolumeManager;
import com.google.common.base.Optional;
import org.apache.brooklyn.location.jclouds.BasicJcloudsLocationCustomizer;
import org.apache.brooklyn.location.jclouds.JcloudsMachineLocation;
import org.jclouds.compute.domain.NodeMetadata;

import java.util.Map;

public abstract class NewVolumeCustomizer extends BasicJcloudsLocationCustomizer {

    public NewVolumeCustomizer(Map<BlockDeviceOptions, FilesystemOptions> volumes) {
        this.volumes = volumes;
        this.mountedBlockDevice = null;
    }

    protected Map<BlockDeviceOptions, FilesystemOptions> volumes;

    protected MountedBlockDevice mountedBlockDevice;

    public Map<BlockDeviceOptions, FilesystemOptions> getVolumes() {
        return volumes;
    }

    public MountedBlockDevice getMountedBlockDevice() {
        return mountedBlockDevice;
    }

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
