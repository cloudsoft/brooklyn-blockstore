package brooklyn.location.blockstore.ec2;

import java.util.List;
import java.util.Map;

import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import brooklyn.location.blockstore.AbstractVolumeCustomizerLiveTest;

@Test
public class Ec2VolumeCustomizerLiveTest extends AbstractVolumeCustomizerLiveTest {

    @Override
    protected String getProvider() {
        return Ec2VolumeManagerLiveTest.PROVIDER;
    }

    @Override
    protected JcloudsLocation createJcloudsLocation() {
        return (JcloudsLocation) ctx.getLocationRegistry().resolve(Ec2VolumeManagerLiveTest.LOCATION_SPEC);
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
    
    // FIXME Failed: org.jclouds.aws.AWSResponseException: request POST https://ec2.us-east-1.amazonaws.com/ HTTP/1.1 failed with code 400, error: AWSError{requestId='2097294e-eadd-4d4c-bc9b-1a8e672b4beb', requestToken='null', code='InvalidParameterValue', message='Invalid value '/dev/sdh' for unixDevice. Attachment point /dev/sdh is already in use', context='{Response=, Errors=}'}
    // What should we use instead of 'h'?
    // 
    @Test(groups="Live")
    @Override
    public void testCreateVmWithAttachedVolume() throws Throwable {
        super.testCreateVmWithAttachedVolume();
    }
}
