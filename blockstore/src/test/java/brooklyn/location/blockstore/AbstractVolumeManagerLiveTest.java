package brooklyn.location.blockstore;

import static brooklyn.util.ssh.CommonCommands.sudo;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.Entities;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.location.jclouds.JcloudsSshMachineLocation;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

public abstract class AbstractVolumeManagerLiveTest {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractVolumeManagerLiveTest.class);

    protected BrooklynProperties brooklynProperties;
    protected ManagementContext ctx;
    
    protected JcloudsLocation jcloudsLocation;
    protected VolumeManager volumeManager;
    protected String volumeId;

    private List<JcloudsSshMachineLocation> machines = Lists.newCopyOnWriteArrayList();
    
    protected abstract String getProvider();

    protected abstract JcloudsLocation createJcloudsLocation();
    
    protected abstract VolumeManager createVolumeManager();

    protected abstract int getVolumeSize();

    protected abstract String getDefaultAvailabilityZone();

    protected abstract void assertVolumeAvailable(String volumeId);

    protected abstract JcloudsSshMachineLocation rebindJcloudsMachine() throws Exception;
    
    protected abstract JcloudsSshMachineLocation createJcloudsMachine() throws Exception;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        ctx = new LocalManagementContext();
        brooklynProperties = (BrooklynProperties) ctx.getConfig();

        // Don't let any defaults from brooklyn.properties (except credentials) interfere with test
        brooklynProperties.remove("brooklyn.jclouds."+getProvider()+".image-description-regex");
        brooklynProperties.remove("brooklyn.jclouds."+getProvider()+".image-name-regex");
        brooklynProperties.remove("brooklyn.jclouds."+getProvider()+".image-id");
        brooklynProperties.remove("brooklyn.jclouds."+getProvider()+".inboundPorts");
        brooklynProperties.remove("brooklyn.jclouds."+getProvider()+".hardware-id");

        // Also removes scriptHeader (e.g. if doing `. ~/.bashrc` and `. ~/.profile`, then that can cause "stdin: is not a tty")
        brooklynProperties.remove("brooklyn.ssh.config.scriptHeader");

        jcloudsLocation = createJcloudsLocation();
        volumeManager = createVolumeManager();
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        for (JcloudsSshMachineLocation machine : machines) {
            jcloudsLocation.release(machine);
        }
        machines.clear();
        
        if (volumeId != null) volumeManager.deleteVolume(jcloudsLocation, volumeId);
        if (ctx != null) Entities.destroyAll(ctx);
    }

    @Test(groups="Live")
    public void testCreateVolume() throws Exception {
        volumeId = volumeManager.createVolume(jcloudsLocation, getDefaultAvailabilityZone(), getVolumeSize(), 
                ImmutableMap.of("user", System.getProperty("user.name"), "purpose", "brooklyn-blockstore-VolumeManagerLiveTest"));

        assertVolumeAvailable(volumeId);
    }

    // Does the attach+mount twice to ensure that cleanup worked
    @Test(groups="Live")
    public void testCreateAndAttachVolume() throws Throwable {
        String deviceSuffix = "h";
        String ec2DeviceName = "/dev/sd" + deviceSuffix;
        String osDeviceName = "/dev/xvd" + deviceSuffix;
        String mountPoint = "/var/opt2/test1";
        String filesystemType = "ext3";

        // For speed, try to use an existing VM; but if that doesn't exist then fallback to creating a temporary one
        JcloudsSshMachineLocation machine;
        try {
            machine = rebindJcloudsMachine();
        } catch (IllegalArgumentException e) {
            // Existing VM does not exist; will create a temporary VM for this run
            LOG.info("Rebind failed; falling back to creating temporary VM in "+jcloudsLocation);
            machine = createJcloudsMachine();
            machines.add(machine);
        }
        
        try {
            // Create and mount the initial volume
            volumeId = volumeManager.createAttachAndMountVolume(machine, ec2DeviceName, osDeviceName, mountPoint, filesystemType, getVolumeSize(),
                    ImmutableMap.of("user", System.getProperty("user.name"), "purpose", "brooklyn-blockstore-VolumeManagerLiveTest"));

            assertExecSucceeds(machine, "show mount points", ImmutableList.of("mount -l", "mount -l | grep \""+mountPoint+"\" | grep \""+osDeviceName+"\""));
            assertExecSucceeds(machine, "list mount contents", ImmutableList.of("ls -la "+mountPoint));

            String tmpDestFile = "/tmp/myfile.txt";
            String destFile = mountPoint+"/myfile.txt";
            machine.copyTo(new ByteArrayInputStream("abc".getBytes()), tmpDestFile);
            assertExecSucceeds(machine, "list mount contents", ImmutableList.of(sudo("cp "+tmpDestFile+" "+destFile)));
            
            // Unmount and detach the volume
            volumeManager.unmountFilesystem(machine, osDeviceName);
            volumeManager.detachVolume(machine, volumeId, ec2DeviceName);

            assertExecFails(machine, "show mount points", ImmutableList.of("mount -l", "mount -l | grep \""+mountPoint+"\" | grep \""+osDeviceName+"\""));
            assertExecFails(machine, "check file contents", ImmutableList.of("cat "+destFile, "grep abc "+destFile));
            
            // Re-attach and mount the volume
            volumeManager.attachAndMountVolume(machine, volumeId, ec2DeviceName, osDeviceName, mountPoint, filesystemType);

            assertExecSucceeds(machine, "show mount points", ImmutableList.of("mount -l", "mount -l | grep \""+mountPoint+"\" | grep \""+osDeviceName+"\""));
            assertExecSucceeds(machine, "list mount contents", ImmutableList.of("ls -la "+mountPoint));
            assertExecSucceeds(machine, "check file contents", ImmutableList.of("cat "+destFile, "grep abc "+destFile));

        } catch (Throwable t) {
            LOG.error("Error creating and attaching volume", t);
            throw t;
            
        } finally {
            if (volumeId != null) {
                try {
                    volumeManager.unmountFilesystem(machine, osDeviceName);
                    volumeManager.detachVolume(machine, volumeId, ec2DeviceName);
                } catch (Exception e) {
                    LOG.error("Error umounting/detaching volume", e);
                }
            }
        }
    }

    private void assertExecSucceeds(SshMachineLocation machine, String description, List<String> cmds) {
        int success = machine.execCommands(description, cmds);
        assertEquals(success, 0);
    }
    
    private void assertExecFails(SshMachineLocation machine, String description, List<String> cmds) {
        int success = machine.execCommands(description, cmds);
        assertNotEquals(success, 0);
    }
}
