package brooklyn.location.blockstore.openstack;

import brooklyn.location.blockstore.NewVolumeCustomizer;
import brooklyn.location.blockstore.api.VolumeOptions;
import com.google.common.collect.ImmutableList;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.test.BrooklynAppLiveTestSupport;
import org.apache.brooklyn.entity.machine.MachineEntity;
import org.apache.brooklyn.entity.software.base.EmptySoftwareProcess;
import org.apache.brooklyn.entity.software.base.lifecycle.NaiveScriptRunner;
import org.apache.brooklyn.entity.software.base.lifecycle.ScriptHelper;
import org.apache.brooklyn.location.jclouds.JcloudsLocationConfig;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.Assert.assertTrue;

/**
 * Requires {@code -da} (i.e. disable assertions) due to bug in jclouds openstack-nova, 
 * for not properly cloning template options.
 * 
 * One also needs to supply cloud credentials:
 * <pre>
 * {@code
 * -Dbrooklyn.location.jclouds.openstack-nova.endpoint="https://openstack.example.com:5000/v2.0"
 * -Dbrooklyn.location.jclouds.openstack-nova.identity="your-tenant:your-username"
 * -Dbrooklyn.location.jclouds.openstack-nova.credential="your-password"
 * -Dbrooklyn.location.jclouds.openstack-nova.auto-generate-keypairs=false
 * -Dbrooklyn.location.jclouds.openstack-nova.keyPair=your-keypair
 * -Dbrooklyn.location.jclouds.openstack-nova.loginUser.privateKeyFile=~/.ssh/your-keypair.pem
 * -Dbrooklyn.location.jclouds.openstack-nova.jclouds.keystone.credential-type=passwordCredentials
 * -Dbrooklyn.location.jclouds.openstack-cinder.identity="your-tenant:your-username"
 * -Dbrooklyn.location.jclouds.openstack-cinder.credential="your-password"
 * }
 * </pre>
 * 
 * Or alternatively these can be hard-coded in ~/.brooklyn/brooklyn.properties (i.e. without the 
 * {@code -D} in the lines above).
 */
public class OpenStackNewVolumeCustomizerLiveTest extends BrooklynAppLiveTestSupport {

    protected Location jcloudsLocation;

    @Test(groups = "Live")
    public void testCustomizerCreatesAndAttachesNewVolumeOnProvisioningTime() {
        OpenStackLocationConfig.addBrooklynProperties(mgmt.getBrooklynProperties());
        Map<?, ?> locationConfig = new OpenStackLocationConfig().getConfigMap();

        jcloudsLocation = mgmt.getLocationRegistry().getLocationManaged(OpenStackLocationConfig.NAMED_LOCATION, locationConfig);

        NewVolumeCustomizer customizer = new NewVolumeCustomizer();
        customizer.setVolumes(MutableList.of(
                VolumeOptions.fromMap(
                        MutableMap.<String, Map<String,?>>of(
                                "blockDevice", MutableMap.of(
                                        "sizeInGb", 3,
                                        "deviceSuffix", 'b',
                                        "deleteOnTermination", true),
                                "filesystem", MutableMap.<String, Object>of(
                                        "mountPoint", "/mount/brooklyn/b",
                                        "filesystemType", "ext3"))),
                VolumeOptions.fromMap(
                        MutableMap.<String, Map<String, ?>>of(
                                "blockDevice", MutableMap.<String, Object>of(
                                    "sizeInGb", 3,
                                    "deviceSuffix", 'c',
                                    "deleteOnTermination", true),
                                "filesystem", MutableMap.<String, Object>of(
                                        "mountPoint", "/mount/brooklyn/c",
                                        "filesystemType", "ext3")))));

        EmptySoftwareProcess entity = app.createAndManageChild(EntitySpec.create(EmptySoftwareProcess.class)
                .configure(MachineEntity.PROVISIONING_PROPERTIES.subKey(JcloudsLocationConfig.JCLOUDS_LOCATION_CUSTOMIZERS.getName()),
                        MutableList.of(customizer)));

        app.start(ImmutableList.of(jcloudsLocation));

        ScriptHelper scriptHelper = new ScriptHelper((NaiveScriptRunner) entity.getDriver(),
                "Checking machine disks").body.append("df").gatherOutput();

        scriptHelper.execute();
        assertTrue(scriptHelper.getResultStdout().contains("/mount/brooklyn/b"));
    }

}
