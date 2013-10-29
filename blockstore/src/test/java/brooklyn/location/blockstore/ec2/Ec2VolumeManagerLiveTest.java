package brooklyn.location.blockstore.ec2;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.util.Map;

import org.jclouds.ec2.domain.Volume;
import org.testng.annotations.Test;

import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.blockstore.AbstractVolumeManagerLiveTest;
import brooklyn.location.blockstore.api.VolumeManager;
import brooklyn.location.blockstore.api.BlockDevice;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.location.jclouds.JcloudsSshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

@Test
public class Ec2VolumeManagerLiveTest extends AbstractVolumeManagerLiveTest {

    // Note we're using the region-name with an explicit availability zone, as is done in the 
    // MarkLogic demo-app so that new VMs will be able to see the existing volumes within that 
    // availability zone.
    
    public static final String PROVIDER = "aws-ec2";
    public static final String REGION_NAME = "us-east-1c";
    public static final String AVAILABILITY_ZONE_NAME = REGION_NAME;
    public static final String LOCATION_SPEC = PROVIDER + (REGION_NAME == null ? "" : ":" + REGION_NAME);
    public static final String TINY_HARDWARE_ID = "t1.micro";
    public static final String SMALL_HARDWARE_ID = "m1.small";

    // Image: {id=us-east-1/ami-7d7bfc14, providerId=ami-7d7bfc14, name=RightImage_CentOS_6.3_x64_v5.8.8.5, location={scope=REGION, id=us-east-1, description=us-east-1, parent=aws-ec2, iso3166Codes=[US-VA]}, os={family=centos, arch=paravirtual, version=6.0, description=rightscale-us-east/RightImage_CentOS_6.3_x64_v5.8.8.5.manifest.xml, is64Bit=true}, description=rightscale-us-east/RightImage_CentOS_6.3_x64_v5.8.8.5.manifest.xml, version=5.8.8.5, status=AVAILABLE[available], loginUser=root, userMetadata={owner=411009282317, rootDeviceType=instance-store, virtualizationType=paravirtual, hypervisor=xen}}
    public static final String CENTOS_IMAGE_ID = "us-east-1/ami-7d7bfc14";
    
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
        return new Ec2VolumeManager();
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
        Map<String, ?> machineFlags = MutableMap.of(
                "id", "i-507fa036",
                "hostname", "ec2-54-204-252-220.compute-1.amazonaws.com",
                "user", "sam",
                JcloudsLocation.PUBLIC_KEY_FILE.getName(), "/Users/sam/.ssh/id_rsa");
//        try {
//            return Optional.of(
//                    jcloudsLocation.rebindMachine(ConfigBag.newInstanceCopying(jcloudsLocation.getRawLocalConfigBag()).putAll(machineFlags)));
//        } catch (NoMachinesAvailableException e) {
//            return Optional.absent();
//        }
        return Optional.absent();
    }
    
    @Override
    protected JcloudsSshMachineLocation createJcloudsMachine() throws Exception {
        return jcloudsLocation.obtain(ImmutableMap.builder()
                .put(JcloudsLocation.IMAGE_ID, CENTOS_IMAGE_ID)
                .put(JcloudsLocation.HARDWARE_ID, SMALL_HARDWARE_ID)
                .build());
    }
}
