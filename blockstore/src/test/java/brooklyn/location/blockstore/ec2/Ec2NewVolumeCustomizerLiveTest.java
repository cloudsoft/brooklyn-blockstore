package brooklyn.location.blockstore.ec2;

import brooklyn.location.blockstore.NewVolumeCustomizer;
import brooklyn.location.blockstore.api.VolumeOptions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
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

/**
 * Assumes that {@code ~/.brooklyn/brooklyn.properties} has aws-ec2 credentials under
 * {@code brooklyn.location.jclouds.aws-ec2.*}.
 */
public class Ec2NewVolumeCustomizerLiveTest extends BrooklynAppLiveTestSupport {

    protected Location jcloudsLocation;

    @Test(groups = "Live")
    public void testCustomizerCreatesAndAttachesNewVolumeOnProvisioningTime() {
        jcloudsLocation = mgmt.getLocationRegistry().getLocationManaged("jclouds:aws-ec2:eu-west-1", ImmutableMap.<String, Object>builder()
                .put("osFamily", "centos")
                .put("imageId", "eu-west-1/ami-1d841c6a")
                .build());

        NewVolumeCustomizer customizer = new NewVolumeCustomizer();
        customizer.setVolumes(MutableList.of(
                VolumeOptions.fromMap(MutableMap.<String, Map<String,?>>of(
                        "blockDevice", MutableMap.of(
                            "sizeInGb", 3,
                            "deviceSuffix", 'h',
                            "deleteOnTermination", true
                            ),
                        "filesystem", MutableMap.of(
                                "mountPoint", "/mount/brooklyn/h",
                                "filesystemType", "ext3"
                        ))),

                VolumeOptions.fromMap(MutableMap.<String, Map<String,?>>of(
                        "blockDevice", MutableMap.of(
                            "sizeInGb", 3,
                            "deviceSuffix", 'g',
                            "deleteOnTermination", true
                        ),
                        "filesystem", MutableMap.of(
                                "mountPoint", "/mount/brooklyn/g",
                                "filesystemType", "ext3"
                        )))));

        EmptySoftwareProcess entity = app.createAndManageChild(EntitySpec.create(EmptySoftwareProcess.class)
                .configure(MachineEntity.PROVISIONING_PROPERTIES.subKey(JcloudsLocationConfig.JCLOUDS_LOCATION_CUSTOMIZERS.getName()),
                        MutableList.of(customizer)));

        app.start(ImmutableList.of(jcloudsLocation));

        ScriptHelper scriptHelper = new ScriptHelper((NaiveScriptRunner) entity.getDriver(),
                "Checking machine disks").body.append("df").gatherOutput();

        scriptHelper.execute();
        assertTrue(scriptHelper.getResultStdout().contains("/mount/brooklyn/h"));
    }
}
