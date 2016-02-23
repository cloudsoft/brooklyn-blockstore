package brooklyn.location.blockstore.ec2;

import java.util.List;
import java.util.Map;

import org.apache.brooklyn.location.jclouds.BasicJcloudsLocationCustomizer;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsSshMachineLocation;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.TemplateBuilder;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

import brooklyn.location.blockstore.BlockDeviceOptions;
import brooklyn.location.blockstore.FilesystemOptions;

/**
 * Creates a location customizer that:
 * <ul>
 * <li>configures the EC2 availability zone</li>
 * <li>attaches the specified (existing) volume to the newly-provisioned EC2 instance</li>
 * <li>mounts the filesystem under the requested path</li>
 * </ul>
 */
public class Ec2NewVolumeCustomizer extends BasicJcloudsLocationCustomizer {

    private static final Ec2VolumeManager ebsVolumeManager = new Ec2VolumeManager();

//    // For simpler yaml usage
//    private List<Map<String,?>> volumes;
//
    private Map<BlockDeviceOptions, FilesystemOptions> _volumes;
    
    public Ec2NewVolumeCustomizer(Map<BlockDeviceOptions, FilesystemOptions> volumes) {
        this._volumes = volumes;
    }
    
    public Ec2NewVolumeCustomizer() {
        // for reflective creation (e.g. with $brooklyn:object)
    }
    
    @SuppressWarnings("unchecked")
    public void setVolumes(List<Map<String, ?>> val) {
        _volumes = Maps.newLinkedHashMap();
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
            
            _volumes.put(bdOptions, fsOptions);
        }
    }
    
    @VisibleForTesting
    public Map<BlockDeviceOptions, FilesystemOptions> getParsedVolumes() {
        return _volumes;
    }
    
    public void customize(JcloudsLocation location, ComputeService computeService, TemplateBuilder templateBuilder) {
        BlockDeviceOptions blockOptions = Iterables.getFirst(_volumes.keySet(), null);
        if (blockOptions != null && blockOptions.getZone() != null) {
            templateBuilder.locationId(blockOptions.getZone());
        }
    }

    public void customize(JcloudsLocation location, ComputeService computeService, JcloudsSshMachineLocation machine) {
        for (Map.Entry<BlockDeviceOptions, FilesystemOptions> entry : _volumes.entrySet()) {
            BlockDeviceOptions blockDeviceOptions = entry.getKey();
            FilesystemOptions filesystemOptions = entry.getValue();
            
            BlockDeviceOptions blockDeviceOptionsCopy = BlockDeviceOptions.copy(blockDeviceOptions);
            Optional<NodeMetadata> node = machine.getOptionalNode();
            if (node.isPresent()) {
                blockDeviceOptionsCopy.zone(node.get().getLocation().getId());
            }

            ebsVolumeManager.createAttachAndMountVolume(machine, blockDeviceOptionsCopy, filesystemOptions);
        }
    }
}
