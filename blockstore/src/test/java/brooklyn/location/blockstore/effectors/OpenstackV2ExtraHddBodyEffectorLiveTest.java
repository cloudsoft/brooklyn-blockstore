package brooklyn.location.blockstore.effectors;

import java.util.Map;

import org.apache.brooklyn.location.jclouds.JcloudsLocation;

import brooklyn.location.blockstore.openstack.OpenStackLocationConfig;
import brooklyn.location.blockstore.openstack.OpenStackNewVolumeCustomizerLiveTest;

/**
 * Requires disable assertions, and credentials to be injected - 
 * see {@link OpenStackNewVolumeCustomizerLiveTest}.
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
        OpenStackLocationConfig.addBrooklynProperties(mgmt.getBrooklynProperties());
        Map<?, ?> locationConfig = new OpenStackLocationConfig().getConfigMap();

        return (JcloudsLocation)mgmt.getLocationRegistry().getLocationManaged(OpenStackLocationConfig.NAMED_LOCATION, locationConfig);
    }
}
