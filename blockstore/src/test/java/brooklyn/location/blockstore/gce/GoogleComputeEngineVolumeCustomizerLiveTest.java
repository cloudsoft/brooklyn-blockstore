package brooklyn.location.blockstore.gce;

import java.util.List;
import java.util.Map;

import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import brooklyn.location.blockstore.AbstractVolumeCustomizerLiveTest;

/**
 * TODO How to inject credentials? The credentials in {@code .brooklyn/brooklyn.properties}
 * are stripped out by {@link AbstractVolumeCustomizerLiveTest#setUp()}.
 */
@Test(groups = "Live")
public class GoogleComputeEngineVolumeCustomizerLiveTest extends AbstractVolumeCustomizerLiveTest {

    public static final String PROVIDER = GoogleComputeEngineVolumeManagerLiveTest.PROVIDER;
    public static final String LOCATION_SPEC = GoogleComputeEngineVolumeManagerLiveTest.LOCATION_SPEC;
    public static final String IMAGE_NAME_REGEX = GoogleComputeEngineVolumeManagerLiveTest.IMAGE_NAME_REGEX;
    
    @Override
    protected String getProvider() {
        return PROVIDER;
    }

    @Override
    protected JcloudsLocation createJcloudsLocation() {
        return (JcloudsLocation) ctx.getLocationRegistry().getLocationManaged(LOCATION_SPEC);
    }
    
    @Override
    protected int getVolumeSize() {
        return 1;
    }

    @Override
    protected Map<?, ?> additionalObtainArgs() throws Exception {
        return ImmutableMap.builder()
                .put(JcloudsLocation.IMAGE_NAME_REGEX, IMAGE_NAME_REGEX)
                .build();
    }

    @Override
    protected List<String> getMountPoints() {
        return ImmutableList.of("/mnt/somewhere", "/mnt/somewhere2");
    }
}
