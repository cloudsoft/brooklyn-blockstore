package brooklyn.location.blockstore.ec2;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.util.Map;

import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsSshMachineLocation;
import org.jclouds.ec2.domain.Volume;
import org.testng.annotations.Test;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

import brooklyn.location.blockstore.AbstractVolumeManagerLiveTest;
import brooklyn.location.blockstore.BlockDeviceOptions;
import brooklyn.location.blockstore.api.BlockDevice;

@Test(groups = "Live")
public class Ec2VolumeManagerLiveTest extends AbstractVolumeManagerLiveTest {

    /*
     * TODO Test failure in testMoveMountedVolumeToAnotherMachine, with the below error:
       2018-05-14 09:17:18,675 INFO  - Creating and mounting device on machine1: SshMachineLocation[52.213.224.120:aledsage@ec2-52-213-224-120.eu-west-1.compute.amazonaws.com/52.213.224.120:22(id=le9rlzj6mt)]
       2018-05-14 09:17:56,834 INFO  - Unmounting filesystem: MountedBlockDeviceImpl{id=vol-01234567890012345, machine=SshMachineLocation[52.213.224.120:aledsage@ec2-52-213-224-120.eu-west-1.compute.amazonaws.com/52.213.224.120:22(id=le9rlzj6mt)], deviceName=/dev/sdh, mountPoint=/var/opt/mountpoint}
       2018-05-14 09:18:57,765 ERROR - Volume MountedBlockDeviceImpl{id=vol-01234567890012345, machine=SshMachineLocation[52.213.224.120:aledsage@ec2-52-213-224-120.eu-west-1.compute.amazonaws.com/52.213.224.120:22(id=le9rlzj6mt)], deviceName=/dev/sdh, mountPoint=/var/opt/mountpoint} still not available. Last known was: Volume [attachments=[Attachment [region=eu-west-1, volumeId=vol-01234567890123456, instanceId=i-012345678901234567, device=/dev/sdh, attachTime=Mon May 14 09:17:24 BST 2018, status=detaching]], availabilityZone=eu-west-1a, createTime=Mon May 14 09:17:19 BST 2018, id=vol-01234567890012345, region=eu-west-1, size=1, snapshotId=null, status=in-use, volumeType=null, iops=null, encrypted=false]; continuing
       2018-05-14 09:18:57,765 INFO  - Remounting BlockDeviceImpl{id=vol-01234567890012345, location=JcloudsLocation[aws-ec2:eu-west-1a:XXXXXXXXXXXXXXXXXXXX@xxxxxxxxxx]} on machine2: SshMachineLocation[52.49.236.134:aledsage@ec2-52-49-236-134.eu-west-1.compute.amazonaws.com/52.49.236.134:22(id=x6spxujti8)]
       2018-05-14 09:18:58,085 INFO  - TESTNG FAILED: "Default test" - brooklyn.location.blockstore.AbstractVolumeManagerLiveTest.testMoveMountedVolumeToAnotherMachine() finished in 229620 ms
       org.jclouds.aws.AWSResponseException: request POST https://ec2.eu-west-1.amazonaws.com/ HTTP/1.1 failed with code 400, error: AWSError{requestId='xxxxxxxx-xxxx-xxxx-a4bd-eb2ea0e7d3fe', requestToken='null', code='VolumeInUse', message='vol-01234567890012345 is already attached to an instance', context='{Response=, Errors=}'}
           at org.jclouds.aws.handlers.ParseAWSErrorFromXmlContent.handleError(ParseAWSErrorFromXmlContent.java:75)
           at org.jclouds.http.handlers.DelegatingErrorHandler.handleError(DelegatingErrorHandler.java:65)
           at org.jclouds.http.internal.BaseHttpCommandExecutorService.shouldContinue(BaseHttpCommandExecutorService.java:138)
           at org.jclouds.http.internal.BaseHttpCommandExecutorService.invoke(BaseHttpCommandExecutorService.java:107)
           at org.jclouds.rest.internal.InvokeHttpMethod.invoke(InvokeHttpMethod.java:91)
           at org.jclouds.rest.internal.InvokeHttpMethod.apply(InvokeHttpMethod.java:74)
           at org.jclouds.rest.internal.InvokeHttpMethod.apply(InvokeHttpMethod.java:45)
           at org.jclouds.reflect.FunctionalReflection$FunctionalInvocationHandler.handleInvocation(FunctionalReflection.java:117)
           at com.google.common.reflect.AbstractInvocationHandler.invoke(AbstractInvocationHandler.java:87)
           at com.sun.proxy.$Proxy70.attachVolumeInRegion(Unknown Source)
           at brooklyn.location.blockstore.ec2.Ec2VolumeManager.attachBlockDevice(Ec2VolumeManager.java:83)
           at brooklyn.location.blockstore.AbstractVolumeManager.attachAndMountVolume(AbstractVolumeManager.java:70)
           at brooklyn.location.blockstore.AbstractVolumeManagerLiveTest.testMoveMountedVolumeToAnotherMachine(AbstractVolumeManagerLiveTest.java:197)
     */
    
