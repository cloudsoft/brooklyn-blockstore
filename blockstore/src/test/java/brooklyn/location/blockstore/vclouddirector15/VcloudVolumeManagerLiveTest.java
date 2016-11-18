package brooklyn.location.blockstore.vclouddirector15;

import brooklyn.location.blockstore.AbstractVolumeManagerLiveTest;
import brooklyn.location.blockstore.api.BlockDevice;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsSshMachineLocation;
import org.jclouds.vcloud.director.v1_5.VCloudDirectorApi;
import org.jclouds.vcloud.director.v1_5.domain.RasdItemsList;
import org.jclouds.vcloud.director.v1_5.domain.dmtf.RasdItem;
import org.testng.Assert;

import javax.annotation.Nullable;

public class VcloudVolumeManagerLiveTest extends AbstractVolumeManagerLiveTest {
    public static final String PROVIDER = "vcloud-director";

    @Override
    protected String getProvider() {
        return PROVIDER;
    }

    @Override
    protected JcloudsLocation createJcloudsLocation() {
        String namedLocation = System.getProperty("vcloud-director.named-location");
        if (namedLocation != null) {
            return (JcloudsLocation)ctx.getLocationRegistry().getLocationManaged("named:" + namedLocation);
        } else {
            return (JcloudsLocation)ctx.getLocationRegistry().getLocationManaged("vcloud-director");
        }
    }

    @Override
    protected int getVolumeSize() {
        return 64;
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
    protected void assertVolumeAvailable(final BlockDevice blockDevice) {
        VCloudDirectorApi vCloudDirectorApi = jcloudsLocation.getComputeService().getContext().unwrapApi(VCloudDirectorApi.class);
        RasdItemsList disks = vCloudDirectorApi.getVmApi().getVirtualHardwareSectionDisks(((VcloudBlockDevice)blockDevice).getVm().getId());
        Optional<RasdItem> diskCreated = Iterables.tryFind(disks, new Predicate<RasdItem>() {
                   @Override public boolean apply(@Nullable RasdItem input) {
                      return RasdItem.ResourceType.DISK_DRIVE.equals(input.getResourceType()) && blockDevice.getId().equals(input.getInstanceID());
                   }
               });
        Assert.assertTrue(diskCreated.isPresent(), "Disk is available.");
    }

    @Override
    protected JcloudsSshMachineLocation createJcloudsMachine() throws Exception {
        return (JcloudsSshMachineLocation)jcloudsLocation.obtain();
    }

    @Override
    protected Optional<JcloudsSshMachineLocation> rebindJcloudsMachine() {
        return Optional.absent();
    }
}
