package brooklyn.location.blockstore.vclouddirector15;

import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.testng.annotations.Test;

import com.google.common.base.Optional;

import brooklyn.location.blockstore.AbstractVolumeCustomizerLiveTest;

/**
 * TODO How to inject cloud credentials?
 * TODO Why VM image etc?
 */
@Test(groups = {"Live", "WIP"})
public class VcloudVolumeCustomizerLiveTest extends AbstractVolumeCustomizerLiveTest {

    @Override
    protected Optional<String> namedLocation() {
        return Optional.of(System.getProperty("vcloud-director.named-location"));
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
    protected char getDefaultDeviceSuffix() {
        throw new IllegalStateException("Not implemented. Figure out the correct device suffix.");
    }
}
