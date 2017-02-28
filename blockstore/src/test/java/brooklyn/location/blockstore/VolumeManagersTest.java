package brooklyn.location.blockstore;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.location.MachineLocation;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.location.SimulatedLocation;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.location.blockstore.azure.arm.AzureArmVolumeManager;
import brooklyn.location.blockstore.ec2.Ec2VolumeManager;
import brooklyn.location.blockstore.gce.GoogleComputeEngineVolumeManager;
import brooklyn.location.blockstore.openstack.OpenStackLocationConfig;
import brooklyn.location.blockstore.openstack.OpenstackVolumeManager;
import brooklyn.location.blockstore.openstack.RackspaceVolumeManagerLiveTest;
import brooklyn.location.blockstore.rackspace.RackspaceVolumeManager;

public class VolumeManagersTest {

    private LocalManagementContext ctx;

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        ctx = new LocalManagementContext();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        if (ctx != null) {
            Entities.destroyAll(ctx);
            ctx = null;
        }
    }

    @Test
    public void testEc2VolumeManager() {
        JcloudsLocation ec2Location = locationFor("aws-ec2:us-east-1c");
        assertTrue(VolumeManagers.isVolumeManagerSupportedForLocation(ec2Location));
        assertEquals(VolumeManagers.newVolumeManager(ec2Location).getClass(), Ec2VolumeManager.class);
    }

    @Test
    public void testAzureArmVolumeManager() {
        JcloudsLocation azureArmLocation = locationFor("azure-arm:westeurope");
        assertTrue(VolumeManagers.isVolumeManagerSupportedForLocation(azureArmLocation));
        assertEquals(VolumeManagers.newVolumeManager(azureArmLocation).getClass(), AzureArmVolumeManager.class);
    }

    @Test
    public void testGCEVolumeManager() {
        JcloudsLocation gceLocation = locationFor("google-compute-engine");
        assertTrue(VolumeManagers.isVolumeManagerSupportedForLocation(gceLocation));
        assertEquals(VolumeManagers.newVolumeManager(gceLocation).getClass(), GoogleComputeEngineVolumeManager.class);
    }

    @Test
    public void testRackspaceVolumeManager() {
        JcloudsLocation rackspaceLocation = locationFor(RackspaceVolumeManagerLiveTest.LOCATION_SPEC);
        assertTrue(VolumeManagers.isVolumeManagerSupportedForLocation(rackspaceLocation));
        assertEquals(VolumeManagers.newVolumeManager(rackspaceLocation).getClass(), RackspaceVolumeManager.class);
    }

    @Test
    public void testOpenstackVolumeManager() {
        JcloudsLocation openstackLocation = locationFor(OpenStackLocationConfig.LOCATION_SPEC);
        assertTrue(VolumeManagers.isVolumeManagerSupportedForLocation(openstackLocation));
        assertEquals(VolumeManagers.newVolumeManager(openstackLocation).getClass(), OpenstackVolumeManager.class);
    }

    @Test(expectedExceptions = {IllegalArgumentException.class})
    public void testNewVolumeManagerThrowsOnInvalidLocation() {
        MachineLocation loc = ctx.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class));
        VolumeManagers.newVolumeManager(loc);
    }

    private JcloudsLocation locationFor(String spec) {
        return JcloudsLocation.class.cast(ctx.getLocationRegistry().getLocationManaged(spec));
    }

}
