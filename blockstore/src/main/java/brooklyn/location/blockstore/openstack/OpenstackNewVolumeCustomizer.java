package brooklyn.location.blockstore.openstack;

import brooklyn.location.blockstore.BlockDeviceOptions;
import brooklyn.location.blockstore.FilesystemOptions;
import brooklyn.location.blockstore.api.MountedBlockDevice;
import brooklyn.location.blockstore.api.VolumeManager;
import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import org.apache.brooklyn.location.jclouds.BasicJcloudsLocationCustomizer;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsMachineLocation;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.NodeMetadata;

import java.util.List;
import java.util.Map;

/**
 * Customizer that can be used for attaching additional disk on provisioning time for OpenStack.<br><br>
 *
 * Below is shown an example:
 *
 * <pre>
 *   provisioning.properties:
 *     customizers:
 *     - $brooklyn:object:
 *         type: brooklyn.location.blockstore.openstack.OpenstackNewVolumeCustomizer
 *         object.fields:
 *           volumes:
 *           - blockDevice:
 *               sizeInGb: 3
 *               deviceSuffix: 'b'
 *               deleteOnTermination: false
 *               tags:
 *                 brooklyn: br-example-val-test-1
 *            filesystem:
 *              mountPoint: /mount/brooklyn/b
 *              filesystemType: ext3
 * </pre>
 *
 * Important notice is that KVM is configured as the default hypervisor for OpenStack which means that the defined device name will be of type /dev/vd*.
 * This means that the device suffix must be set as the next letter in alphabetical order from the existing device names on the VM.
 */

public class OpenstackNewVolumeCustomizer extends BasicJcloudsLocationCustomizer {

    private static final OpenstackVolumeManager openstackVolumeManager = new OpenstackVolumeManager();

    private Map<BlockDeviceOptions, FilesystemOptions> volumes;

    protected MountedBlockDevice mountedBlockDevice;

    public OpenstackNewVolumeCustomizer(Map<BlockDeviceOptions, FilesystemOptions> volumes) {
        this.volumes = volumes;
        this.mountedBlockDevice = null;
    }

    public OpenstackNewVolumeCustomizer() {
        // for reflective creation (e.g. with $brooklyn:object)
    }

    protected VolumeManager getVolumeManager() {
        return new OpenstackVolumeManager();
    }

    public MountedBlockDevice getMountedBlockDevice() {
        return mountedBlockDevice;
    }

    @SuppressWarnings("unchecked")
    public void setVolumes(List<Map<String, ?>> val) {
        volumes = Maps.newLinkedHashMap();
        if (val == null) {
            return;
        }
        for (Map<String,?> volume : val) {
            BlockDeviceOptions bdOptions;
            FilesystemOptions fsOptions;
            Object blockDevice = volume.get("blockDevice");
            if (blockDevice instanceof Map<?,?>) {
                bdOptions = BlockDeviceOptions.fromMap((Map<String, ?>) blockDevice);
            } else if (blockDevice instanceof BlockDeviceOptions) {
                bdOptions = (BlockDeviceOptions) blockDevice;
            } else {
                throw new IllegalArgumentException("Invalid blockDevice "+blockDevice+ (blockDevice == null ? "" : " of type "+blockDevice.getClass().getName()));
            }

            Object filesystem = volume.get("filesystem");
            if (filesystem instanceof Map<?,?>) {
                fsOptions = FilesystemOptions.fromMap((Map<String, ?>) filesystem);
            } else if (filesystem instanceof FilesystemOptions) {
                fsOptions = (FilesystemOptions) filesystem;
            } else {
                throw new IllegalArgumentException("Invalid filesystem "+filesystem+ (filesystem == null ? "" : " of type "+filesystem.getClass().getName()));
            }

            volumes.put(bdOptions, fsOptions);
        }
    }

    public void customize(JcloudsLocation location, ComputeService computeService, JcloudsMachineLocation machine) {
        createAndAttachDisks(machine);
    }

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
                mountedBlockDevice = openstackVolumeManager.createAttachAndMountVolume(machine, blockOptionsCopy, filesystemOptions);
            }
        }
    }
}
