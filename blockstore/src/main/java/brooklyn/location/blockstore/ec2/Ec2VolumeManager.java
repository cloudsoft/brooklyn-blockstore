package brooklyn.location.blockstore.ec2;

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsMachineLocation;
import org.apache.brooklyn.util.repeat.Repeater;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.ec2.EC2Api;
import org.jclouds.ec2.domain.Attachment;
import org.jclouds.ec2.domain.Volume;
import org.jclouds.ec2.features.ElasticBlockStoreApi;
import org.jclouds.ec2.features.TagApi;
import org.jclouds.ec2.options.DetachVolumeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import brooklyn.location.blockstore.AbstractVolumeManager;
import brooklyn.location.blockstore.BlockDeviceOptions;
import brooklyn.location.blockstore.Devices;
import brooklyn.location.blockstore.api.AttachedBlockDevice;
import brooklyn.location.blockstore.api.BlockDevice;

/**
 * For managing EBS volumes via EC2-compatible APIs.
 */
public class Ec2VolumeManager extends AbstractVolumeManager {

    private static final Logger LOG = LoggerFactory.getLogger(Ec2VolumeManager.class);

    private static final String DEVICE_PREFIX = "/dev/sd";
    private static final String OS_DEVICE_PREFIX = "/dev/xvd";

    @Override
    protected String getVolumeDeviceName(char deviceSuffix) {
        return DEVICE_PREFIX + deviceSuffix;
    }

    @Override
    protected String getOSDeviceName(char deviceSuffix) {    	
        String osDeviceName = OS_DEVICE_PREFIX + deviceSuffix; 
        // for root device 1 is needed
        if(deviceSuffix=='a')
            osDeviceName += 1;    	
            return osDeviceName;
    }

    @Override
    public BlockDevice createBlockDevice(JcloudsLocation location, BlockDeviceOptions options) {
        LOG.debug("Creating block device: location={}; options={}", location, options);

        ElasticBlockStoreApi ebsApi = getEbsApi(location);
        TagApi tagApi = getTagApi(location);

        Volume volume = ebsApi.createVolumeInAvailabilityZone(options.getZone(), options.getSizeInGb());
        if (options.hasTags()) {
            tagApi.applyToResources(options.getTags(), ImmutableList.of(volume.getId()));
        }

        BlockDevice device = Devices.newBlockDevice(location, volume.getId());
        waitForVolumeToBeAvailable(device);

        LOG.debug("Created block device: id="+device.getId()+"; location="+location);
        return device;
    }

    @Override
    public AttachedBlockDevice attachBlockDevice(JcloudsMachineLocation machine, BlockDevice blockDevice, BlockDeviceOptions options) {
        LOG.debug("Attaching block device: machine={}; device={}; options={}", new Object[]{machine, blockDevice, options});

        Optional<NodeMetadata> node = machine.getOptionalNode();
        if (!node.isPresent()) {
            throw new IllegalStateException("Cannot find jclouds-node for machine "+node);
        }

        JcloudsLocation location = machine.getParent();
        String region = getRegionName(location);
        ElasticBlockStoreApi ebsApi = getEbsApi(location);
        
        Attachment attachment = ebsApi.attachVolumeInRegion(region, blockDevice.getId(),
                node.get().getProviderId(), getVolumeDeviceName(options.getDeviceSuffix()));

        LOG.debug("Finished attaching block device: machine={}; device={}; options={}", new Object[]{machine, blockDevice, options});
        return blockDevice.attachedTo(machine, attachment.getDevice());
    }

