package brooklyn.location.blockstore.effectors;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.brooklyn.api.effector.Effector;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.test.BrooklynAppLiveTestSupport;
import org.apache.brooklyn.entity.software.base.EmptySoftwareProcess;
import org.apache.brooklyn.test.Asserts;
import org.testng.annotations.Test;

// TODO make unit test
public class TestEffectorDoesNotWorkForNotSupportedCloudsLiveTest extends BrooklynAppLiveTestSupport {
    protected Location jcloudsLocation;

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
