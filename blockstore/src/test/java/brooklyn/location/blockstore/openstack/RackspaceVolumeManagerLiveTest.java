package brooklyn.location.blockstore.openstack;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import org.jclouds.openstack.cinder.v1.domain.Volume;
import org.testng.annotations.Test;

import brooklyn.location.blockstore.AbstractVolumeManagerLiveTest;
import brooklyn.location.blockstore.api.BlockDevice;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.location.jclouds.JcloudsSshMachineLocation;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

@Test
public class RackspaceVolumeManagerLiveTest extends AbstractVolumeManagerLiveTest {

    public static final String PROVIDER = "rackspace-cloudservers-uk";
    public static final String LOCATION_SPEC = PROVIDER;
    public static final String TINY_HARDWARE_ID = "1";
    public static final String SMALL_HARDWARE_ID = "2";
    public static final String IMAGE_NAME_REGEX = ".*CentOS 6.*";

    @Override
    protected String getProvider() {
        return PROVIDER;
    }

    @Override
    protected JcloudsLocation createJcloudsLocation() {
        return (JcloudsLocation) ctx.getLocationRegistry().resolve(LOCATION_SPEC);
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
        return jcloudsLocation.obtain(ImmutableMap.builder()
                .put(JcloudsLocation.IMAGE_NAME_REGEX, IMAGE_NAME_REGEX)
                .build());
    }
}
