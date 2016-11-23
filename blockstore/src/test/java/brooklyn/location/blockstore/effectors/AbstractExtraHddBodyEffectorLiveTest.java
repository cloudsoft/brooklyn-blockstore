package brooklyn.location.blockstore.effectors;

import brooklyn.location.blockstore.api.MountedBlockDevice;
import brooklyn.location.blockstore.openstack.OpenStackLocationConfig;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.brooklyn.api.effector.Effector;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.test.BrooklynAppLiveTestSupport;
import org.apache.brooklyn.entity.software.base.EmptySoftwareProcess;
import org.apache.brooklyn.entity.software.base.lifecycle.NaiveScriptRunner;
import org.apache.brooklyn.entity.software.base.lifecycle.ScriptHelper;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.ExecutionException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;

public abstract class AbstractExtraHddBodyEffectorLiveTest extends BrooklynAppLiveTestSupport {

    protected JcloudsLocation jcloudsLocation;
    protected BrooklynProperties brooklynProperties;
    protected OpenStackLocationConfig locationConfig;

    protected abstract JcloudsLocation obtainJcloudsLocation();

    @BeforeMethod(alwaysRun = true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        jcloudsLocation = obtainJcloudsLocation();
    }

    @Test(groups = "Live")
    public void testEffectorWorksForWithTwoDisks() throws ExecutionException, InterruptedException {
        EmptySoftwareProcess entity = app.createAndManageChild(EntitySpec.create(EmptySoftwareProcess.class)
                .addInitializer(new ExtraHddBodyEffector()));
        app.start(ImmutableList.of(jcloudsLocation));

        Effector<?> effector = entity.getEntityType().getEffectorByName(ExtraHddBodyEffector.EXTRA_HDD_EFFECTOR_NAME).get();
        String parameterInput = "{\n" +
                "  \"blockDevice\": {\n" +
                "    \"deviceSuffix\": \"" + getFirstDeviceSuffix() + "\",\n" +
                "    \"sizeInGb\": 4,\n" +
                "    \"deleteOnTermination\": true,\n" +
                "    \"tags\": {\n" +
                "      \"brooklyn\": \"br-test-" + getFirstDeviceSuffix() + "\"\n" +
                "    }\n" +
                "  },\n" +
                "  \"filesystem\": {\n" +
                "    \"mountPoint\": \"" + getMountPointBaseDir() + "/" + getFirstDeviceSuffix() + "\",\n" +
                "    \"filesystemType\": \"ext3\"\n" +
                "  }\n" +
                "}";
        MountedBlockDevice result = (MountedBlockDevice) entity.invoke(effector, ImmutableMap.<String, Object>of(
                ExtraHddBodyEffector.LOCATION_CUSTOMIZER_FIELDS.getName(), parameterInput)).get();
        assertNotNull(result);
        assertEquals(result.getMountPoint(), getMountPointBaseDir() + "/" + getFirstDeviceSuffix());
        assertEquals(result.getDeviceName(), getDeviceNamePrefix() + getFirstDeviceSuffix());
        assertNotEquals(entity.getLocations().size(), 0);
        assertEquals(result.getMachine(), entity.getLocations().iterator().next());

        String parameterInputForSecondInvoke = "{\n" +
                "  \"blockDevice\": {\n" +
                "    \"deviceSuffix\": \"" + (char)(getFirstDeviceSuffix() + 1) + "\",\n" +
                "    \"sizeInGb\": 16,\n" +
                "    \"deleteOnTermination\": true,\n" +
                "    \"tags\": {\n" +
                "      \"brooklyn\": \"br-test-"+(char)(getFirstDeviceSuffix() + 1)+"\"\n" +
                "    }\n" +
                "  },\n" +
                "  \"filesystem\": {\n" +
                "    \"mountPoint\": \"" + getMountPointBaseDir() + "\",\n" +
                "    \"filesystemType\": \"ext3\"\n" +
                "  }\n" +
                "}";
        MountedBlockDevice resultFromSecondInvoke = (MountedBlockDevice) entity.invoke(effector, ImmutableMap.<String, Object>of(
                ExtraHddBodyEffector.LOCATION_CUSTOMIZER_FIELDS.getName(), parameterInputForSecondInvoke)).get();
        assertNotNull(resultFromSecondInvoke);
        assertEquals(resultFromSecondInvoke.getMountPoint(), getMountPointBaseDir());
        assertEquals(resultFromSecondInvoke.getDeviceName(), getDeviceNamePrefix() + (char)(getFirstDeviceSuffix() + 1));
        assertNotEquals(entity.getLocations().size(), 0);
        assertEquals(resultFromSecondInvoke.getMachine(), entity.getLocations().iterator().next());

        ScriptHelper scriptHelper = new ScriptHelper((NaiveScriptRunner) entity.getDriver(),
                "Checking machine disks").body.append("df").gatherOutput();
        scriptHelper.execute();
        assertFalse(scriptHelper.getResultStdout().contains(getMountPointBaseDir() + "/" + getFirstDeviceSuffix()));
        assertFalse(scriptHelper.getResultStdout().contains(getMountPointBaseDir() + "/" + (char)(getFirstDeviceSuffix()+1)));
    }

    protected abstract String getMountPointBaseDir();

    protected abstract String getDeviceNamePrefix();

    protected abstract char getFirstDeviceSuffix();
}
