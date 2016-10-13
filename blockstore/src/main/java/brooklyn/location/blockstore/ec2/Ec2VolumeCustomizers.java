package brooklyn.location.blockstore.ec2;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Map;

import brooklyn.location.blockstore.api.MountedBlockDevice;
import org.apache.brooklyn.location.jclouds.BasicJcloudsLocationCustomizer;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsLocationCustomizer;
import org.apache.brooklyn.location.jclouds.JcloudsMachineLocation;
import org.apache.brooklyn.util.collections.MutableMap;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.compute.options.TemplateOptions;
import org.jclouds.ec2.compute.options.EC2TemplateOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

import brooklyn.location.blockstore.BlockDeviceOptions;
import brooklyn.location.blockstore.FilesystemOptions;
import brooklyn.location.blockstore.api.AttachedBlockDevice;
import brooklyn.location.blockstore.api.BlockDevice;

/**
 * Customization hooks to ensure that any EC2 instances provisioned via a corresponding jclouds location become associated
 * with an EBS volume (either an existing volume, specified by ID, or newly created).
 */
public class Ec2VolumeCustomizers {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(Ec2VolumeCustomizers.class);

    private static final Ec2VolumeManager ebsVolumeManager = new Ec2VolumeManager();

    // Prevent construction: helper class.
    private Ec2VolumeCustomizers() {
    }

    /**
     * Returns a location customizer that:
     * <ul>
     * <li>configures the EC2 availability zone</li>
     * <li>creates a new EBS volume of the requested size in the given availability zone</li>
     * <li>attaches the new volume to the newly-provisioned EC2 instance</li>
     * <li>formats the new volume with the requested filesystem</li>
     * <li>mounts the filesystem under the requested path</li>
     * </ul>
     */
    public static JcloudsLocationCustomizer withNewVolume(final BlockDeviceOptions blockOptions, final FilesystemOptions filesystemOptions) {
        return new NewVolumeCustomizer(MutableMap.of(checkNotNull(blockOptions, "blockOptions"), filesystemOptions));
    }

    // TODO what mount point etc?
    public static JcloudsLocationCustomizer withNewVolumes(List<Integer> capacities) {
        Map<BlockDeviceOptions, FilesystemOptions> volumes = Maps.newLinkedHashMap();
        for (int i = 0; i < capacities.size(); i++) {
            Integer capacity = checkNotNull(capacities.get(i), "capacity(%s)", i);
            char deviceSuffix = (char)('h'+i);
            BlockDeviceOptions blockOptions = new BlockDeviceOptions()
                    .deviceSuffix(deviceSuffix)
                    .sizeInGb(capacity)
                    .deleteOnTermination(true);
            FilesystemOptions filesystemOptions = new FilesystemOptions("/mnt/brooklyn/"+deviceSuffix);
            volumes.put(blockOptions, filesystemOptions);
        }
        return new NewVolumeCustomizer(volumes);
    }

    public static class NewVolumeCustomizer extends BasicJcloudsLocationCustomizer {

        private Map<BlockDeviceOptions, FilesystemOptions> volumes;

        private MountedBlockDevice mountedBlockDevice;

        public NewVolumeCustomizer(Map<BlockDeviceOptions, FilesystemOptions> volumes) {
            this.volumes = volumes;
            this.mountedBlockDevice = null;
        }

        public Map<BlockDeviceOptions, FilesystemOptions> getVolumes() {
            return volumes;
        }

        public MountedBlockDevice getMountedBlockDevice() {
            return mountedBlockDevice;
        }

        @Override
        public void customize(JcloudsLocation location, ComputeService computeService, TemplateBuilder templateBuilder) {
            BlockDeviceOptions blockOptions = Iterables.getFirst(volumes.keySet(), null);
            if (blockOptions != null && blockOptions.getZone() != null) {
                templateBuilder.locationId(blockOptions.getZone());
            }
        }

        @Override
        public void customize(JcloudsLocation location, ComputeService computeService, TemplateOptions templateOptions) {
            for (BlockDeviceOptions blockOptions : volumes.keySet()) {
                ((EC2TemplateOptions) templateOptions).mapNewVolumeToDeviceName(
                        ebsVolumeManager.getVolumeDeviceName(blockOptions.getDeviceSuffix()), blockOptions.getSizeInGb().intValue(), blockOptions.deleteOnTermination());
            }
        }