    @Override
    public BlockDevice detachBlockDevice(AttachedBlockDevice attachedBlockDevice) {
        LOG.debug("Detaching block device: {}", attachedBlockDevice);

        Optional<NodeMetadata> node = attachedBlockDevice.getMachine().getOptionalNode();
        if (!node.isPresent()) {
            throw new IllegalStateException("Cannot find jclouds-node for machine "+node);
        }

        String region = getRegionName(attachedBlockDevice.getLocation());
        String instanceId = node.get().getProviderId();
        ElasticBlockStoreApi ebsApi = getEbsApi(attachedBlockDevice.getLocation());

        ebsApi.detachVolumeInRegion(region, attachedBlockDevice.getId(), true,
                DetachVolumeOptions.Builder
                        .fromDevice(attachedBlockDevice.getDeviceName())
                        .fromInstance(instanceId));
        Volume volume = waitForVolumeToBeAvailable(attachedBlockDevice);

        LOG.debug("Finished detaching block device: {}", attachedBlockDevice);
        return Devices.newBlockDevice(attachedBlockDevice.getLocation(), volume.getId());
    }

    @Override
    public void deleteBlockDevice(BlockDevice blockDevice) {
        LOG.debug("Deleting device: {}", blockDevice);

        String region = getRegionName(blockDevice.getLocation());
        ElasticBlockStoreApi ebsApi = getEbsApi(blockDevice.getLocation());
        ebsApi.deleteVolumeInRegion(region, blockDevice.getId());
    }

    /**
     * Describes the given volume. Or returns null if it is not found.
     */
    public Volume describeVolume(BlockDevice blockDevice) {
        LOG.debug("Describing device: {}", blockDevice);

        String region = getRegionName(blockDevice.getLocation());
        ElasticBlockStoreApi ebsApi = getEbsApi(blockDevice.getLocation());
        Set<Volume> volumes = ebsApi.describeVolumesInRegion(region, blockDevice.getId());
        return Iterables.getFirst(volumes, null);
    }
    
    // Naming convention is things like "us-east-1" or "us-east-1c"; strip off the availability zone suffix.
    // This is a hack to get around that jclouds accepts regions with the suffix for creating VMs, but not for ebsClient calls.
    private String getRegionName(JcloudsLocation location) {
        String region = location.getRegion();
        char lastchar = region.charAt(region.length() - 1);
        if (Character.isDigit(lastchar)) {
            return region; // normal region name; return as-is
        } else {
            return region.substring(0, region.length()-1); // remove single char representing availability zone
        }
    }

    protected EC2Api getApi(JcloudsLocation location) {
        return location.getComputeService().getContext().unwrapApi(EC2Api.class);
    }
    
    protected ElasticBlockStoreApi getEbsApi(JcloudsLocation location) {
        String region = getRegionName(location);
        EC2Api api = location.getComputeService().getContext().unwrapApi(EC2Api.class);
        if (region != null) {
            return api.getElasticBlockStoreApiForRegion(region).get();
        } else {
            return api.getElasticBlockStoreApi().get();
        }
    }

    protected TagApi getTagApi(JcloudsLocation location) {
        String region = getRegionName(location);
        EC2Api api = location.getComputeService().getContext().unwrapApi(EC2Api.class);
        if (region != null) {
            return api.getTagApiForRegion(region).get();
        } else {
            return api.getTagApi().get();
        }
    }

    /**
     * Waits for the status of the volume to be {@link Volume.Status#AVAILABLE available}.
     * If the status does not reach available after a delay, logs an error.
     * @return the last fetched volume
     */
    private Volume waitForVolumeToBeAvailable(final BlockDevice device) {
        final AtomicReference<Volume> lastVolume = new AtomicReference<Volume>();
        
        boolean available = Repeater.create("waiting for volume available:" + device)
                .every(1, TimeUnit.SECONDS)
                .limitTimeTo(60, TimeUnit.SECONDS)
                .until(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        Volume volume = describeVolume(device);
                        lastVolume.set(volume);
                        return volume.getStatus() == Volume.Status.AVAILABLE;
                    }})
                .run();

        if (!available) {
            LOG.error("Volume {} still not available. Last known was: {}; continuing", device, lastVolume.get());
        }

        return lastVolume.get();
    }

}
