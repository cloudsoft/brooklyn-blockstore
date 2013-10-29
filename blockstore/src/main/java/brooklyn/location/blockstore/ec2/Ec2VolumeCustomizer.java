package brooklyn.location.blockstore.ec2;

import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.compute.options.TemplateOptions;
import org.jclouds.ec2.compute.options.EC2TemplateOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.blockstore.BlockDeviceOptions;
import brooklyn.location.blockstore.FilesystemOptions;
import brooklyn.location.blockstore.api.AttachedBlockDevice;
import brooklyn.location.blockstore.api.BlockDevice;
import brooklyn.location.jclouds.BasicJcloudsLocationCustomizer;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.location.jclouds.JcloudsLocationCustomizer;
import brooklyn.location.jclouds.JcloudsSshMachineLocation;

/**
 * Customization hooks to ensure that any EC2 instances provisioned via a corresponding jclouds location become associated
 * with an EBS volume (either an existing volume, specified by ID, or newly created).
 */
public class Ec2VolumeCustomizer {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(Ec2VolumeCustomizer.class);

    private static final Ec2VolumeManager ebsVolumeManager = new Ec2VolumeManager();

    // Prevent construction: helper class.
    private Ec2VolumeCustomizer() {
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

        return new BasicJcloudsLocationCustomizer() {
            public void customize(JcloudsLocation location, ComputeService computeService, TemplateBuilder templateBuilder) {
                templateBuilder.locationId(blockOptions.getZone());
            }

            public void customize(JcloudsLocation location, ComputeService computeService, TemplateOptions templateOptions) {
                ((EC2TemplateOptions) templateOptions).mapNewVolumeToDeviceName(
                        ebsVolumeManager.getVolumeDeviceName(blockOptions.getDeviceSuffix()), blockOptions.getSizeInGb(), blockOptions.deleteOnTermination());
            }

            public void customize(JcloudsLocation location, ComputeService computeService, JcloudsSshMachineLocation machine) {
                ebsVolumeManager.createAttachAndMountVolume(machine, blockOptions, filesystemOptions);
            }
        };
    }

    // TODO: Either the JavaDoc or the implementation is incorrect. The implementation makes no attempt to attach volumes.
    /**
     * Returns a location customizer that:
     * <ul>
     * <li>configures the EC2 availability zone</li>
     * <li>obtains a new EBS volume from the specified snapshot in the given availability zone</li>
     * <li>attaches the new volume to the newly-provisioned EC2 instance</li>
     * <li>mounts the filesystem under the requested path</li>
     * </ul>
     */
    public static JcloudsLocationCustomizer withExistingSnapshot(final AttachedBlockDevice attachedDevice,
            final BlockDeviceOptions blockOptions, final FilesystemOptions filesystemOptions) {

        return new BasicJcloudsLocationCustomizer() {
            public void customize(JcloudsLocation location, ComputeService computeService, TemplateBuilder templateBuilder) {
                templateBuilder.locationId(blockOptions.getZone());
            }

            public void customize(JcloudsLocation location, ComputeService computeService, TemplateOptions templateOptions) {
                ((EC2TemplateOptions) templateOptions).mapEBSSnapshotToDeviceName(
                        ebsVolumeManager.getVolumeDeviceName(blockOptions.getDeviceSuffix()),
                        attachedDevice.getId(),
                        blockOptions.getSizeInGb(),
                        blockOptions.deleteOnTermination());
            }

            public void customize(JcloudsLocation location, ComputeService computeService, JcloudsSshMachineLocation machine) {
                ebsVolumeManager.mountFilesystem(attachedDevice, filesystemOptions);
            }
        };
    }

    /**
     * Returns a location customizer that:
     * <ul>
     * <li>configures the EC2 availability zone</li>
     * <li>attaches the specified (existing) volume to the newly-provisioned EC2 instance</li>
     * <li>mounts the filesystem under the requested path</li>
     * </ul>
     */
    public static JcloudsLocationCustomizer withExistingVolume(final BlockDevice device,
            final BlockDeviceOptions blockOptions, final FilesystemOptions filesystemOptions
            ,
            final String volumeDeviceName, final String osDeviceName, final String mountPoint,
            final String region, final String availabilityZone, final String volumeId) {

        return new BasicJcloudsLocationCustomizer() {
            public void customize(JcloudsLocation location, ComputeService computeService, TemplateBuilder templateBuilder) {
                templateBuilder.locationId(blockOptions.getZone());
            }

            public void customize(JcloudsLocation location, ComputeService computeService, JcloudsSshMachineLocation machine) {
                ebsVolumeManager.attachAndMountVolume(machine, device, blockOptions, filesystemOptions);
            }
        };
    }
}
