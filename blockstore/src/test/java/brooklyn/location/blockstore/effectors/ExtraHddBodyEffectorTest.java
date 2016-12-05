package brooklyn.location.blockstore.effectors;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import java.util.List;

import org.apache.brooklyn.api.effector.Effector;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.camp.brooklyn.AbstractYamlTest;
import org.apache.brooklyn.core.effector.AddEffector;
import org.apache.brooklyn.core.effector.Effectors;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsMachineLocation;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.testng.annotations.Test;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import brooklyn.location.blockstore.BlockDeviceOptions;
import brooklyn.location.blockstore.FilesystemOptions;
import brooklyn.location.blockstore.api.AttachedBlockDevice;
import brooklyn.location.blockstore.api.MountedBlockDevice;
import brooklyn.location.blockstore.api.VolumeOptions;
import brooklyn.location.blockstore.effectors.ExtraHddBodyEffectorTest.StubExtraHddBodyEffector.RecordingBody;

public class ExtraHddBodyEffectorTest extends AbstractYamlTest {

    @Test
    public void testEffectorIsProperlyAttached() throws Exception {
        Entity app = createAndStartApplication(
                "services:",
                "- type: " + TestEntity.class.getName(),
                "  brooklyn.initializers:",
                "  - type: brooklyn.location.blockstore.effectors.ExtraHddBodyEffector");
        waitForApplicationTasks(app);

        TestEntity entity = (TestEntity) Iterables.getOnlyElement(app.getChildren());
        Effector<?> effector = entity.getEntityType().getEffectorByName(ExtraHddBodyEffector.EXTRA_HDD_EFFECTOR_NAME).get();
        
        assertEquals("addExtraHdd", effector.getName());
        assertEquals(effector.getDescription(), "An effector to add extra hdd to provisioned vm");
        assertEquals(MountedBlockDevice.class, effector.getReturnType());
        assertEquals(1, effector.getParameters().size());
        assertEquals(effector.getParameters().get(0).getName(), "volume");
        assertEquals(effector.getParameters().get(0).getParameterClass(), VolumeOptions.class);
    }

    // Run with -da (disable assertions) due to bug in jclouds openstack-nova for not properly cloning template options
    @Test
    public void testEffectorFailsForLocationsNotOfJcloudsMachineLocationType() throws Exception {

        Entity app = createAndStartApplication(
                "location:",
                "  localhost",
                "services:",
                "- type: " + TestEntity.class.getName(),
                "  brooklyn.initializers:",
                "  - type: brooklyn.location.blockstore.effectors.ExtraHddBodyEffector");
        waitForApplicationTasks(app);

        TestEntity entity = (TestEntity) Iterables.getOnlyElement(app.getChildren());
        Effector<?> effector = entity.getEntityType().getEffectorByName(ExtraHddBodyEffector.EXTRA_HDD_EFFECTOR_NAME).get();

        String parameterInput = Joiner.on("\n").join(
                "{",
                "  \"blockDevice\": {",
                "    \"deviceSuffix\": \"h\",",
                "    \"sizeInGb\": 4,",
                "    \"deleteOnTermination\": true,",
                "    \"tags\": {",
                "      \"brooklyn\": \"br-test-1\"",
                "    }",
                "  },",
                "  \"filesystem\": {",
                "    \"mountPoint\": \"/mount/brooklyn/h\",",
                "    \"filesystemType\": \"ext3\"",
                "  }",
                "}");

        try {
            entity.invoke(effector, ImmutableMap.<String, Object>of(ExtraHddBodyEffector.VOLUME.getName(), parameterInput)).get();
            Asserts.shouldHaveFailedPreviously();
        } catch (Exception e) {
            Asserts.expectedFailureOfType(e, IllegalStateException.class);
            Asserts.expectedFailureContains(e, "requires a single " + JcloudsMachineLocation.class.getName() + ", but has []");
        }
    }
    
