package brooklyn.location.blockstore;

import brooklyn.location.blockstore.api.VolumeOptions;
import com.google.common.reflect.TypeToken;

import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.location.jclouds.BasicJcloudsLocationCustomizer;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsLocationConfig;
import org.apache.brooklyn.location.jclouds.JcloudsMachineLocation;
import org.jclouds.compute.ComputeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 *
 * Attaches additional disk on provisioning time for AWS, Vcloud Director, OpenStack v2.
 *
 * Customizer actions:
 * <ul>
 * <li>configures the EC2 availability zone</li>
 * <li>attaches the specified (existing) volume to the newly-provisioned EC2 instance</li>
 * <li>mounts the filesystem under the requested path</li>
 * </ul>
 *
 * Example usage:
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

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(NewVolumeCustomizer.class);

    @SuppressWarnings("serial")
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

    public void setVolumes(List<VolumeOptions> volumes) {
        this.config().set(VOLUMES,volumes);
    }

    @Override
    public void customize(JcloudsLocation location, ComputeService computeService, JcloudsMachineLocation machine) {
        if (!getVolumes().isEmpty()) {
            createAndAttachDisks(machine);
        } else {
            throw new UnsupportedOperationException("There is no volume data populated to create and attach disk.");
        }
    }

    protected void createAndAttachDisks(JcloudsMachineLocation machine) {
        for (VolumeOptions volume : getVolumes()) {
            VolumeManagerFactory.getVolumeManager(machine, getConfig(JcloudsLocationConfig.CLOUD_PROVIDER)).createAndAttachDisk(machine, volume);
        }
    }
}
