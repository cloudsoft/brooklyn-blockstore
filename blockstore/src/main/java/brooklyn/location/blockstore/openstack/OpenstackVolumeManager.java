package brooklyn.location.blockstore.openstack;

import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.jclouds.ContextBuilder;
import org.jclouds.encryption.bouncycastle.config.BouncyCastleCryptoModule;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.openstack.cinder.v1.CinderApi;
import org.jclouds.openstack.nova.v2_0.NovaApi;
import org.jclouds.sshj.config.SshjSshClientModule;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Module;

/**
 * For managing volumes in OpenStack Cinder (e.g. Rackspace).
 */
public class OpenstackVolumeManager extends AbstractOpenstackVolumeManager {

    // FIXME Will this create a new jclouds context every time, which will never be closed?
    // Should we be getting the CinderApi from location.getComputeService().getContext() somehow?
    @Override
    protected CinderApi getCinderApi(JcloudsLocation location) {
        String provider = "openstack-cinder";
        String endpoint = location.getEndpoint();
        String identity = location.getIdentity();
        String credential = location.getCredential();
        Iterable<Module> modules = ImmutableSet.<Module> of(
                new SshjSshClientModule(), 
                new SLF4JLoggingModule(),
                new BouncyCastleCryptoModule());

        return ContextBuilder.newBuilder(provider)
                .endpoint(endpoint)
                .credentials(identity, credential)
                .modules(modules)
                .buildApi(CinderApi.class);
    }

    // FIXME reusing jclouds context? See comment on getCinderApi.
    @Override
    protected NovaApi getNovaApi(JcloudsLocation location) {
        String provider = "openstack-nova";
        String endpoint = location.getEndpoint();
        String identity = location.getIdentity();
        String credential = location.getCredential();
        Iterable<Module> modules = ImmutableSet.<Module> of(
                new SshjSshClientModule(), 
                new SLF4JLoggingModule(),
                new BouncyCastleCryptoModule());

        return ContextBuilder.newBuilder(provider)
                .endpoint(endpoint)
                .credentials(identity, credential)
                .modules(modules)
                .buildApi(NovaApi.class);
    }

    @Override
    protected String getRegion(JcloudsLocation location) {
        return location.getRegion();
    }

    @Override
    protected String getZone(JcloudsLocation location) {
        // FIXME Untested code; what should this be?
        return null;
    }
}