    @Test
    public void testEffectorDeserializesVolumeOptions() throws Exception {
        String yaml = Joiner.on("\n").join(
                "services:",
                "- type: " + TestEntity.class.getName(),
                "  brooklyn.initializers:",
                "  - type: " + StubExtraHddBodyEffector.class.getName());
        
        String jsonParam = Joiner.on("\n").join(
                "{",
                "  \"blockDevice\": {",
                "      \"sizeInGb\": 1,",
                "      \"deleteOnTermination\": true,",
                "      \"deviceSuffix\": \"z\",",
                "      \"tags\": {",
                "          \"tag1\": \"val1\"",
                "      }",
                "  },",
                "  \"filesystem\": {",
                "      \"mountPoint\": \"/my/mount/point\",",
                "      \"filesystemType\": \"ext3\"",
                "  }",
                "}");

        Entity app = createAndStartApplication(yaml);
        Entity entity = Iterables.getOnlyElement(app.getChildren());
        Effector<?> effector = entity.getEntityType().getEffectorByName(ExtraHddBodyEffector.EXTRA_HDD_EFFECTOR_NAME).get();
        
        MountedBlockDevice result = (MountedBlockDevice) entity.invoke(effector, ImmutableMap.of("volume", jsonParam)).get();
        assertSame(result, StubMountedBlockDevice.INSTANCE);
        
        VolumeOptions volumeOptions = RecordingBody.getLastCall();
        BlockDeviceOptions blockDevice = volumeOptions.getBlockDeviceOptions();
        FilesystemOptions filesystemOptions = volumeOptions.getFilesystemOptions();
        assertEquals(blockDevice.getSizeInGb(), 1);
        assertEquals(blockDevice.deleteOnTermination(), true);
        assertEquals(blockDevice.getDeviceSuffix(), 'z');
        assertEquals(blockDevice.getTags(), ImmutableMap.of("tag1", "val1"));
        assertEquals(filesystemOptions.getMountPoint(), "/my/mount/point");
        assertEquals(filesystemOptions.getFilesystemType(), "ext3");
    }

    // Very similar to ExtraHddBodyEffector, but replaces the effector "body" with a stub.
    // This stub just calls parameters.get(volume).
    public static class StubExtraHddBodyEffector extends AddEffector {

        public StubExtraHddBodyEffector() {
            super(newEffectorBuilder().build());
        }

        public static Effectors.EffectorBuilder<MountedBlockDevice> newEffectorBuilder() {
            return ExtraHddBodyEffector.newEffectorBuilder()
                .impl(new RecordingBody());
        }

        @Override
        public void apply(EntityLocal entity) {
            super.apply(entity);
        }

        public static class RecordingBody extends ExtraHddBodyEffector.Body {
            private static final List<VolumeOptions> calls = Lists.newCopyOnWriteArrayList();

            public static VolumeOptions getLastCall() {
                return calls.get(calls.size() - 1);
            }

            public static void clear() {
                calls.clear();
            }

            @Override
            public MountedBlockDevice call(ConfigBag parameters) {
                VolumeOptions volumeOptions = parameters.get(ExtraHddBodyEffector.VOLUME);
                calls.add(volumeOptions);
                return StubMountedBlockDevice.INSTANCE;
            }
        }
    }
    
    public static class StubMountedBlockDevice implements MountedBlockDevice {
        public static final StubMountedBlockDevice INSTANCE = new StubMountedBlockDevice();
        
        @Override public String getDeviceName() {
            return null;
        }
        @Override public char getDeviceSuffix() {
            return 0;
        }
        @Override public JcloudsMachineLocation getMachine() {
            return null;
        }
        @Override public MountedBlockDevice mountedAt(String mountPoint) {
            return null;
        }
        @Override public String getId() {
            return null;
        }
        @Override public JcloudsLocation getLocation() {
            return null;
        }
        @Override public AttachedBlockDevice attachedTo(JcloudsMachineLocation machine, String deviceName) {
            return null;
        }
        @Override public String getMountPoint() {
            return null;
        }
    }
}
