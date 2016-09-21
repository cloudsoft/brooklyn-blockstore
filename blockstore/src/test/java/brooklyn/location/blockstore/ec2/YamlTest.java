package brooklyn.location.blockstore.ec2;

import static org.testng.Assert.assertEquals;

import java.util.Map;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.camp.brooklyn.AbstractYamlTest;
import org.apache.brooklyn.entity.stock.BasicApplication;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.testng.annotations.Test;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

import brooklyn.location.blockstore.BlockDeviceOptions;
import brooklyn.location.blockstore.FilesystemOptions;

public class YamlTest extends AbstractYamlTest {

    @Test
    public void testInstantiateCustomizer() throws Exception {
        String yaml = Joiner.on("\n").join(
                "location:",
                "  aws-ec2:us-east-1:",
                "    customizers:",
                "    - $brooklyn:object:",
                "        type: "+Ec2NewVolumeCustomizer.class.getName(),
                "        object.fields:",
                "          volumes:",
                "          - blockDevice:",
                "              sizeInGb: 1",
                "              zone: us-east-1b",
                "              deviceSuffix: 'z'",
                "              deleteOnTermination: false",
                "              tags:",
                "                tag1: val1",
                "            filesystem:",
                "              mountPoint: /my/mount/point",
                "              filesystemType: ext3",
                "services:",
                "- type: "+BasicApplication.class.getName());

        Entity app = createAndStartApplication(yaml);
        waitForApplicationTasks(app);

        JcloudsLocation loc = (JcloudsLocation) Iterables.getOnlyElement(app.getLocations());
        Ec2NewVolumeCustomizer customizer = (Ec2NewVolumeCustomizer) Iterables.getOnlyElement(loc.config().get(JcloudsLocation.JCLOUDS_LOCATION_CUSTOMIZERS));
        
        Map<BlockDeviceOptions, FilesystemOptions> volumes = customizer.getParsedVolumes();
        String msg = "volumes="+volumes;
        
        assertEquals(volumes.size(), 1, msg);
        
        BlockDeviceOptions blockDevice = Iterables.getOnlyElement(volumes.keySet());
        assertEquals(blockDevice.getSizeInGb(), 1);
        assertEquals(blockDevice.deleteOnTermination(), false);
        assertEquals(blockDevice.getZone(), "us-east-1b");
        assertEquals(blockDevice.getDeviceSuffix(), 'z');
        assertEquals(blockDevice.getTags(), ImmutableMap.of("tag1", "val1"));

        FilesystemOptions filesystemOptions = Iterables.getOnlyElement(volumes.values());
        assertEquals(filesystemOptions.getMountPoint(), "/my/mount/point");
        assertEquals(filesystemOptions.getFilesystemType(), "ext3");
    }
}
