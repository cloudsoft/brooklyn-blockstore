package brooklyn.location.blockstore;

import brooklyn.location.blockstore.api.MountedBlockDevice;
import brooklyn.location.blockstore.api.VolumeManager;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.location.jclouds.BasicJcloudsLocationCustomizer;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsMachineLocation;
import org.apache.brooklyn.util.collections.MutableMap;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.NodeMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public abstract class NewVolumeCustomizer extends BasicJcloudsLocationCustomizer {

    private static final Logger LOG = LoggerFactory.getLogger(NewVolumeCustomizer.class);

    public static final ConfigKey<List<Map<?, ?>>> VOLUMES = ConfigKeys.newConfigKey(
            new TypeToken<List<Map<?, ?>>>() {},
            "volumes", "List of volumes to be attached");

    /**
     * Used only for checking results from customization
     */
    protected transient MountedBlockDevice mountedBlockDevice;

    protected NewVolumeCustomizer() {
    }

    public NewVolumeCustomizer(Map<BlockDeviceOptions, FilesystemOptions> volume) {
        setVolumes(ImmutableList.<Map<?, ?>>of(volume));
    }

    public List<Map<?, ?>> getVolumes() {
        try {
            return this.getConfig(VOLUMES);
        } catch (Exception e) {
            // We don't want to break execution here but prefer to throw more informative exception
            // for empty volumes when this method is invoked
            LOG.warn("Trying to get missing config " + VOLUMES);
            return ImmutableList.of();
        }
    }

    public MountedBlockDevice getMountedBlockDevice() {
        return mountedBlockDevice;
    }

    protected abstract VolumeManager getVolumeManager();

    public void setVolumes(List<Map<?, ?>> volumes) {
        if (volumes == null) {
            return;
        }
        this.config().set(VOLUMES,volumes);
    }

    public static Map<BlockDeviceOptions, FilesystemOptions> transformMapToVolume(Map<?, ?> map) {
        if (map.keySet().iterator().next() instanceof BlockDeviceOptions && map.values().iterator().next() instanceof FilesystemOptions) {
            return (Map<BlockDeviceOptions, FilesystemOptions>) map;
        }

        if (map.containsKey("blockDevice") && map.containsKey("filesystem")) {
            BlockDeviceOptions blockDeviceOptions = (map.get("blockDevice") instanceof BlockDeviceOptions) ?
                    (BlockDeviceOptions) map.get("blockDevice") : BlockDeviceOptions.fromMap((Map<String, ?>) map.get("blockDevice"));
            if (blockDeviceOptions.getSizeInGb() == 0) {
                throw new IllegalArgumentException("Tried to create volume with not appropriate parameters "
                        + map + "; \"blockDevice\" should contain value for \"sizeInGb\"");
            }
            FilesystemOptions filesystemOptions = (map.get("filesystem") instanceof FilesystemOptions) ?
                    (FilesystemOptions) map.get("filesystem") : FilesystemOptions.fromMap((Map<String, ?>) map.get("filesystem"));
            Map<BlockDeviceOptions, FilesystemOptions> locationCustomizerFields = MutableMap.of(
                    blockDeviceOptions,
                    filesystemOptions
            );

            return locationCustomizerFields;
        } else {
            throw new IllegalArgumentException("Tried to create volume with not appropriate parameters. " +
                    "Expected parameter of type { \"blockDevice\": {}, \"filesystem\": {} }, but found " + map);
        }
    }

    public void customize(JcloudsLocation location, ComputeService computeService, JcloudsMachineLocation machine) {
        if (!getVolumes().isEmpty()) {
            createAndAttachDisks(machine);
        } else {
            throw new UnsupportedOperationException("There is no volume data populated to create and attach disk.");
        }
    }

    protected void createAndAttachDisks(JcloudsMachineLocation machine) {
        for (Map<?, ?> volume : getVolumes()) {
            createAndAttachDisk(machine, transformMapToVolume(volume));
        }
    }

    protected void createAndAttachDisk(JcloudsMachineLocation machine, Map<BlockDeviceOptions, FilesystemOptions> volume) {
        for (Map.Entry<BlockDeviceOptions, FilesystemOptions> entry : volume.entrySet()) {
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
