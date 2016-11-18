package brooklyn.location.blockstore.ec2;

import static brooklyn.location.blockstore.AbstractVolumeManagerLiveTest.assertMountPointExists;
import static brooklyn.location.blockstore.AbstractVolumeManagerLiveTest.assertReadable;
import static brooklyn.location.blockstore.AbstractVolumeManagerLiveTest.assertWritable;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Map;

import brooklyn.location.blockstore.api.VolumeOptions;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsLocationCustomizer;
import org.apache.brooklyn.util.collections.MutableList;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import brooklyn.location.blockstore.AbstractVolumeCustomizerLiveTest;
import brooklyn.location.blockstore.BlockDeviceOptions;
import brooklyn.location.blockstore.FilesystemOptions;

@Test(groups = "WIP")
public class Ec2VolumeCustomizerLiveTest extends AbstractVolumeCustomizerLiveTest {

    @Override
    protected String getProvider() {
        return Ec2VolumeManagerLiveTest.PROVIDER;
    }

    @Override
    protected JcloudsLocation createJcloudsLocation() {
        return (JcloudsLocation) ctx.getLocationRegistry().getLocationManaged(Ec2VolumeManagerLiveTest.LOCATION_SPEC);
    }
    
    @Override
    protected int getVolumeSize() {
        return 1;
    }

    @Override
    protected Map<?, ?> additionalObtainArgs() throws Exception {
        return ImmutableMap.builder()
                .put(JcloudsLocation.IMAGE_ID, Ec2VolumeManagerLiveTest.CENTOS_IMAGE_ID)
                .put(JcloudsLocation.HARDWARE_ID, Ec2VolumeManagerLiveTest.SMALL_HARDWARE_ID)
                .build();
    }

    @Override
    protected List<String> getMountPoints() {
        char deviceSuffix = 'h';
        return ImmutableList.of("/mnt/brooklyn/"+deviceSuffix, "/mnt/brooklyn/"+(deviceSuffix+1));
    }
    
    protected String getDefaultAvailabilityZone() {
        return Ec2VolumeManagerLiveTest.AVAILABILITY_ZONE_NAME;
    }

//    @Test(groups="Live")
//    public void testCreateVmWithAttachedVolume() throws Throwable {
//        List<Character> deviceSuffixes = ImmutableList.of('g');
//        List<Integer> capacities = ImmutableList.of(1);
//        List<String> mountPoints = ImmutableList.of("/mnt/brooklyn/g");
//
//        Map<BlockDeviceOptions, FilesystemOptions> volumes = Maps.newLinkedHashMap();
//        for (int i = 0; i < capacities.size(); i++) {
//            char deviceSuffix = deviceSuffixes.get(i);
//            Integer capacity = capacities.get(i);
//            String mountPoint = mountPoints.get(i);
//            BlockDeviceOptions blockOptions = new BlockDeviceOptions()
//                    .deviceSuffix(deviceSuffix)
//                    .sizeInGb(capacity)
//                    .deleteOnTermination(true);
//            FilesystemOptions filesystemOptions = new FilesystemOptions(mountPoint);
//            volumes.put(blockOptions, filesystemOptions);
//        }
//        JcloudsLocationCustomizer customizer = Ec2VolumeCustomizers.withNewVolumes(volumes);
//
//        machine = createJcloudsMachine(customizer);
//        
//        for (String mountPoint : mountPoints) {
//            assertMountPointWritable(mountPoint);
//        }
//    }
    
    @Test(groups="Live")
    @Override
    public void testCreateVmWithAttachedVolume() throws Exception {
        //String mountPoint = "/var/opt2/test1";
        List<Character> deviceSuffixes = ImmutableList.of('g');
        List<Integer> capacities = ImmutableList.of(1);
        List<String> mountPoints = ImmutableList.of("/mnt/brooklyn/g");

        List<VolumeOptions> volumes = MutableList.of();
        for (int i = 0; i < capacities.size(); i++) {
            Integer capacity = checkNotNull(capacities.get(i), "capacity(%s)", i);
            Character deviceSuffix = checkNotNull(deviceSuffixes.get(i), "deviceSuffix(%s)", i);
            BlockDeviceOptions blockDeviceOptions = new BlockDeviceOptions()
                    .sizeInGb(capacity)
                    .zone(getDefaultAvailabilityZone())
                    .deviceSuffix(deviceSuffix)
                    .tags(ImmutableMap.of(
                            "user", System.getProperty("user.name"),
                            "purpose", "brooklyn-blockstore-VolumeCustomizerLiveTest"));
            FilesystemOptions filesystemOptions = new FilesystemOptions(mountPoints.get(i), "ext3");
            volumes.add(new VolumeOptions(blockDeviceOptions, filesystemOptions));
        }
        
        JcloudsLocationCustomizer customizer = new Ec2NewVolumeCustomizer(volumes);

        machine = createJcloudsMachine(customizer);
        
        for (String mountPoint : mountPoints) {
            String destFile = mountPoint+"/myfile.txt";
            assertMountPointExists(machine, mountPoint);
            assertWritable(machine, destFile, "abc".getBytes());
            assertReadable(machine, destFile, "abc".getBytes());
        }
    }
}
