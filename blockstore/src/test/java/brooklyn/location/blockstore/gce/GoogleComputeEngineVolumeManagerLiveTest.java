package brooklyn.location.blockstore.gce;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import org.apache.brooklyn.core.location.cloud.CloudLocationConfig;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsSshMachineLocation;
import org.jclouds.googlecomputeengine.domain.Disk;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

import brooklyn.location.blockstore.AbstractVolumeManagerLiveTest;
import brooklyn.location.blockstore.api.BlockDevice;
import org.testng.annotations.Test;

/**
 * TODO How to inject credentials? The credentials in {@code .brooklyn/brooklyn.properties}
 * are stripped out by {@link AbstractVolumeManagerLiveTest#setUp()}.
 */
@Test(groups = "Live")
public class GoogleComputeEngineVolumeManagerLiveTest extends AbstractVolumeManagerLiveTest {

    public static final String PROVIDER = "google-compute-engine";
    public static final String REGION = "us-central1-a";
    public static final String LOCATION_SPEC = "jclouds:" + PROVIDER;// + (REGION == null ? "" : ":" + REGION);
    public static final String IMAGE_NAME_REGEX = ".*centos-7-.*";

    @Override
    protected String getProvider() {
        return PROVIDER;
    }

    @Override
    protected JcloudsLocation createJcloudsLocation() {
        JcloudsLocation jcloudsLocation = (JcloudsLocation) ctx.getLocationRegistry().getLocationManaged(LOCATION_SPEC);
        jcloudsLocation.config().set(CloudLocationConfig.CLOUD_REGION_ID, getDefaultAvailabilityZone());
        return (JcloudsLocation) ctx.getLocationRegistry().getLocationManaged(LOCATION_SPEC);
    }

    @Override
    protected char getDefaultDeviceSuffix() {
        return 'b';
    }

    @Override
    protected int getVolumeSize() {
        return 1;
    }

    @Override
    protected String getDefaultAvailabilityZone() {
        return REGION;
    }

    @Override
    protected void assertVolumeAvailable(BlockDevice device) {
        Disk disk = ((GoogleComputeEngineVolumeManager) volumeManager).describeVolume(device);
        assertNotNull(disk);
        assertEquals(disk.status(), Disk.Status.READY);
    }

    @Override
    protected Optional<JcloudsSshMachineLocation> rebindJcloudsMachine() {
        return Optional.absent();
    }

    @Override
    protected JcloudsSshMachineLocation createJcloudsMachine() throws Exception {
        return (JcloudsSshMachineLocation) jcloudsLocation.obtain(ImmutableMap.builder()
                .put(JcloudsLocation.IMAGE_NAME_REGEX, IMAGE_NAME_REGEX)
                .build());
    }
    
    @Test(groups={"Live", "WIP"})
    @Override
    public void testCreateVolume() throws Exception {
        super.testCreateVolume();
    }

    // TODO FIXME: Test provisions a VM in asia-east1-a instead of the region specified.
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
