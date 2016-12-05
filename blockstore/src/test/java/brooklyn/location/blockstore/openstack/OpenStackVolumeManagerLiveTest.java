package brooklyn.location.blockstore.openstack;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.util.Map;

import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsSshMachineLocation;
import org.jclouds.openstack.cinder.v1.domain.Volume;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Optional;

import brooklyn.location.blockstore.AbstractVolumeManagerLiveTest;
import brooklyn.location.blockstore.api.BlockDevice;

/**
 * Requires disable assertions, and credentials to be injected - 
 * see {@link OpenStackNewVolumeCustomizerLiveTest}.
 */
@Test(groups = "Live")
public class OpenStackVolumeManagerLiveTest extends AbstractVolumeManagerLiveTest {

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected String getProvider() {
        return OpenStackLocationConfig.PROVIDER;
    }

    @Override
    protected void addBrooklynProperties(BrooklynProperties props) {
        OpenStackLocationConfig.addBrooklynProperties(props);
    }

    @Override
    protected JcloudsLocation createJcloudsLocation() {
        return (JcloudsLocation) ctx.getLocationRegistry().getLocationManaged("named:"+OpenStackLocationConfig.NAMED_LOCATION);
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
        Map<?, ?> locationConfig = new OpenStackLocationConfig().getConfigMap();

        return (JcloudsSshMachineLocation) jcloudsLocation.obtain(locationConfig);
    }
}
