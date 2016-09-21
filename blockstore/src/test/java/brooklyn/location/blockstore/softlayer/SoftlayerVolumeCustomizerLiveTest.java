package brooklyn.location.blockstore.softlayer;

import java.util.List;
import java.util.Map;

import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import brooklyn.location.blockstore.AbstractVolumeCustomizerLiveTest;

@Test
public class SoftlayerVolumeCustomizerLiveTest extends AbstractVolumeCustomizerLiveTest {

    public static final String PROVIDER = "softlayer";
    public static final String REGION_NAME = "ams01";
    public static final String LOCATION_SPEC = PROVIDER + (REGION_NAME == null ? "" : ":" + REGION_NAME);
    public static final String IMAGE_ID = "CENTOS_6_64";
    
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
        return 25;
    }

    @Override
    protected int maxTagLength() {
        return 20;
    }

    @Override
    protected Map<?, ?> additionalObtainArgs() throws Exception {
        return ImmutableMap.builder()
                .put(JcloudsLocation.IMAGE_ID, IMAGE_ID)
                .build();
    }

    @Override
    protected List<String> getMountPoints() {
        return ImmutableList.of("/mnt/somewhere", "/mnt/somewhere2");
    }
}
