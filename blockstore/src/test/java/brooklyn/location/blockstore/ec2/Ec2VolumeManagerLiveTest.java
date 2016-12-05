package brooklyn.location.blockstore.ec2;

import brooklyn.location.blockstore.AbstractVolumeManagerLiveTest;
import brooklyn.location.blockstore.api.BlockDevice;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsSshMachineLocation;
import org.jclouds.ec2.domain.Volume;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

@Test(groups = "Live")
public class Ec2VolumeManagerLiveTest extends AbstractVolumeManagerLiveTest {

    // Note we're using the region-name with an explicit availability zone, as is done in the 
    // MarkLogic demo-app so that new VMs will be able to see the existing volumes within that 
    // availability zone.
    
    public static final String PROVIDER = "aws-ec2";
    public static final String REGION_NAME = "eu-west-1";
    public static final String AVAILABILITY_ZONE_NAME = REGION_NAME + "a";
    public static final String LOCATION_SPEC = "jclouds:" + PROVIDER + (REGION_NAME == null ? "" : ":" + REGION_NAME);
    public static final String TINY_HARDWARE_ID = "t1.micro";
    public static final String SMALL_HARDWARE_ID = "m1.small";

    // Must be an image that supports EBS
    // {id=us-east-1/ami-4e32c626, providerId=ami-4e32c626, name=RightImage_CentOS_6.5_x64_v14.0_EBS, location={scope=REGION, id=us-east-1, description=us-east-1, parent=aws-ec2, iso3166Codes=[US-VA]}, os={family=centos, arch=paravirtual, version=5.0, description=411009282317/RightImage_CentOS_6.5_x64_v14.0_EBS, is64Bit=true}, description=RightImage_CentOS_6.5_x64_v14.0_EBS, version=14.0_EBS, status=AVAILABLE[available], loginUser=root, userMetadata={owner=411009282317, rootDeviceType=ebs, virtualizationType=paravirtual, hypervisor=xen}}
    public static final String CENTOS_IMAGE_ID = "eu-west-1/ami-1d841c6a";
    
    @Override
    protected String getProvider() {
        return PROVIDER;
    }

    @Override
    protected char getDefaultDeviceSuffix() {
        return 'h';
    }

    @Override
    protected JcloudsLocation createJcloudsLocation() {
        return (JcloudsLocation) ctx.getLocationRegistry().getLocationManaged(LOCATION_SPEC);
    }
    
    @Override
    protected int getVolumeSize() {
        return 1;
    }

    @Override
    protected String getDefaultAvailabilityZone() {
        return AVAILABILITY_ZONE_NAME;
    }

    @Override
    protected void assertVolumeAvailable(BlockDevice device) {
        Volume volume = ((Ec2VolumeManager)volumeManager).describeVolume(device);
        assertNotNull(volume);
        assertEquals(volume.getStatus(), Volume.Status.AVAILABLE);
    }

    @Override
    protected Optional<JcloudsSshMachineLocation> rebindJcloudsMachine() {
        return Optional.absent();
    }
    
    @Override
    protected JcloudsSshMachineLocation createJcloudsMachine() throws Exception {
        return (JcloudsSshMachineLocation) jcloudsLocation.obtain(ImmutableMap.builder()
                .put(JcloudsLocation.IMAGE_ID, CENTOS_IMAGE_ID)
                .build());
    }
}
