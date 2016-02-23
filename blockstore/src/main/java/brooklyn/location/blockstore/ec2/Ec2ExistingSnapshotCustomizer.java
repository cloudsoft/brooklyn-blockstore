package brooklyn.location.blockstore.ec2;

import java.util.Map;

import org.apache.brooklyn.location.jclouds.BasicJcloudsLocationCustomizer;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsSshMachineLocation;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.compute.options.TemplateOptions;
import org.jclouds.ec2.compute.options.EC2TemplateOptions;

import brooklyn.location.blockstore.BlockDeviceOptions;
import brooklyn.location.blockstore.FilesystemOptions;
import brooklyn.location.blockstore.api.AttachedBlockDevice;

// TODO: Either the JavaDoc or the implementation is incorrect. The implementation makes no attempt to attach volumes.
/**
 * Creates a location customizer that:
 * <ul>
 * <li>configures the EC2 availability zone</li>
 * <li>obtains a new EBS volume from the specified snapshot in the given availability zone</li>
 * <li>attaches the new volume to the newly-provisioned EC2 instance</li>
 * <li>mounts the filesystem under the requested path</li>
 * </ul>
 */
public class Ec2ExistingSnapshotCustomizer extends BasicJcloudsLocationCustomizer {

    private static final Ec2VolumeManager ebsVolumeManager = new Ec2VolumeManager();

    private AttachedBlockDevice attachedDevice;
    protected BlockDeviceOptions blockOptions;
    protected FilesystemOptions filesystemOptions;

    public Ec2ExistingSnapshotCustomizer(AttachedBlockDevice attachedDevice,
            BlockDeviceOptions blockOptions, FilesystemOptions filesystemOptions) {
        this.attachedDevice = attachedDevice;
        this.blockOptions = blockOptions;
        this.filesystemOptions = filesystemOptions;
    }
    
    public Ec2ExistingSnapshotCustomizer() {
        // for reflective creation (e.g. with $brooklyn:object)
    }
    
    public void setAttachedDevice(AttachedBlockDevice val) {
        attachedDevice = val;
    }
    
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
}
