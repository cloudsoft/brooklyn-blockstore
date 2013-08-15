package brooklyn.location.blockstore.openstack;

import java.util.Map;

import org.jclouds.ContextBuilder;
import org.jclouds.encryption.bouncycastle.config.BouncyCastleCryptoModule;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.openstack.cinder.v1.CinderApi;
import org.jclouds.openstack.cinder.v1.domain.Volume;
import org.jclouds.openstack.cinder.v1.features.VolumeApi;
import org.jclouds.openstack.cinder.v1.options.CreateVolumeOptions;
import org.jclouds.openstack.cinder.v1.predicates.VolumePredicates;
import org.jclouds.openstack.nova.v2_0.NovaApi;
import org.jclouds.openstack.nova.v2_0.extensions.VolumeAttachmentApi;
import org.jclouds.sshj.config.SshjSshClientModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.blockstore.AbstractVolumeManager;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.location.jclouds.JcloudsSshMachineLocation;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Module;

/**
 * For managing volumes in OpenStack Cinder (e.g. Rackspace).
 */
public class OpenstackVolumeManager extends AbstractVolumeManager {

    private static final Logger LOG = LoggerFactory.getLogger(OpenstackVolumeManager.class);

    @Override
    public String createVolume(JcloudsLocation location, String availabilityZone, int size, Map<String,String> tags) {
        LOG.info("Creating volume: location={}; size={}", new Object[] {location, size});
        
        String zone = getZone(location.getProvider());
        if (availabilityZone != null && !availabilityZone.equals(zone)) {
            LOG.warn("Availability zone "+availabilityZone+" does not match "+zone+", expected for "+location.getProvider());
        }
        
        CinderApi cinderApi = getCinderApi(location);
        VolumeApi volumeApi = cinderApi.getVolumeApiForZone(zone);
        
        CreateVolumeOptions options = CreateVolumeOptions.Builder
                .name("brooklyn-something") // FIXME
                .metadata(tags != null ? tags : ImmutableMap.<String,String>of());
        
        Volume volume = volumeApi.create(size, options);
        return volume.getId();
    }
    
    @Override
    public void attachVolume(JcloudsSshMachineLocation machine, String volumeId, String volumeDeviceName) {
        LOG.info("Attaching volume: machine={}; volume={}; volumeDeviceName={}", new Object[] {machine, volumeId, volumeDeviceName});

        JcloudsLocation location = machine.getParent();
        String zone = getZone(location.getProvider());
        String instanceId = machine.getNode().getProviderId();
        CinderApi cinderApi = getCinderApi(location);
        NovaApi novaApi = getNovaApi(location);
        VolumeAttachmentApi attachmentApi = novaApi.getVolumeAttachmentExtensionForZone(zone).get();
        VolumeApi volumeApi = cinderApi.getVolumeApiForZone(zone);
        Volume volume = volumeApi.get(volumeId);
        
        attachmentApi.attachVolumeToServerAsDevice(volumeId, instanceId, volumeDeviceName);
        
        // Wait for the volume to become Attached (aka In Use) before moving on
        if (!VolumePredicates.awaitInUse(volumeApi).apply(volume)) {
            throw new IllegalStateException("Timeout on attaching volume: volumeId="+volumeId+"; machine="+machine);
        }
    }

    @Override
    public void detachVolume(JcloudsSshMachineLocation machine, final String volumeId, String volumeDeviceName) {
        LOG.info("Detaching volume: machine={}; volume={}; volumeDeviceName={}", new Object[] {machine, volumeId, volumeDeviceName});
        
        JcloudsLocation location = machine.getParent();
        String zone = getZone(location.getProvider());
        String instanceId = machine.getNode().getProviderId();
        CinderApi cinderApi = getCinderApi(location);
        NovaApi novaApi = getNovaApi(location);
        VolumeAttachmentApi attachmentApi = novaApi.getVolumeAttachmentExtensionForZone(zone).get();
        VolumeApi volumeApi = cinderApi.getVolumeApiForZone(zone);
        Volume volume = volumeApi.get(volumeId);
        
        attachmentApi.detachVolumeFromServer(volumeId, instanceId);
        
        // Wait for the volume to become Attached (aka In Use) before moving on
        if (!VolumePredicates.awaitAvailable(volumeApi).apply(volume)) {
            throw new IllegalStateException("Timeout on attaching volume: volumeId="+volumeId+"; machine="+machine);
        }
    }
    
    @Override
    public void deleteVolume(JcloudsLocation location, String volumeId) {
        LOG.info("Deleting volume: location={}; volume={}", new Object[] {location, volumeId});
        
        String zone = getZone(location.getProvider());
        CinderApi cinderApi = getCinderApi(location);
        VolumeApi volumeApi = cinderApi.getVolumeApiForZone(zone);
        
        volumeApi.delete(volumeId);
    }
    
    /**
     * Describes the given volume. Or returns null if it is not found.
     */
    public Volume describeVolume(JcloudsLocation location, String volumeId) {
        if (LOG.isDebugEnabled()) LOG.debug("Describing volume: location={}; volume={}", new Object[] {location, volumeId});
        
        String zone = getZone(location.getProvider());
        CinderApi cinderApi = getCinderApi(location);
        VolumeApi volumeApi = cinderApi.getVolumeApiForZone(zone);
        return volumeApi.get(volumeId);
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
