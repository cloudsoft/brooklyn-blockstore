package brooklyn.location.blockstore.openstack;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsSshMachineLocation;
import org.jclouds.openstack.cinder.v1.domain.Volume;
import org.testng.annotations.Test;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

import brooklyn.location.blockstore.AbstractVolumeManagerLiveTest;
import brooklyn.location.blockstore.api.BlockDevice;

/**
 * TODO Tests disabled - need to properly implement {@link #getDefaultDeviceSuffix()}.
 * 
 * TODO How to inject credentials? The credentials in {@code .brooklyn/brooklyn.properties}
 * are stripped out by {@link AbstractVolumeCustomizerLiveTest#setUp()}.
 */
@Test(groups = "Live")
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
        return (JcloudsLocation) ctx.getLocationRegistry().getLocationManaged(LOCATION_SPEC);
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
        throw new IllegalStateException("Not implemented. Figure out the correct device suffix.");
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
    
    // TODO See getDefaultDeviceSuffix() - it just throws an exception
    @Test(groups={"Live", "WIP"})
    @Override
    public void testCreateVolume() throws Exception {
        super.testCreateVolume();
    }
    
    @Test(groups={"Live", "WIP"}, dependsOnMethods = "testCreateVolume")
    @Override
    public void testCreateAndAttachVolume() throws Exception {
        super.testCreateAndAttachVolume();
    }

    @Test(groups={"Live", "WIP"}, dependsOnMethods = {"testCreateAndAttachVolume"})
    @Override
    public void testMoveMountedVolumeToAnotherMachine() throws Throwable {
        super.testMoveMountedVolumeToAnotherMachine();
    }
}
