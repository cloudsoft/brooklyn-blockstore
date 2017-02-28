package brooklyn.location.blockstore.vclouddirector15;

import brooklyn.location.blockstore.AbstractVolumeManagerLiveTest;
import brooklyn.location.blockstore.BlockDeviceOptions;
import brooklyn.location.blockstore.api.BlockDevice;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsMachineLocation;
import org.apache.brooklyn.location.jclouds.JcloudsSshMachineLocation;
import org.jclouds.vcloud.director.v1_5.domain.dmtf.RasdItem;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Map;

/**
 * TODO Assumes that {@code ~/.brooklyn/brooklyn.properties} contains a named location
 * {@code vcloud-director.named-location}.
 * 
 * TODO Unify the way that the live tests expect to get their credentials (e.g. via system 
 * properties, or hard-coded in brooklyn.properties as named locations, etc).
 */
@Test(groups = "Live")
public class VcloudVolumeManagerLiveTest extends AbstractVolumeManagerLiveTest {
    public static final String PROVIDER = "vcloud-director";

    @Override
    protected String getProvider() {
        return PROVIDER;
    }

    @Test(groups="Live")
    @Override
    public void testCreateVolume() throws Exception {
        Map<String, String> tags = ImmutableMap.of(
                "user", System.getProperty("user.name"),
                "purpose", "brooklyn-blockstore-VolumeManagerLiveTest");
        BlockDeviceOptions options = new BlockDeviceOptions()
                .deviceSuffix(getDefaultDeviceSuffix())
                .sizeInGb(getVolumeSize())
                .tags(tags);
        JcloudsMachineLocation currentJcloudshMachineLocation = createJcloudsMachine();
        machines.add(currentJcloudshMachineLocation);
        volume = ((VcloudVolumeManager)volumeManager).createBlockDevice(currentJcloudshMachineLocation, options);
        assertVolumeAvailable(volume);
    }

    @Test(groups="Live", dependsOnMethods = "testCreateVolume")
    @Override
    public void testCreateAndAttachVolume() throws Exception {
        super.testCreateAndAttachVolume();
    }

    @Test(groups="WIP")
    @Override
    public void testMoveMountedVolumeToAnotherMachine() throws Throwable {
        throw new UnsupportedOperationException("This is not possible in Vcloud Director. A volume is bound to the VM and cannot be transferred.");
    }

    @Override
    protected JcloudsLocation createJcloudsLocation() {
        if (namedJcloudsLocation().isPresent()) {
            return (JcloudsLocation)ctx.getLocationRegistry().getLocationManaged("named:" + namedJcloudsLocation().get());
        } else {
            return (JcloudsLocation)ctx.getLocationRegistry().getLocationManaged("vcloud-director");
        }
    }

    @Override
    protected int getVolumeSize() {
        return 64;
    }

    /**
     * No availability zone can be specified in Vcloud Director.
     * @return
     */
    @Override
    protected String getDefaultAvailabilityZone() {
        return null;
    }

    @Override
    protected char getDefaultDeviceSuffix() {
        return 'b';
    }

    @Override
    protected void assertVolumeAvailable(final BlockDevice blockDevice) {
        Optional<RasdItem> diskCreated = VcloudVolumeManager.describeVolume((VcloudBlockDevice)blockDevice);
        Assert.assertTrue(diskCreated.isPresent(), "Disk is available.");
    }

    @Override
    protected JcloudsSshMachineLocation createJcloudsMachine() throws Exception {
        return (JcloudsSshMachineLocation)jcloudsLocation.obtain();
    }

    @Override
    protected Optional<String> namedJcloudsLocation() {
        return Optional.fromNullable(System.getProperty("test.vcloud-director.named-jclouds-location"));
    }

    @Override
    protected Optional<JcloudsSshMachineLocation> rebindJcloudsMachine() {
        return Optional.absent();
    }
}
