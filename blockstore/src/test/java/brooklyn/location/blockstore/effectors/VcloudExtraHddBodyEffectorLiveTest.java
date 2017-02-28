package brooklyn.location.blockstore.effectors;

import org.apache.brooklyn.location.jclouds.JcloudsLocation;

public class VcloudExtraHddBodyEffectorLiveTest extends AbstractExtraHddBodyEffectorLiveTest {
    @Override
    protected JcloudsLocation obtainJcloudsLocation() {
        String namedJcloudsLocation = System.getProperty("test.vcloud-director.named-jclouds-location");
        if (namedJcloudsLocation != null) {
            return (JcloudsLocation)mgmt.getLocationRegistry().getLocationManaged("named:" + namedJcloudsLocation);
        } else {
            return (JcloudsLocation)mgmt.getLocationRegistry().getLocationManaged("vcloud-director");
        }
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
        return 'b';
    }
}
