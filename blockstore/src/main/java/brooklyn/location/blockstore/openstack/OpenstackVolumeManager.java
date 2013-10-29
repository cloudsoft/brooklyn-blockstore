package brooklyn.location.blockstore.openstack;

import org.jclouds.ContextBuilder;
import org.jclouds.encryption.bouncycastle.config.BouncyCastleCryptoModule;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.openstack.cinder.v1.CinderApi;
import org.jclouds.openstack.cinder.v1.domain.Volume;
import org.jclouds.openstack.cinder.v1.features.VolumeApi;
import org.jclouds.openstack.cinder.v1.options.CreateVolumeOptions;
import org.jclouds.openstack.cinder.v1.predicates.VolumePredicates;
import org.jclouds.openstack.nova.v2_0.NovaApi;
import org.jclouds.openstack.nova.v2_0.domain.VolumeAttachment;
import org.jclouds.openstack.nova.v2_0.extensions.VolumeAttachmentApi;
import org.jclouds.sshj.config.SshjSshClientModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Module;

import brooklyn.location.blockstore.AbstractVolumeManager;
import brooklyn.location.blockstore.BlockDeviceOptions;
import brooklyn.location.blockstore.Devices;
import brooklyn.location.blockstore.api.AttachedBlockDevice;
import brooklyn.location.blockstore.api.BlockDevice;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.location.jclouds.JcloudsSshMachineLocation;

/**
 * For managing volumes in OpenStack Cinder (e.g. Rackspace).
 */
public class OpenstackVolumeManager extends AbstractVolumeManager {

    private static final Logger LOG = LoggerFactory.getLogger(OpenstackVolumeManager.class);
    private static final String DEVICE_PREFIX = "/dev/sd";
    private static final String OS_DEVICE_PREFIX = "/dev/xvd";

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

        String zone = getZone(location.getProvider());
        String availabilityZone = config.getZone();
        if (availabilityZone != null && !availabilityZone.equals(zone)) {
            LOG.warn("Availability zone "+availabilityZone+" does not match "+zone+", expected for "+location.getProvider());
        }

        CinderApi cinderApi = getCinderApi(location);
        VolumeApi volumeApi = cinderApi.getVolumeApiForZone(zone);
        CreateVolumeOptions options = CreateVolumeOptions.Builder
                .name("brooklyn-something") // FIXME
                .metadata(config.getTags());

        Volume volume = volumeApi.create(config.getSizeInGb(), options);
        return Devices.newBlockDevice(location, volume.getId());
    }

    @Override
    public AttachedBlockDevice attachBlockDevice(JcloudsSshMachineLocation machine, BlockDevice blockDevice, BlockDeviceOptions options) {
        LOG.info("Attaching volume: machine={}; device={}; options={}", new Object[] {machine, blockDevice, options});

        JcloudsLocation location = machine.getParent();
        String zone = getZone(location.getProvider());
        String instanceId = machine.getNode().getProviderId();
        CinderApi cinderApi = getCinderApi(location);
        NovaApi novaApi = getNovaApi(location);
        VolumeAttachmentApi attachmentApi = novaApi.getVolumeAttachmentExtensionForZone(zone).get();
        VolumeApi volumeApi = cinderApi.getVolumeApiForZone(zone);
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

        JcloudsSshMachineLocation machine = attachedBlockDevice.getMachine();
        JcloudsLocation location = machine.getParent();
        String zone = getZone(location.getProvider());
        String instanceId = machine.getNode().getProviderId();
        CinderApi cinderApi = getCinderApi(location);
        NovaApi novaApi = getNovaApi(location);
        VolumeAttachmentApi attachmentApi = novaApi.getVolumeAttachmentExtensionForZone(zone).get();
        VolumeApi volumeApi = cinderApi.getVolumeApiForZone(zone);
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
        String zone = getZone(location.getProvider());
        CinderApi cinderApi = getCinderApi(location);
        VolumeApi volumeApi = cinderApi.getVolumeApiForZone(zone);

        volumeApi.delete(blockDevice.getId());
    }

    /**
     * Describes the given volume. Or returns null if it is not found.
     */
    public Volume describeVolume(BlockDevice device) {
        if (LOG.isDebugEnabled())
            LOG.debug("Describing device: {}", device);
        
        String zone = getZone(device.getLocation().getProvider());
        CinderApi cinderApi = getCinderApi(device.getLocation());
        VolumeApi volumeApi = cinderApi.getVolumeApiForZone(zone);
        return volumeApi.get(device.getId());
    }
    
    // FIXME Will this create a new jclouds context every time, which will never be closed?
    // Should we be getting the CinderApi from location.getComputeService().getContext() somehow?
    private CinderApi getCinderApi(JcloudsLocation location) {
        String provider = "rackspace-cloudblockstorage-uk";
        String identity = location.getIdentity();
        String credential = location.getCredential();
        Iterable<Module> modules = ImmutableSet.<Module> of(
                new SshjSshClientModule(), 
                new SLF4JLoggingModule(),
                new BouncyCastleCryptoModule());

        return ContextBuilder.newBuilder(provider)
              .credentials(identity, credential)
              .modules(modules)
              .buildApi(CinderApi.class);
    }

    // FIXME reusing jclouds context? See comment on getCinderApi.
    private NovaApi getNovaApi(JcloudsLocation location) {
        String provider = "rackspace-cloudservers-uk";
        String identity = location.getIdentity();
        String credential = location.getCredential();
        Iterable<Module> modules = ImmutableSet.<Module> of(
                new SshjSshClientModule(), 
                new SLF4JLoggingModule(),
                new BouncyCastleCryptoModule());

        return ContextBuilder.newBuilder(provider)
                .credentials(identity, credential)
                .modules(modules)
                .buildApi(NovaApi.class);
    }

    private String getZone(String provider) {
        if (provider.matches("rackspace-.*-uk") || provider.matches("cloudservers-uk")) {
            return "LON";
        } else if (provider.matches("rackspace-.*-us") || provider.matches("cloudservers-us")) {
            return "DFW";
        } else {
            throw new IllegalStateException("Cannot determine zone for provider "+provider);
        }
    }

}
