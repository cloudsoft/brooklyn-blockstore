package brooklyn.location.blockstore.azure.arm;

import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsSshMachineLocation;
import org.testng.annotations.Test;

import com.google.common.base.Optional;

import brooklyn.location.blockstore.AbstractVolumeManagerLiveTest;
import brooklyn.location.blockstore.api.BlockDevice;

/**
 * Assumes that {@code ~/.brooklyn/brooklyn.properties} specifies a "named location" for
 * azurecompute-arm, including the identity and credential. For example:
 * 
 * <pre>
 * brooklyn.location.named.azurecompute-arm-for-blockstore=jclouds:azurecompute-arm
 * brooklyn.location.named.azurecompute-arm-for-blockstore.identity=11111111-2222-3333-4444-555555555555
 * brooklyn.location.named.azurecompute-arm-for-blockstore.credential=pa55w0rd
 * brooklyn.location.named.azurecompute-arm-for-blockstore.endpoint=https://management.azure.com/subscriptions/22222222-3333-4444-5555-666666666666
 * brooklyn.location.named.azurecompute-arm-for-blockstore.oauth.endpoint=https://login.microsoftonline.com/33333333-4444-5555-6666-777777777777/oauth2/token
 * brooklyn.location.named.azurecompute-arm-for-blockstore.region=northeurope
 * brooklyn.location.named.azurecompute-arm-for-blockstore.vmNameMaxLength=45
 * brooklyn.location.named.azurecompute-arm-for-blockstore.jclouds.azurecompute.arm.publishers=OpenLogic
 * brooklyn.location.named.azurecompute-arm-for-blockstore.jclouds.azurecompute.operation.timeout=120000
 * brooklyn.location.named.azurecompute-arm-for-blockstore.osFamil=centos
 * brooklyn.location.named.azurecompute-arm-for-blockstore.osVersionRegex=7
 * brooklyn.location.named.azurecompute-arm-for-blockstore.minRam=2000
 * brooklyn.location.named.azurecompute-arm-for-blockstore.loginUser=qaframework
 * brooklyn.location.named.azurecompute-arm-for-blockstore.templateOptions={overrideAuthenticateSudo: true}
 * brooklyn.location.named.azurecompute-arm-for-blockstore.machineCreateAttempts=1
 * }
 * </pre>
 * 
 * TODO Unify the way that the live tests expect to get their credentials (e.g. via system 
 * properties, or hard-coded in brooklyn.properties as named locations, etc).
 */
@Test(groups = "Live")
public class AzureArmVolumeManagerLiveTest extends AbstractVolumeManagerLiveTest {

    public static final String PROVIDER = "azurecompute-arm";
    public static final String NAMED_LOCATION = System.getProperty("azurecompute-arm.named-location", "azurecompute-arm-for-blockstore");
    
    @Override
    protected String getProvider() {
        return PROVIDER;
    }

    @Override
    protected Optional<String> namedLocation() {
        return Optional.of(NAMED_LOCATION);
    }

    @Override
    protected JcloudsLocation createJcloudsLocation() {
        return (JcloudsLocation) ctx.getLocationRegistry().getLocationManaged(NAMED_LOCATION);
    }
    
    // Does not do the attach+mount twice (because not supported in azure yet)
    @Test(groups="Live")
    public void testCreateAndAttachVolume() throws Exception {
        runCreateAndAttachVolume(ReattachMode.NO_OP);
    }

    @Test(enabled=false)
    public void testCreateVolume() throws Exception {
        throw new UnsupportedOperationException("This is not possible in Azure ARM (using 'unmanaged disk' api). A volume is bound to the VM at creation time.");
    }

    @Test(enabled=false)
    @Override
    public void testMoveMountedVolumeToAnotherMachine() throws Throwable {
        throw new UnsupportedOperationException("This is not possible in Azure ARM (using 'unmanaged disk' api). A volume is bound to the VM and cannot be transferred.");
    }

    @Override
    protected void assertVolumeAvailable(final BlockDevice blockDevice) {
        throw new UnsupportedOperationException("Unattached volumes not supported in Azure ARM (using 'unmanaged disk' api). A volume is bound to the VM and cannot be transferred.");
    }

    @Override
    protected int getVolumeSize() {
        return 1;
    }

    @Override
    protected char getDefaultDeviceSuffix() {
        // When using image "northeurope/OpenLogic/CentOS-HPC/7.1", the attached disk is at /dev/sdc
        return 'c';
    }
    
    @Override
    protected JcloudsSshMachineLocation createJcloudsMachine() throws Exception {
        return (JcloudsSshMachineLocation) jcloudsLocation.obtain();
    }
    
    /**
     * Not required - will be inferred in tests from JcloudsMachineLocation
     */
    @Override
    protected String getDefaultAvailabilityZone() {
        return null;
    }

    @Override
    protected Optional<JcloudsSshMachineLocation> rebindJcloudsMachine() {
        return Optional.absent();
        
        /*
         * e.g.
         *   Map<String, ?> machineFlags = MutableMap.of(
         *           "id", "northeurope/brooklynom8fdsaledsage-a21",
         *           "hostname", "13.74.14.150",
         *           "user", "aledsage");
         *   ConfigBag config = ConfigBag.newInstanceCopying(jcloudsLocation.getRawLocalConfigBag()).putAll(machineFlags);
         *   JcloudsMachineLocation machine = jcloudsLocation.registerMachine(config);
         *   return Optional.of((JcloudsSshMachineLocation)machine);
         */
    }
}
