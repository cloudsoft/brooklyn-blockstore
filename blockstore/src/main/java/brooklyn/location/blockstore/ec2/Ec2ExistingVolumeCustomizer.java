package brooklyn.location.blockstore.ec2;

import static com.google.common.base.Preconditions.checkState;

import java.util.Map;

import org.apache.brooklyn.location.jclouds.BasicJcloudsLocationCustomizer;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsSshMachineLocation;
import org.apache.brooklyn.util.text.Strings;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.TemplateBuilder;

import brooklyn.location.blockstore.BlockDeviceOptions;
import brooklyn.location.blockstore.Devices;
import brooklyn.location.blockstore.FilesystemOptions;
import brooklyn.location.blockstore.api.BlockDevice;

/**
 * Creates a location customizer that:
 * <ul>
 * <li>configures the EC2 availability zone</li>
 * <li>attaches the specified (existing) volume to the newly-provisioned EC2 instance</li>
 * <li>mounts the filesystem under the requested path</li>
 * </ul>
 */
public class Ec2ExistingVolumeCustomizer extends BasicJcloudsLocationCustomizer {

    private static final Ec2VolumeManager ebsVolumeManager = new Ec2VolumeManager();

    protected BlockDevice device;
    protected BlockDeviceOptions blockOptions;
    protected FilesystemOptions filesystemOptions;
    protected String volumeId;

    public Ec2ExistingVolumeCustomizer(final BlockDevice device, BlockDeviceOptions blockOptions, 
            FilesystemOptions filesystemOptions, String volumeId) {
        this.device = device;
        this.filesystemOptions = filesystemOptions;
        this.volumeId = volumeId;
    }
    
    public Ec2ExistingVolumeCustomizer() {
        // for reflective creation (e.g. with $brooklyn:object)
    }
    
    public void setBlockDevice(BlockDevice val) {
        device = val;
    }
    
    @SuppressWarnings("unchecked")
    public void setBlockOptions(Object val) {
        if (val == null) {
            blockOptions = null;
        } else if (val instanceof BlockDeviceOptions) {
            blockOptions = (BlockDeviceOptions) val;
        } else if (val instanceof Map<?,?>) {
            blockOptions = BlockDeviceOptions.fromMap((Map<String, ?>) val);
        } else {
            throw new IllegalArgumentException("Invalid blockOptions: "+val);
        }
    }
    
    @SuppressWarnings("unchecked")
    public void setFilesystemOptions(Object val) {
        if (val == null) {
            filesystemOptions = null;
        } else if (val instanceof FilesystemOptions) {
            filesystemOptions = (FilesystemOptions) val;
        } else if (val instanceof Map<?,?>) {
            filesystemOptions = FilesystemOptions.fromMap((Map<String, ?>) val);
        } else {
            throw new IllegalArgumentException("Invalid filesystemOptions: "+val);
        }
    }
    
    public void setVolumeId(String val) {
        volumeId = val;
    }

    public void customize(JcloudsLocation location, ComputeService computeService, TemplateBuilder templateBuilder) {
        checkState(device != null ^ volumeId != null, "device=%s and volumeId=%s, but exactly one must be set", device, volumeId);
        // TODO validate that zone is in the same region; or if we were given an AZ then it's the same one
        if (Strings.isNonBlank(blockOptions.getZone())) {
            templateBuilder.locationId(blockOptions.getZone());
        }
    }

    public void customize(JcloudsLocation location, ComputeService computeService, JcloudsSshMachineLocation machine) {
        if (device == null) {
            device = Devices.newBlockDevice(location, volumeId);
        }
        ebsVolumeManager.attachAndMountVolume(machine, device, blockOptions, filesystemOptions);
    }
}
