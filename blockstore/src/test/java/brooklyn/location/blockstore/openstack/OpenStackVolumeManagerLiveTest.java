package brooklyn.location.blockstore.openstack;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import org.jclouds.openstack.cinder.v1.domain.Volume;
import org.testng.annotations.Test;

import brooklyn.location.blockstore.AbstractVolumeManagerLiveTest;
import brooklyn.location.blockstore.api.BlockDevice;
import brooklyn.location.blockstore.api.VolumeManager;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.location.jclouds.JcloudsSshMachineLocation;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

@Test
public class OpenStackVolumeManagerLiveTest extends AbstractVolumeManagerLiveTest {

    public static final String PROVIDER = "rackspace-uk";
    public static final String LOCATION_SPEC = PROVIDER;
    public static final String TINY_HARDWARE_ID = "1";
    public static final String SMALL_HARDWARE_ID = "2";

    @Override
    protected String getProvider() {
        return PROVIDER;
    }

    @Override
    protected JcloudsLocation createJcloudsLocation() {
        return (JcloudsLocation) ctx.getLocationRegistry().resolve(LOCATION_SPEC);
    }
    
    @Override
    protected VolumeManager createVolumeManager() {
        return new OpenstackVolumeManager();
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
        Volume volume = ((OpenstackVolumeManager)volumeManager).describeVolume(device);
        assertNotNull(volume);
        assertEquals(volume.getStatus(), Volume.Status.AVAILABLE);
    }

    @Override
    protected Optional<JcloudsSshMachineLocation> rebindJcloudsMachine() {
        return Optional.absent();
    }
    
    @Override
    protected JcloudsSshMachineLocation createJcloudsMachine() throws Exception {
        String osRegex = "CentOS 6.";

        // TODO Wanted to specify hardware id, but this failed; and wanted to force no imageId (in case specified in brooklyn.properties)
        return jcloudsLocation.obtain(ImmutableMap.builder()
                .put(JcloudsLocation.IMAGE_NAME_REGEX, osRegex)
                //.put(JcloudsLocation.IMAGE_ID, "")
                //.put(JcloudsLocation.HARDWARE_ID, SMALL_HARDWARE_ID)
                .build());
    }
}
