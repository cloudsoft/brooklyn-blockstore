package brooklyn.location.blockstore;

import brooklyn.location.blockstore.api.MountedBlockDevice;
import brooklyn.location.blockstore.api.VolumeManager;
import brooklyn.location.blockstore.api.VolumeOptions;
import com.google.common.base.Optional;
import com.google.common.reflect.TypeToken;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.location.jclouds.BasicJcloudsLocationCustomizer;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsMachineLocation;
import org.apache.brooklyn.util.collections.MutableList;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.NodeMetadata;

import java.util.List;

public abstract class NewVolumeCustomizer extends BasicJcloudsLocationCustomizer {

    public static final ConfigKey<List<VolumeOptions>> VOLUMES = ConfigKeys.newConfigKey(
            new TypeToken<List<VolumeOptions>>() {},
            "volumes", "List of volumes to be attached");

    /**
     * Used only for checking results from customization
     */
    protected transient List<MountedBlockDevice> mountedBlockDeviceList;

    protected NewVolumeCustomizer() {
        mountedBlockDeviceList = MutableList.of();
    }

    public NewVolumeCustomizer(List<VolumeOptions> volumesOptions) {
        this.config().set(VOLUMES, volumesOptions);
        mountedBlockDeviceList = MutableList.of();
    }

    public List<VolumeOptions> getVolumes() {
        return this.getConfig(VOLUMES);
    }

    public List<MountedBlockDevice> getMountedBlockDeviceList() {
        return mountedBlockDeviceList;
    }

    protected abstract VolumeManager getVolumeManager();

    public void setVolumes(List<VolumeOptions> volumes) {
        this.config().set(VOLUMES,volumes);
    }

    public void customize(JcloudsLocation location, ComputeService computeService, JcloudsMachineLocation machine) {
        if (!getVolumes().isEmpty()) {
            createAndAttachDisks(machine);
        } else {
            throw new UnsupportedOperationException("There is no volume data populated to create and attach disk.");
        }
    }

    protected void createAndAttachDisks(JcloudsMachineLocation machine) {
        for (VolumeOptions volume : getVolumes()) {
            createAndAttachDisk(machine, volume);
        }
    }

    protected void createAndAttachDisk(JcloudsMachineLocation machine, VolumeOptions volumeOptions) {
        if (volumeOptions.getFilesystemOptions() != null) {
            BlockDeviceOptions blockOptionsCopy = BlockDeviceOptions.copy(volumeOptions.getBlockDeviceOptions());
            Optional<NodeMetadata> node = machine.getOptionalNode();
            if (node.isPresent()) {
                blockOptionsCopy.zone(node.get().getLocation().getId());
            }
            mountedBlockDeviceList.add(getVolumeManager().createAttachAndMountVolume(machine, blockOptionsCopy, volumeOptions.getFilesystemOptions()));
        }
    }
}
