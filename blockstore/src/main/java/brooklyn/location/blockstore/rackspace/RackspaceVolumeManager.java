package brooklyn.location.blockstore.rackspace;

import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.jclouds.ContextBuilder;
import org.jclouds.encryption.bouncycastle.config.BouncyCastleCryptoModule;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.openstack.cinder.v1.CinderApi;
import org.jclouds.openstack.nova.v2_0.NovaApi;
import org.jclouds.sshj.config.SshjSshClientModule;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Module;

import brooklyn.location.blockstore.openstack.AbstractOpenstackVolumeManager;

/**
 * For managing volumes in OpenStack Cinder (e.g. Rackspace).
 */
public class RackspaceVolumeManager extends AbstractOpenstackVolumeManager {

    // FIXME Will this create a new jclouds context every time, which will never be closed?
    // Should we be getting the CinderApi from location.getComputeService().getContext() somehow?
    @Override
    protected CinderApi getCinderApi(JcloudsLocation location) {
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
    @Override
    protected NovaApi getNovaApi(JcloudsLocation location) {
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

    @Override
    protected String getZone(JcloudsLocation location) {
        String provider = location.getProvider();
        if (provider.matches("rackspace-.*-uk") || provider.matches("cloudservers-uk")) {
            return "LON";
        } else if (provider.matches("rackspace-.*-us") || provider.matches("cloudservers-us")) {
            return "DFW";
        } else {
            throw new IllegalStateException("Cannot determine zone for provider "+provider);
        }
    }

}
