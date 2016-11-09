package brooklyn.location.blockstore.openstack;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsSshMachineLocation;
import org.apache.brooklyn.util.text.Identifiers;
import org.jclouds.openstack.cinder.v1.domain.Volume;
import org.testng.annotations.Test;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

import brooklyn.location.blockstore.AbstractVolumeManagerLiveTest;
import brooklyn.location.blockstore.api.BlockDevice;

@Test
public class OpenStackVolumeManagerLiveTest extends AbstractVolumeManagerLiveTest {

    public static final String PROVIDER = "openstack-nova";
    public static final String ENDPOINT = "https://cloudsoft2-lon.openstack.blueboxgrid.com:5000/v2.0/";
    public static final String LOCATION_SPEC = PROVIDER+":"+ENDPOINT;
    public static final String NAMED_LOCATION = "OpenStackVolumeManagerLiveTest" + Identifiers.makeRandomId(4);
    public static final String IMAGE_NAME_REGEX = ".*CentOS 6.*";

    @Override
    protected String getProvider() {
        return PROVIDER;
    }

    @Override
    protected void addBrooklynProperties(BrooklynProperties props) {
        // re-using rackspace credentials, but pointing at it as a raw OpenStack nova endpoint
        Object identity = props.get(BROOKLYN_PROPERTIES_JCLOUDS_PREFIX+"openstack-cinder.identity");
        Object credential = props.get(BROOKLYN_PROPERTIES_JCLOUDS_PREFIX+"openstack-cinder.credential");
        props.put("brooklyn.location.named."+NAMED_LOCATION, LOCATION_SPEC);
        props.put("brooklyn.location.named."+NAMED_LOCATION+".identity", identity);
        props.put("brooklyn.location.named."+NAMED_LOCATION+".credential", credential);
        props.put("brooklyn.location.named."+NAMED_LOCATION+".region", "RegionOne");
    }

    @Override
    protected JcloudsLocation createJcloudsLocation() {
        return (JcloudsLocation) ctx.getLocationRegistry().getLocationManaged("named:"+NAMED_LOCATION);
    }
    
    @Override
    protected int getVolumeSize() {
        return 100; // min on rackspace is 100
    }

    @Override
    protected String getDefaultAvailabilityZone() {
        return null;
    }

    @Override
    protected void assertVolumeAvailable(BlockDevice device) {
        Volume volume = ((AbstractOpenstackVolumeManager)volumeManager).describeVolume(device);
        assertNotNull(volume);
        assertEquals(volume.getStatus(), Volume.Status.AVAILABLE);
    }

    @Override
    protected Optional<JcloudsSshMachineLocation> rebindJcloudsMachine() {
        return Optional.absent();
    }
    
    @Override
    protected JcloudsSshMachineLocation createJcloudsMachine() throws Exception {
        // TODO Wanted to specify hardware id, but this failed; and wanted to force no imageId (in case specified in brooklyn.properties)
        return (JcloudsSshMachineLocation) jcloudsLocation.obtain(ImmutableMap.builder()
                .put(JcloudsLocation.IMAGE_NAME_REGEX, IMAGE_NAME_REGEX)
                .build());
    }
}
