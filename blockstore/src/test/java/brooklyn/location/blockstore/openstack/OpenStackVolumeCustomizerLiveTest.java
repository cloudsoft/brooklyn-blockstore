package brooklyn.location.blockstore.openstack;

import java.util.Map;

import org.apache.brooklyn.util.collections.MutableMap;

import com.google.common.base.Optional;

import brooklyn.location.blockstore.AbstractVolumeCustomizerLiveTest;

/**
 * Requires {@code -da} (i.e. disable assertions) due to bug in jclouds openstack-nova, 
 * for not properly cloning template options.
 * 
 * One also needs to supply cloud credentials:
 * <pre>
 * {@code
 * -Dbrooklyn.location.jclouds.openstack-nova.endpoint="https://openstack.example.com:5000/v2.0"
 * -Dbrooklyn.location.jclouds.openstack-nova.identity="your-tenant:your-username"
 * -Dbrooklyn.location.jclouds.openstack-nova.credential="your-password"
 * -Dbrooklyn.location.jclouds.openstack-nova.auto-generate-keypairs=false
 * -Dbrooklyn.location.jclouds.openstack-nova.keyPair=your-keypair
 * -Dbrooklyn.location.jclouds.openstack-nova.loginUser.privateKeyFile=~/.ssh/your-keypair.pem
 * -Dbrooklyn.location.jclouds.openstack-nova.jclouds.keystone.credential-type=passwordCredentials
 * -Dbrooklyn.location.jclouds.openstack-cinder.identity="your-tenant:your-username"
 * -Dbrooklyn.location.jclouds.openstack-cinder.credential="your-password"
 * }
 * </pre>
 * 
 * Or alternatively these can be hard-coded in ~/.brooklyn/brooklyn.properties (i.e. without the 
 * {@code -D} in the lines above).
 */
public class OpenStackVolumeCustomizerLiveTest extends AbstractVolumeCustomizerLiveTest {

    @Override
    protected Optional<String> namedLocation() {
        return Optional.of(OpenStackLocationConfig.NAMED_LOCATION);
    }

    @Override
    protected Map<?, ?> additionalObtainArgs() throws Exception {
        return MutableMap.builder()
                .putAll(super.additionalObtainArgs())
                .putAll(new OpenStackLocationConfig().getConfigMap())
                .build();
    }

    @Override
    protected int getVolumeSize() {
        return 3;
    }

    @Override
    protected char getDefaultDeviceSuffix() {
        return 'b';
    }
}
