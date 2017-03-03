package brooklyn.location.blockstore.softlayer;

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
public class SoftlayerVolumeCustomizerLiveTest extends AbstractVolumeCustomizerLiveTest {

    public static final String PROVIDER = "softlayer";
    public static final String REGION_NAME = "ams01";
    public static final String LOCATION_SPEC = PROVIDER + (REGION_NAME == null ? "" : ":" + REGION_NAME);
    public static final String IMAGE_ID = "CENTOS_6_64";
    
    @Override
    protected String locationSpec() {
        return LOCATION_SPEC;
    }
    
    @Override
    protected int getVolumeSize() {
        return 25;
    }

    @Override
    protected Map<?, ?> additionalObtainArgs() throws Exception {
        return ImmutableMap.builder()
                .put(JcloudsLocation.IMAGE_ID, IMAGE_ID)
                .build();
    }

    @Override
    protected char getDefaultDeviceSuffix() {
        throw new IllegalStateException("Not implemented. Figure out the correct device suffix.");
    }
}
