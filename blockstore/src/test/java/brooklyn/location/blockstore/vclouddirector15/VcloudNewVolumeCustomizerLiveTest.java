package brooklyn.location.blockstore.vclouddirector15;

import brooklyn.location.blockstore.AbstractVolumeCustomizerLiveTest;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

/**
 * Use named location.
 *
 * TODO How to inject cloud credentials?
 * 
 * TODO Test fails because {@link #additionalObtainArgs()} returns null, causing a NullPointerException.
 */
@Test(groups = {"Live", "WIP"})
public class VcloudNewVolumeCustomizerLiveTest extends AbstractVolumeCustomizerLiveTest {

    @Override
    protected String getProvider() {
        return null;
    }

    @Override
    protected JcloudsLocation createJcloudsLocation() {
        return null;
    }

    @Override
    protected int getVolumeSize() {
        return 32;
    }

    @Override
    protected List<String> getMountPoints() {
        return null;
    }

    @Override
    protected Map<?, ?> additionalObtainArgs() throws Exception {
        return null;
    }
}
