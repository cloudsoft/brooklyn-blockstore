package brooklyn.location.blockstore.effectors;

import com.google.common.collect.ImmutableMap;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;

public class AwsExtraHddBodyEffectorLiveTest extends AbstractExtraHddBodyEffectorLiveTest {
    @Override
    protected JcloudsLocation obtainJcloudsLocation() {
        return (JcloudsLocation)mgmt.getLocationRegistry().getLocationManaged("jclouds:aws-ec2:eu-west-1", ImmutableMap.<String, Object>builder()
                .put("osFamily", "centos")
                .put("imageId", "eu-west-1/ami-1d841c6a")
                .build());
    }

    @Override
    protected String getMountPointBaseDir() {
        return "/mount/brooklyn";
    }

    @Override
    protected String getDeviceNamePrefix() {
        return "/dev/sd";
    }

    @Override
    protected char getFirstDeviceSuffix() {
        return 'h';
    }
}
