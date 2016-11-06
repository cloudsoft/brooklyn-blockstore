package brooklyn.location.blockstore.effectors;

import brooklyn.location.blockstore.api.MountedBlockDevice;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.brooklyn.api.effector.Effector;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.test.BrooklynAppLiveTestSupport;
import org.apache.brooklyn.entity.software.base.EmptySoftwareProcess;
import org.apache.brooklyn.entity.software.base.lifecycle.NaiveScriptRunner;
import org.apache.brooklyn.entity.software.base.lifecycle.ScriptHelper;
import org.apache.brooklyn.test.Asserts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.ExecutionException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertNotNull;

public class ExtraHddBodyEffectorLiveTest extends BrooklynAppLiveTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(ExtraHddBodyEffectorLiveTest.class);

    protected Location jcloudsLocation;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        super.setUp();

        jcloudsLocation = mgmt.getLocationRegistry().getLocationManaged("jclouds:aws-ec2:eu-west-1", ImmutableMap.<String, Object>builder()
                .put("osFamily", "centos")
                .put("imageId", "eu-west-1/ami-1d841c6a")
                .build());
    }

    @Test(groups = "Live")
    public void testEffectorWorksForSupportedClouds() throws ExecutionException, InterruptedException {
        EmptySoftwareProcess entity = app.createAndManageChild(EntitySpec.create(EmptySoftwareProcess.class)
                .addInitializer(new ExtraHddBodyEffector()));

        app.start(ImmutableList.of(jcloudsLocation));

        Effector<?> effector = entity.getEntityType().getEffectorByName(ExtraHddBodyEffector.EXTRA_HDD_EFFECTOR_NAME).get();

        String parameterInput = "{\n" +
                "  \"blockDevice\": {\n" +
                "    \"deviceSuffix\": \"h\",\n" +
                "    \"sizeInGb\": 2,\n" +
                "    \"deleteOnTermination\": true,\n" +
                "    \"tags\": {\n" +
                "      \"brooklyn\": \"br-test-1\"\n" +
                "    }\n" +
                "  },\n" +
                "  \"filesystem\": {\n" +
                "    \"mountPoint\": \"/mount/brooklyn/h\",\n" +
                "    \"filesystemType\": \"ext3\"\n" +
                "  }\n" +
                "}";

        ScriptHelper scriptHelper = new ScriptHelper((NaiveScriptRunner) entity.getDriver(),
                "Checking machine disks").body.append("df").gatherOutput();

        scriptHelper.execute();
        assertFalse(scriptHelper.getResultStdout().contains("/mount/brooklyn/h"));

        MountedBlockDevice result = (MountedBlockDevice) entity.invoke(effector, ImmutableMap.<String, Object>of(
                ExtraHddBodyEffector.LOCATION_CUSTOMIZER_FIELDS.getName(), parameterInput)).get();
        assertNotNull(result);
        assertEquals(result.getMountPoint(), "/mount/brooklyn/h");
        assertEquals(result.getDeviceName(), "/dev/sdh");
        assertNotEquals(entity.getLocations().size(), 0);
        assertEquals(result.getMachine(), entity.getLocations().iterator().next());

        scriptHelper.execute();
        assertTrue(scriptHelper.getResultStdout().contains("/mount/brooklyn/h"));
    }

    @Test(groups = "Live")
    public void testEffectorDoesNotWorkForNotSupportedClouds() throws Exception {

        jcloudsLocation = mgmt.getLocationRegistry().getLocationManaged("jclouds:softlayer", ImmutableMap.<String, Object>builder()
                .put("imageimageNameRegexId", "25G CentOS 6 64-bit")
                .build());

        EmptySoftwareProcess entity = app.createAndManageChild(EntitySpec.create(EmptySoftwareProcess.class)
                .addInitializer(new ExtraHddBodyEffector()));

        app.start(ImmutableList.of(jcloudsLocation));

        Effector<?> effector = entity.getEntityType().getEffectorByName(ExtraHddBodyEffector.EXTRA_HDD_EFFECTOR_NAME).get();

        String parameterInput = "{\n" +
                "  \"blockDevice\": {\n" +
                "    \"deviceSuffix\": \"h\",\n" +
                "    \"sizeInGb\": 2,\n" +
                "    \"deleteOnTermination\": true,\n" +
                "    \"tags\": {\n" +
                "      \"brooklyn\": \"br-test-1\"\n" +
                "    }\n" +
                "  },\n" +
                "  \"filesystem\": {\n" +
                "    \"mountPoint\": \"/mount/brooklyn/h\",\n" +
                "    \"filesystemType\": \"ext3\"\n" +
                "  }\n" +
                "}";

        try {
            entity.invoke(effector, ImmutableMap.<String, Object>of(ExtraHddBodyEffector.LOCATION_CUSTOMIZER_FIELDS.getName(), parameterInput)).get();
            Asserts.shouldHaveFailedPreviously("Tried to invoke addExtraHdd effector on entity");
        } catch (Exception e) {
            Asserts.expectedFailureOfType(e, UnsupportedOperationException.class);
            Asserts.expectedFailureContains(e, "which does not support adding disks from an effector.");
        }
    }
}
