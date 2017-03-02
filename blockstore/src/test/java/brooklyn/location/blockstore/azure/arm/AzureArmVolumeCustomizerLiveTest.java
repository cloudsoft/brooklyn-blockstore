package brooklyn.location.blockstore.azure.arm;

import com.google.common.base.Optional;

import brooklyn.location.blockstore.AbstractVolumeCustomizerLiveTest;

/**
 * Assumes named location exists in {@code ~/.brooklyn/brooklyn.properties} 
 * (see {@link AzureArmVolumeManagerLiveTest}).
 */
public class AzureArmVolumeCustomizerLiveTest extends AbstractVolumeCustomizerLiveTest {

    public static final String NAMED_LOCATION = AzureArmVolumeManagerLiveTest.NAMED_LOCATION;

    @Override
    protected Optional<String> namedLocation() {
        return Optional.of(NAMED_LOCATION);
    }

    @Override
    protected int getVolumeSize() {
        return 1;
    }

    @Override
    protected char getDefaultDeviceSuffix() {
        // See {@link AzureArmVolumeManagerLiveTest#getDefaultDeviceSuffix()}
        return 'c';
    }
}
