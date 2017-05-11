package brooklyn.location.blockstore.effectors;

import org.apache.brooklyn.api.effector.Effector;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.entity.BrooklynConfigKeys;
import org.apache.brooklyn.core.test.BrooklynAppLiveTestSupport;
import org.apache.brooklyn.entity.software.base.EmptySoftwareProcess;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.test.Asserts;
import org.testng.annotations.Test;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import brooklyn.location.blockstore.softlayer.SoftlayerVolumeCustomizerLiveTest;

// TODO make unit test
public class UnsupportedCloudExtraHddBodyEffectorLiveTest extends BrooklynAppLiveTestSupport {

    @Test(groups = "Live")
    public void testEffectorDoesNotWorkForNotSupportedClouds() throws Exception {
        Location jcloudsLocation = mgmt.getLocationRegistry().getLocationManaged("jclouds:softlayer", ImmutableMap.<Object, Object>builder()
                .put(JcloudsLocation.IMAGE_ID, SoftlayerVolumeCustomizerLiveTest.IMAGE_ID)
                .put(JcloudsLocation.WAIT_FOR_SSHABLE, false)
                .build());

        EmptySoftwareProcess entity = app.createAndManageChild(EntitySpec.create(EmptySoftwareProcess.class)
                .configure(BrooklynConfigKeys.SKIP_ON_BOX_BASE_DIR_RESOLUTION, true)
                .configure(EmptySoftwareProcess.USE_SSH_MONITORING, false)
                .addInitializer(new ExtraHddBodyEffector()));

        app.start(ImmutableList.of(jcloudsLocation));

        Effector<?> effector = entity.getEntityType().getEffectorByName(ExtraHddBodyEffector.EXTRA_HDD_EFFECTOR_NAME).get();

        String parameterInput = Joiner.on("\n").join(
                "{",
                "  \"blockDevice\": {",
                "    \"deviceSuffix\": \"h\",",
                "    \"sizeInGb\": 2,",
                "    \"deleteOnTermination\": true,",
                "    \"tags\": {",
                "      \"brooklyn\": \"br-test-1\"",
                "    }",
                "  },",
                "  \"filesystem\": {",
                "    \"mountPoint\": \"/mount/brooklyn/h\",",
                "    \"filesystemType\": \"ext4\"",
                "  }",
                "}");

        try {
            entity.invoke(effector, ImmutableMap.<String, Object>of(ExtraHddBodyEffector.VOLUME.getName(), parameterInput)).get();
            Asserts.shouldHaveFailedPreviously("Tried to invoke addExtraHdd effector on entity");
        } catch (Exception e) {
            Asserts.expectedFailureOfType(e, UnsupportedOperationException.class);
            Asserts.expectedFailureContains(e, "which is not supported for adding disks");
        }
    }
}
