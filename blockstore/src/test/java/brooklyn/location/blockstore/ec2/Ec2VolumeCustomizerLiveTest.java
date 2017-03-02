package brooklyn.location.blockstore.ec2;

import java.util.Map;

import org.apache.brooklyn.location.jclouds.JcloudsLocation;

import com.google.common.collect.ImmutableMap;

import brooklyn.location.blockstore.AbstractVolumeCustomizerLiveTest;

/**
 * Assumes that {@code ~/.brooklyn/brooklyn.properties} has aws-ec2 credentials under
 * {@code brooklyn.location.jclouds.aws-ec2.*}.
 */
public class Ec2VolumeCustomizerLiveTest extends AbstractVolumeCustomizerLiveTest {

    public static final String PROVIDER = "aws-ec2";
    public static final String REGION_NAME = "eu-west-1";
    public static final String LOCATION_SPEC = "jclouds:" + PROVIDER + ":" + REGION_NAME;
    
    public static final String CENTOS_IMAGE_ID = Ec2VolumeManagerLiveTest.CENTOS_IMAGE_ID;

    @Override
    protected String locationSpec() {
        return LOCATION_SPEC;
    }

    @Override
    protected Map<?, ?> additionalObtainArgs() throws Exception {
        return ImmutableMap.<Object,Object>builder()
                .putAll(super.additionalObtainArgs())
                .put(JcloudsLocation.IMAGE_ID, CENTOS_IMAGE_ID)
                .build();
    }

    @Override
    protected int getVolumeSize() {
        return 1;
    }

    @Override
    protected char getDefaultDeviceSuffix() {
        return 'h';
    }
}
