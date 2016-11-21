package brooklyn.location.blockstore.openstack;

import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsMachineLocation;
import org.jclouds.openstack.cinder.v1.CinderApi;
import org.jclouds.openstack.cinder.v1.domain.Volume;
import org.jclouds.openstack.cinder.v1.features.VolumeApi;
import org.jclouds.openstack.cinder.v1.options.CreateVolumeOptions;
import org.jclouds.openstack.cinder.v1.predicates.VolumePredicates;
import org.jclouds.openstack.nova.v2_0.NovaApi;
import org.jclouds.openstack.nova.v2_0.domain.VolumeAttachment;
import org.jclouds.openstack.nova.v2_0.extensions.VolumeAttachmentApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.blockstore.AbstractVolumeManager;
import brooklyn.location.blockstore.BlockDeviceOptions;
import brooklyn.location.blockstore.Devices;
import brooklyn.location.blockstore.api.AttachedBlockDevice;
import brooklyn.location.blockstore.api.BlockDevice;

/**
 * For managing volumes in OpenStack Cinder (e.g. Rackspace).
 */
public abstract class AbstractOpenstackVolumeManager extends AbstractVolumeManager {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractOpenstackVolumeManager.class);
    private static final String DEVICE_PREFIX = "/dev/sd";
    private static final String OS_DEVICE_PREFIX = "/dev/vd";

    protected abstract CinderApi getCinderApi(JcloudsLocation location);

    protected abstract NovaApi getNovaApi(JcloudsLocation location);

    protected abstract String getRegion(JcloudsLocation location);

    protected abstract String getZone(JcloudsLocation location);

    @Override
    protected String getVolumeDeviceName(char deviceSuffix) {
        return DEVICE_PREFIX + deviceSuffix;
    }

    @Override
    protected String getOSDeviceName(char deviceSuffix) {
        return OS_DEVICE_PREFIX + deviceSuffix;
    }

    @Override
    public BlockDevice createBlockDevice(JcloudsLocation location, BlockDeviceOptions config) {
        LOG.info("Creating volume: location={}; config={}", location, config);

        String region = getRegion(location);
        String zone = getZone(location);
        String availabilityZone = config.getZone();
        if (availabilityZone != null && !availabilityZone.equals(zone)) {
            LOG.warn("Availability zone "+availabilityZone+" does not match "+zone+", expected for "+location.getProvider());
        }

        CinderApi cinderApi = getCinderApi(location);
        VolumeApi volumeApi = cinderApi.getVolumeApi(region);
        CreateVolumeOptions options = CreateVolumeOptions.Builder
                .name(getOrMakeName(location, config))
                .metadata(config.getTags());

        Volume volume = volumeApi.create(config.getSizeInGb(), options);
        return Devices.newBlockDevice(location, volume.getId());
    }

    @Override
    public AttachedBlockDevice attachBlockDevice(JcloudsMachineLocation machine, BlockDevice blockDevice, BlockDeviceOptions options) {
        LOG.info("Attaching volume: machine={}; device={}; options={}", new Object[] {machine, blockDevice, options});

        JcloudsLocation location = machine.getParent();
        String region = getRegion(location);
        String instanceId = machine.getNode().getProviderId();
        CinderApi cinderApi = getCinderApi(location);
        NovaApi novaApi = getNovaApi(location);
        VolumeAttachmentApi attachmentApi = novaApi.getVolumeAttachmentApi(region).get();
        VolumeApi volumeApi = cinderApi.getVolumeApi(region);
        Volume volume = volumeApi.get(blockDevice.getId());

        VolumeAttachment attachment = attachmentApi.attachVolumeToServerAsDevice(
                blockDevice.getId(), instanceId, getVolumeDeviceName(options.getDeviceSuffix()));

        // Wait for the volume to become Attached (aka In Use) before moving on
        if (!VolumePredicates.awaitInUse(volumeApi).apply(volume)) {
            throw new IllegalStateException("Timeout on attaching volume: device="+blockDevice+"; machine="+machine);
        }

        return blockDevice.attachedTo(machine, attachment.getDevice());
    }

    @Override
    public BlockDevice detachBlockDevice(AttachedBlockDevice attachedBlockDevice) {
        LOG.info("Detaching device: {}", attachedBlockDevice);

        JcloudsMachineLocation machine = attachedBlockDevice.getMachine();
        JcloudsLocation location = machine.getParent();
        String region = getRegion(location);
        String instanceId = machine.getNode().getProviderId();
        CinderApi cinderApi = getCinderApi(location);
        NovaApi novaApi = getNovaApi(location);
        VolumeAttachmentApi attachmentApi = novaApi.getVolumeAttachmentApi(region).get();
        VolumeApi volumeApi = cinderApi.getVolumeApi(region);
        Volume volume = volumeApi.get(attachedBlockDevice.getId());

        attachmentApi.detachVolumeFromServer(attachedBlockDevice.getId(), instanceId);

        // Wait for the volume to become Attached (aka In Use) before moving on
        if (!VolumePredicates.awaitAvailable(volumeApi).apply(volume)) {
            throw new IllegalStateException("Timeout on attaching volume: device="+attachedBlockDevice+"; machine="+machine);
        }

        return Devices.newBlockDevice(location, volume.getId());
    }

    @Override
    public void deleteBlockDevice(BlockDevice blockDevice) {
        LOG.info("Deleting device: {}", blockDevice);

        JcloudsLocation location = blockDevice.getLocation();
        String region = getRegion(location);
        CinderApi cinderApi = getCinderApi(location);
        VolumeApi volumeApi = cinderApi.getVolumeApi(region);

        volumeApi.delete(blockDevice.getId());
    }

    /**
     * Describes the given volume. Or returns null if it is not found.
     */
    public Volume describeVolume(BlockDevice device) {
        if (LOG.isDebugEnabled())
            LOG.debug("Describing device: {}", device);
        
        String region = getRegion(device.getLocation());
        CinderApi cinderApi = getCinderApi(device.getLocation());
        VolumeApi volumeApi = cinderApi.getVolumeApi(region);
        return volumeApi.get(device.getId());
    }
}
