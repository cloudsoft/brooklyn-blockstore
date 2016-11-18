package brooklyn.location.blockstore.openstack;

import brooklyn.location.blockstore.AbstractVolumeManagerLiveTest;
import brooklyn.location.blockstore.api.BlockDevice;
import com.google.common.base.Optional;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsSshMachineLocation;
import org.jclouds.openstack.cinder.v1.domain.Volume;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

@Test
public class OpenStackVolumeManagerLiveTest extends AbstractVolumeManagerLiveTest {

    public static OpenStackLocationConfig locationConfig;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        super.setUp();
        locationConfig = new OpenStackLocationConfig();
    }

    @Override
    protected String getProvider() {
        return locationConfig.PROVIDER;
    }

    @Override
    protected void addBrooklynProperties(BrooklynProperties props) {
        locationConfig.addBrooklynProperties(props);
    }

    @Override
    protected JcloudsLocation createJcloudsLocation() {
        return (JcloudsLocation) ctx.getLocationRegistry().getLocationManaged("named:"+locationConfig.NAMED_LOCATION);
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
    protected char getDefaultDeviceSuffix() {
        return 'b';
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
        return (JcloudsSshMachineLocation) jcloudsLocation.obtain(locationConfig.getConfigMap());
    }
}
