package brooklyn.location.blockstore;

import brooklyn.location.blockstore.api.MountedBlockDevice;
import brooklyn.location.blockstore.api.VolumeManager;
import brooklyn.location.blockstore.api.VolumeOptions;
import brooklyn.location.blockstore.ec2.Ec2VolumeManager;
import brooklyn.location.blockstore.openstack.OpenstackVolumeManager;
import brooklyn.location.blockstore.vclouddirector15.VcloudVolumeManager;
import com.google.common.base.Optional;
import com.google.common.reflect.TypeToken;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.location.jclouds.BasicJcloudsLocationCustomizer;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsLocationConfig;
import org.apache.brooklyn.location.jclouds.JcloudsMachineLocation;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.NodeMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static brooklyn.location.blockstore.VolumeManagers.*;

/**
 * Creates a location customizer that:
 * <ul>
 * <li>configures the EC2 availability zone</li>
 * <li>attaches the specified (existing) volume to the newly-provisioned EC2 instance</li>
 * <li>mounts the filesystem under the requested path</li>
 * </ul>
 *
 * Can be used for attaching additional disk on provisioning time for AWS.
 * Below is shown an example:
 *
 * <pre>
 *   provisioning.properties:
 *     customizers:
 *     - $brooklyn:object:
 *         type: brooklyn.location.blockstore.NewVolumeCustomizer
 *         brooklyn.config:
 *           volumes:
 *           - blockDevice:
 *               sizeInGb: 3
 *               deviceSuffix: 'h'
 *               deleteOnTermination: true
 *               tags:
 *                 brooklyn: br-example-test-1
 *             filesystem:
 *               mountPoint: /mount/brooklyn/h
 *               filesystemType: ext3
 * </pre>
 *
 * Important notice is that KVM is configured as the default hypervisor for OpenStack which means that the defined device name will be of type /dev/vd*.
 * This means that the device suffix must be set as the next letter in alphabetical order from the existing device names on the VM.
 */
public class NewVolumeCustomizer extends BasicJcloudsLocationCustomizer {
    // TODO write a rebind test

    private static final Logger LOG = LoggerFactory.getLogger(NewVolumeCustomizer.class);

    public static final ConfigKey<List<VolumeOptions>> VOLUMES = ConfigKeys.newConfigKey(
            new TypeToken<List<VolumeOptions>>() {},
            "volumes", "List of volumes to be attached");

    public NewVolumeCustomizer() {
    }

    public NewVolumeCustomizer(List<VolumeOptions> volumesOptions) {
        this.config().set(VOLUMES, volumesOptions);
    }

    public List<VolumeOptions> getVolumes() {
        return this.getConfig(VOLUMES);
    }

    protected VolumeManager getVolumeManager(JcloudsMachineLocation machine) {
        String provider;
        provider = getConfig(JcloudsLocationConfig.CLOUD_PROVIDER);
        if (provider == null) {
            provider = machine.getParent().getProvider();
        }

        switch (provider) {
            case AWS_EC2:
                return new Ec2VolumeManager();
            case OPENSTACK_NOVA:
                return new OpenstackVolumeManager();
            case VCLOUD_DIRECTOR:
                return new VcloudVolumeManager();
            default:
                throw new UnsupportedOperationException("Tried to attach volume for a cloud "
                        + provider + " which is not supported for adding disks. Caller entity " + getCallerContext(machine));
        }
    }

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

    // TODO move that to the VolumeManager?
    public MountedBlockDevice createAndAttachDisk(JcloudsMachineLocation machine, VolumeOptions volumeOptions) {
        if (volumeOptions.getFilesystemOptions() != null) {
            BlockDeviceOptions blockOptionsCopy = BlockDeviceOptions.copy(volumeOptions.getBlockDeviceOptions());
            Optional<NodeMetadata> node = machine.getOptionalNode();
            if (node.isPresent()) {
                blockOptionsCopy.zone(node.get().getLocation().getId());
            } else {
                LOG.warn("JcloudsNodeMetadata is not available for the MachineLocation. Using zone specified from a parameter.");
            }
            return getVolumeManager(machine).createAttachAndMountVolume(machine, blockOptionsCopy, volumeOptions.getFilesystemOptions());
        } else {
            throw new IllegalArgumentException("volume to be provisioned has null FileSystemOptions " + volumeOptions);
        }
    }
}
