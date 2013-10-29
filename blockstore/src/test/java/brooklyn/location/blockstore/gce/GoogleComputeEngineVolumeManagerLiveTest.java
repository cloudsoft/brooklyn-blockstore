package brooklyn.location.blockstore.gce;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import org.jclouds.googlecomputeengine.domain.Disk;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

import brooklyn.location.blockstore.AbstractVolumeManagerLiveTest;
import brooklyn.location.blockstore.api.VolumeManager;
import brooklyn.location.blockstore.api.BlockDevice;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.location.jclouds.JcloudsSshMachineLocation;

public class GoogleComputeEngineVolumeManagerLiveTest extends AbstractVolumeManagerLiveTest {

    public static final String PROVIDER = "google-compute-engine";
    public static final String LOCATION_SPEC = PROVIDER;
    public static final String IMAGE_ID = "centos-6-v20130325";
    public static final String IMAGE_NAME_REGEX = ".*centos-6-v20130325.*";

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
        return new GoogleComputeEngineVolumeManager();
    }

    @Override
    protected int getVolumeSize() {
        return 1;
    }

    @Override
    protected String getDefaultAvailabilityZone() {
        return "europe-west1-a";
    }

    @Override
    protected void assertVolumeAvailable(BlockDevice device) {
        Disk disk = ((GoogleComputeEngineVolumeManager) volumeManager).describeVolume(device);
        assertNotNull(disk);
        assertEquals(disk.getStatus(), "READY");
    }

    @Override
    protected Optional<JcloudsSshMachineLocation> rebindJcloudsMachine() {
        return Optional.absent();
    }

    @Override
    protected JcloudsSshMachineLocation createJcloudsMachine() throws Exception {
        return jcloudsLocation.obtain(ImmutableMap.builder()
                .put(JcloudsLocation.IMAGE_ID, IMAGE_ID)
                .put(JcloudsLocation.IMAGE_NAME_REGEX, IMAGE_NAME_REGEX)
                .build());
    }
}
