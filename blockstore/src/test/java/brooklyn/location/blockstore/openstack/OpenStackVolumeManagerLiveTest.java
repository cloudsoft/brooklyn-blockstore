package brooklyn.location.blockstore.openstack;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.util.Map;

import org.jclouds.openstack.cinder.v1.domain.Volume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.blockstore.AbstractVolumeManagerLiveTest;
import brooklyn.location.blockstore.VolumeManager;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.location.jclouds.JcloudsSshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;

import com.google.common.collect.ImmutableMap;

@Test
public class OpenStackVolumeManagerLiveTest extends AbstractVolumeManagerLiveTest {

    private static final Logger LOG = LoggerFactory.getLogger(OpenStackVolumeManagerLiveTest.class);

    public static final String PROVIDER = "rackspace-cloudservers-uk";
    public static final String LOCATION_SPEC = PROVIDER;
    public static final String TINY_HARDWARE_ID = "1";
    public static final String SMALL_HARDWARE_ID = "2";

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
        return new OpenstackVolumeManager();
    }

    @Override
    protected int getVolumeSize() {
        return 100; // min on rackspace is 100
    }

    @Override
    protected String getDefaultAvailabilityZone() {
        return null;
    }

    @Override
    protected void assertVolumeAvailable(String volumeId) {
        Volume volume = ((OpenstackVolumeManager)volumeManager).describeVolume(jcloudsLocation, volumeId);
        assertNotNull(volume);
        assertEquals(volume.getStatus(), Volume.Status.AVAILABLE);
    }

    @Override
    protected JcloudsSshMachineLocation rebindJcloudsMachine() throws NoMachinesAvailableException {
        Map<String, ?> machineFlags = MutableMap.of("id", "LON/dab52345-9b6f-4b60-94de-64881a3f91d9", 
                "hostname", "162.13.8.61", 
                "user", "root", 
                "password", "Bko8gLpZs6CJ",
                JcloudsLocation.PUBLIC_KEY_FILE.getName(), "/Users/aled/.ssh/id_rsa");
        return jcloudsLocation.rebindMachine(ConfigBag.newInstanceCopying(jcloudsLocation.getRawLocalConfigBag()).putAll(machineFlags));
    }
    
    @Override
    protected JcloudsSshMachineLocation createJcloudsMachine() throws Exception {
        String osRegex = "CentOS 6.0";

        // TODO Wanted to specify hardware id, but this failed; and wanted to force no imageId (in case specified in brooklyn.properties)
        return jcloudsLocation.obtain(ImmutableMap.builder()
                .put(JcloudsLocation.IMAGE_NAME_REGEX, osRegex)
                //.put(JcloudsLocation.IMAGE_ID, "")
                //.put(JcloudsLocation.HARDWARE_ID, SMALL_HARDWARE_ID)
                .build());
    }
}
