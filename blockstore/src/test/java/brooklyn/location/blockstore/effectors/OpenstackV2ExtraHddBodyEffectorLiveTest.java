package brooklyn.location.blockstore.effectors;

import brooklyn.location.blockstore.openstack.OpenStackLocationConfig;
import com.google.common.collect.ImmutableMap;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;

/**
 * NB!! Disable asserts when launching this test.
 */
public class OpenstackV2ExtraHddBodyEffectorLiveTest extends AbstractExtraHddBodyEffectorLiveTest {
    @Override
    protected String getMountPointBaseDir() {
        return "/mount/brooklyn";
    }

    @Override
    protected String getDeviceNamePrefix() {
        return "/dev/vd";
    }

    @Override
    protected char getFirstDeviceSuffix() {
        return 'b';
    }

    @Override
    protected JcloudsLocation obtainJcloudsLocation() {
        locationConfig = new OpenStackLocationConfig();
        brooklynProperties = mgmt.getBrooklynProperties();
        locationConfig.addBrooklynProperties(brooklynProperties);
        return (JcloudsLocation)mgmt.getLocationRegistry().getLocationManaged(locationConfig.NAMED_LOCATION, locationConfig.getConfigMap());
    }
}