        @Override
        public void customize(JcloudsLocation location, ComputeService computeService, JcloudsMachineLocation machine) {
            for (Map.Entry<BlockDeviceOptions, FilesystemOptions> entry : volumes.entrySet()) {
                BlockDeviceOptions blockOptions = entry.getKey();
                FilesystemOptions filesystemOptions = entry.getValue();
                if (filesystemOptions != null) {
                    BlockDeviceOptions blockOptionsCopy = BlockDeviceOptions.copy(blockOptions);
                    Optional<NodeMetadata> node = machine.getOptionalNode();
                    if (node.isPresent()) {
                        blockOptionsCopy.zone(node.get().getLocation().getId());
                    }
                    mountedBlockDevice = ebsVolumeManager.createAttachAndMountVolume(machine, blockOptionsCopy, filesystemOptions);
                }
            }
        }
    }

    // TODO: Either the JavaDoc or the implementation is incorrect. The implementation makes no attempt to attach volumes.
    /**
     * A location customizer type that:
     * <ul>
     * <li>configures the EC2 availability zone</li>
     * <li>obtains a new EBS volume from the specified snapshot in the given availability zone</li>
     * <li>attaches the new volume to the newly-provisioned EC2 instance</li>
     * <li>mounts the filesystem under the requested path</li>
     * </ul>
     */

    public static class ExistingSnapshot extends BasicJcloudsLocationCustomizer {

        private AttachedBlockDevice attachedDevice;

        private BlockDeviceOptions blockOptions;

        private FilesystemOptions filesystemOptions;


        private MountedBlockDevice mountedBlockDevice;


        public ExistingSnapshot(AttachedBlockDevice attachedDevice, BlockDeviceOptions blockOptions, FilesystemOptions filesystemOptions) {
            this.attachedDevice = attachedDevice;
            this.blockOptions = blockOptions;
            this.filesystemOptions = filesystemOptions;
            this.mountedBlockDevice = null;
        }

        public MountedBlockDevice getMountedBlockDevice() {
            return mountedBlockDevice;
        }

        @Override
        public void customize(JcloudsLocation location, ComputeService computeService, TemplateBuilder templateBuilder) {
            templateBuilder.locationId(blockOptions.getZone());
        }

        @Override
        public void customize(JcloudsLocation location, ComputeService computeService, TemplateOptions templateOptions) {
            ((EC2TemplateOptions) templateOptions).mapEBSSnapshotToDeviceName(
                    ebsVolumeManager.getVolumeDeviceName(blockOptions.getDeviceSuffix()),
                    attachedDevice.getId(),
                    blockOptions.getSizeInGb().intValue(),
                    blockOptions.deleteOnTermination());
        }

        @Override
        public void customize(JcloudsLocation location, ComputeService computeService, JcloudsMachineLocation machine) {
            mountedBlockDevice = ebsVolumeManager.mountFilesystem(attachedDevice, filesystemOptions);
        }
    }

    /**
     * A location customizer type that:
     * <ul>
     * <li>configures the EC2 availability zone</li>
     * <li>attaches the specified (existing) volume to the newly-provisioned EC2 instance</li>
     * <li>mounts the filesystem under the requested path</li>
     * </ul>
     */
    public static class ExistingVolumeCustomizer extends BasicJcloudsLocationCustomizer {

        private BlockDevice device;

        private BlockDeviceOptions blockOptions;

        private FilesystemOptions filesystemOptions;

        private String volumeDeviceName;

        private String osDeviceName;

        private String mountPoint;

        private String region;

        private String availabilityZone;

        private String volumeId;

        private MountedBlockDevice mountedBlockDevice;

        public MountedBlockDevice getMountedBlockDevice() {
            return mountedBlockDevice;
        }

        public ExistingVolumeCustomizer(BlockDevice device, BlockDeviceOptions blockOptions,
                                           FilesystemOptions filesystemOptions, String volumeDeviceName, String osDeviceName,
                                           String mountPoint, String region, String availabilityZone, String volumeId) {
            this.device = device;
            this.blockOptions = blockOptions;
            this.filesystemOptions = filesystemOptions;
            this.volumeDeviceName = volumeDeviceName;
            this.osDeviceName = osDeviceName;
            this.mountPoint = mountPoint;
            this.region = region;
            this.availabilityZone = availabilityZone;
            this.volumeId = volumeId;
            this.mountedBlockDevice = null;
        }

        @Override
        public void customize(JcloudsLocation location, ComputeService computeService, TemplateBuilder templateBuilder) {
            templateBuilder.locationId(blockOptions.getZone());
        }

        @Override
        public void customize(JcloudsLocation location, ComputeService computeService, JcloudsMachineLocation machine) {
            mountedBlockDevice = ebsVolumeManager.attachAndMountVolume(machine, device, blockOptions, filesystemOptions);
        }
    }
}
