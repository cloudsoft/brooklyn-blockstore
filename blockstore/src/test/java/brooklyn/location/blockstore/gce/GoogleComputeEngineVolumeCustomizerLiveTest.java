package brooklyn.location.blockstore.gce;

import java.util.Map;

import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;

import brooklyn.location.blockstore.AbstractVolumeCustomizerLiveTest;

/**
 * TODO How to inject credentials? The credentials in {@code .brooklyn/brooklyn.properties}
 * are stripped out by {@link AbstractVolumeCustomizerLiveTest#setUp()}.
 */
@Test(groups = "Live")
public class GoogleComputeEngineVolumeCustomizerLiveTest extends AbstractVolumeCustomizerLiveTest {

    public static final String LOCATION_SPEC = GoogleComputeEngineVolumeManagerLiveTest.LOCATION_SPEC;
    public static final String IMAGE_NAME_REGEX = GoogleComputeEngineVolumeManagerLiveTest.IMAGE_NAME_REGEX;
    
    @Override
    protected String locationSpec() {
        return LOCATION_SPEC;
    }

    @Override
    protected Map<?, ?> additionalObtainArgs() throws Exception {
        return ImmutableMap.builder()
                .put(JcloudsLocation.IMAGE_NAME_REGEX, IMAGE_NAME_REGEX)
                .build();
    }

    @Override
    protected int getVolumeSize() {
        return 1;
    }

    @Override
    protected char getDefaultDeviceSuffix() {
        throw new IllegalStateException("Not implemented. Figure out the correct device suffix.");
    }
}
