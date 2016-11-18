package brooklyn.location.blockstore.openstack;

import brooklyn.location.blockstore.api.VolumeOptions;
import com.google.common.collect.ImmutableList;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.test.BrooklynAppLiveTestSupport;
import org.apache.brooklyn.entity.machine.MachineEntity;
import org.apache.brooklyn.entity.software.base.EmptySoftwareProcess;
import org.apache.brooklyn.entity.software.base.lifecycle.NaiveScriptRunner;
import org.apache.brooklyn.entity.software.base.lifecycle.ScriptHelper;
import org.apache.brooklyn.location.jclouds.JcloudsLocationConfig;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.Assert.assertTrue;

public class OpenStackNewVolumeCustomizerLiveTest extends BrooklynAppLiveTestSupport {

    protected Location jcloudsLocation;
    protected BrooklynProperties brooklynProperties;
    protected OpenStackLocationConfig locationConfig;

    @Test(groups = "Live")
    public void testCustomizerCreatesAndAttachesNewVolumeOnProvisioningTime() {
        locationConfig = new OpenStackLocationConfig();
        brooklynProperties = mgmt.getBrooklynProperties();
        locationConfig.addBrooklynProperties(brooklynProperties);

        jcloudsLocation = mgmt.getLocationRegistry().getLocationManaged(locationConfig.NAMED_LOCATION, locationConfig.getConfigMap());

        OpenstackNewVolumeCustomizer customizer = new OpenstackNewVolumeCustomizer();
        customizer.setVolumes(MutableList.of(
                VolumeOptions.<Map<String, Map<String, ?>>>fromMap(
                        MutableMap.<String, Map<String,?>>of(
                                "blockDevice", MutableMap.of(
                                        "sizeInGb", 3,
                                        "deviceSuffix", 'b',
                                        "deleteOnTermination", true),
                                "filesystem", MutableMap.<String, Object>of(
                                        "mountPoint", "/mount/brooklyn/b",
                                        "filesystemType", "ext3"))),
                VolumeOptions.<String, Map<String,?>>fromMap(
                        MutableMap.<String, Map<String, ?>>of(
                                "blockDevice", MutableMap.<String, Object>of(
                                    "sizeInGb", 3,
                                    "deviceSuffix", 'c',
                                    "deleteOnTermination", true),
                                "filesystem", MutableMap.<String, Object>of(
                                        "mountPoint", "/mount/brooklyn/c",
                                        "filesystemType", "ext3")))));

        EmptySoftwareProcess entity = app.createAndManageChild(EntitySpec.create(EmptySoftwareProcess.class)
                .configure(MachineEntity.PROVISIONING_PROPERTIES.subKey(JcloudsLocationConfig.JCLOUDS_LOCATION_CUSTOMIZERS.getName()),
                        MutableList.of(customizer)));

        app.start(ImmutableList.of(jcloudsLocation));

        ScriptHelper scriptHelper = new ScriptHelper((NaiveScriptRunner) entity.getDriver(),
                "Checking machine disks").body.append("df").gatherOutput();

        scriptHelper.execute();
        assertTrue(scriptHelper.getResultStdout().contains("/mount/brooklyn/b"));
    }

}