    // Note we're using the region-name with an explicit availability zone, as is done in the 
    // MarkLogic demo-app so that new VMs will be able to see the existing volumes within that 
    // availability zone.
    
    public static final String PROVIDER = "aws-ec2";
    public static final String REGION_NAME = "eu-west-1";
    public static final String AVAILABILITY_ZONE_NAME = REGION_NAME + "a";
    public static final String LOCATION_SPEC = "jclouds:" + PROVIDER + ":" + AVAILABILITY_ZONE_NAME;
    public static final String TINY_HARDWARE_ID = "t1.micro";
    public static final String SMALL_HARDWARE_ID = "m1.small";

    // Must be an image that supports EBS
    // {id=us-east-1/ami-4e32c626, providerId=ami-4e32c626, name=RightImage_CentOS_6.5_x64_v14.0_EBS, location={scope=REGION, id=us-east-1, description=us-east-1, parent=aws-ec2, iso3166Codes=[US-VA]}, os={family=centos, arch=paravirtual, version=5.0, description=411009282317/RightImage_CentOS_6.5_x64_v14.0_EBS, is64Bit=true}, description=RightImage_CentOS_6.5_x64_v14.0_EBS, version=14.0_EBS, status=AVAILABLE[available], loginUser=root, userMetadata={owner=411009282317, rootDeviceType=ebs, virtualizationType=paravirtual, hypervisor=xen}}
    public static final String CENTOS_IMAGE_ID = "eu-west-1/ami-1d841c6a";
    
    // A copy of super method, except also tests setting volumeType
    @Test(groups="Live")
    @Override
    public void testCreateVolume() throws Exception {
        // TODO see https://issues.apache.org/jira/browse/JCLOUDS-1417: if you include tags, 
        // then `describeVolumesInRegion` fails to parse the volume correctly!
        Map<String, String> tags = ImmutableMap.of(
                "user", System.getProperty("user.name"),
                "purpose", "brooklyn-blockstore-VolumeManagerLiveTest");
        BlockDeviceOptions options = new BlockDeviceOptions()
                .zone(getDefaultAvailabilityZone())
                .deviceSuffix(getDefaultDeviceSuffix())
                .sizeInGb(getVolumeSize())
                .volumeType("gp2");
//                .tags(tags);
        volume = volumeManager.createBlockDevice(jcloudsLocation, options);
        assertVolumeAvailable(volume);
        assertVolumeType(volume, "gp2");
    }

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
        assertEquals(volume.getStatus(), Volume.Status.AVAILABLE, "volume="+volume);
    }

    
    protected void assertVolumeType(BlockDevice device, String expectedType) {
        Volume volume = ((Ec2VolumeManager)volumeManager).describeVolume(device);
        assertEquals(volume.getVolumeType(), expectedType, "volume="+volume);
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
