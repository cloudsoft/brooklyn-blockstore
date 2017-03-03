package brooklyn.location.blockstore;

import static brooklyn.location.blockstore.AbstractVolumeManagerLiveTest.assertDfIncludesMountPoint;
import static brooklyn.location.blockstore.AbstractVolumeManagerLiveTest.assertMountPointExists;
import static brooklyn.location.blockstore.AbstractVolumeManagerLiveTest.assertReadable;
import static brooklyn.location.blockstore.AbstractVolumeManagerLiveTest.assertWritable;

import java.util.Collection;
import java.util.Map;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.entity.BrooklynConfigKeys;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.location.Machines;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.entity.machine.MachineEntity;
import org.apache.brooklyn.entity.software.base.EmptySoftwareProcess;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsLocationConfig;
import org.apache.brooklyn.location.jclouds.JcloudsMachineLocation;
import org.apache.brooklyn.location.jclouds.JcloudsSshMachineLocation;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import brooklyn.location.blockstore.api.VolumeOptions;

public abstract class AbstractVolumeCustomizerLiveTest {

    // FIXME Delete volume? Or will it automatically be deleted when VM is deleted for all clouds?!
    //       Should assert that it's deleted.
    
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(AbstractVolumeCustomizerLiveTest.class);

    public static final String BROOKLYN_PROPERTIES_JCLOUDS_PREFIX = "brooklyn.location.jclouds.";
    public static final String BROOKLYN_PROPERTIES_JCLOUDS_LEGACY_PREFIX = "brooklyn.jclouds.";

    protected BrooklynProperties brooklynProperties;
    protected ManagementContext mgmt;
    
    protected JcloudsLocation jcloudsLocation;

    protected abstract int getVolumeSize();
    protected abstract char getDefaultDeviceSuffix();

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        brooklynProperties = BrooklynProperties.Factory.newDefault();
        AbstractVolumeManagerLiveTest.stripBrooklynProperties(brooklynProperties, namedLocation());
        addBrooklynProperties(brooklynProperties);
        mgmt = new LocalManagementContextForTests(brooklynProperties);
        
        jcloudsLocation = createJcloudsLocation();
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        try {
            if (jcloudsLocation != null) {
                Collection<Location> machines = jcloudsLocation.getChildren();
                for (Location machine : machines) {
                    if (machine instanceof JcloudsMachineLocation) {
                        jcloudsLocation.release((JcloudsMachineLocation)machine);
                    }
                }
            }
        } finally {
            if (mgmt != null) {
                Entities.destroyAllCatching(mgmt);
                mgmt = null;
            }
        }
    }

    protected Optional<String> namedLocation() {
        return Optional.absent();
    }

    protected String locationSpec() {
        if (namedLocation().isPresent()) {
            return namedLocation().get();
        } else {
            throw new UnsupportedOperationException("sub-class must override namedLocation() or locationSpec()");
        }
    }

    protected JcloudsLocation createJcloudsLocation() throws Exception {
        return (JcloudsLocation) mgmt.getLocationRegistry().getLocationManaged(locationSpec(), additionalObtainArgs());
    }

    protected void addBrooklynProperties(BrooklynProperties props) {
        // no-op; for overriding
    }

    protected Map<?, ?> additionalObtainArgs() throws Exception {
        return ImmutableMap.of();
    }

    @Test(groups = "Live")
    public void testCustomizerCreatesAndAttachesNewVolumeOnProvisioningTime() throws Exception {
        String mountPoint = "/mount/brooklyn/" + getDefaultDeviceSuffix();
        
        NewVolumeCustomizer customizer = new NewVolumeCustomizer();
        customizer.setVolumes(MutableList.of(
                VolumeOptions.fromMap(MutableMap.<String, Map<String,?>>of(
                        "blockDevice", MutableMap.of(
                            "sizeInGb", getVolumeSize(),
                            "deviceSuffix", getDefaultDeviceSuffix(),
                            "deleteOnTermination", true,
                            "tags", ImmutableMap.of(
                                    "user", System.getProperty("user.name"),
                                    "purpose", "brooklyn-blockstore-VolumeCustomizerLiveTest")
                        ),
                        "filesystem", MutableMap.of(
                                "mountPoint", mountPoint,
                                "filesystemType", "ext3"
                        )))));

        TestApplication app = mgmt.getEntityManager().createEntity(EntitySpec.create(TestApplication.class)
                .configure(BrooklynConfigKeys.SKIP_ON_BOX_BASE_DIR_RESOLUTION, true));

        EmptySoftwareProcess entity = app.createAndManageChild(EntitySpec.create(EmptySoftwareProcess.class)
                .configure(MachineEntity.PROVISIONING_PROPERTIES.subKey(JcloudsLocationConfig.JCLOUDS_LOCATION_CUSTOMIZERS.getName()),
                        MutableList.of(customizer)));

        app.start(ImmutableList.of(jcloudsLocation));

        JcloudsSshMachineLocation machine = Machines.findUniqueMachineLocation(entity.getLocations(), JcloudsSshMachineLocation.class).get();
        String destFile = mountPoint+"/myfile.txt";
        assertMountPointExists(machine, mountPoint);
        assertDfIncludesMountPoint(machine, mountPoint);
        assertWritable(machine, destFile, "abc".getBytes());
        assertReadable(machine, destFile, "abc".getBytes());
    }
}
