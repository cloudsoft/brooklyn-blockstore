package brooklyn.location.blockstore;

import brooklyn.location.jclouds.JcloudsSshMachineLocation;
import brooklyn.util.collections.MutableMap;
import com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static brooklyn.util.ssh.CommonCommands.dontRequireTtyForSudo;
import static brooklyn.util.ssh.CommonCommands.sudo;
import static java.lang.String.format;

public abstract class AbstractVolumeManager implements VolumeManager {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractVolumeManager.class);

    protected AbstractVolumeManager() {
    }

    @Override
    public String createAttachAndMountVolume(JcloudsSshMachineLocation machine,
            final String volumeDeviceName, final String osDeviceName, final String mountPoint, final String filesystemType,
            final int sizeInGib, Map<String, String> tags) {
        String volumeId = createVolume(machine.getParent(), machine.getNode().getLocation().getId(), sizeInGib, tags);
        attachVolume(machine, volumeId, volumeDeviceName);
        createFilesystem(machine, osDeviceName, filesystemType);
        mountFilesystem(machine, osDeviceName, mountPoint, filesystemType);
        return volumeId;
    }

    /**
     * Attaches the given volume to the given VM, and mounts it.
     */
    @Override
    public void attachAndMountVolume(JcloudsSshMachineLocation machine, String volumeId, String ec2DeviceName, String osDeviceName, String mountPoint) {
        attachAndMountVolume(machine, volumeId, ec2DeviceName, osDeviceName, mountPoint, "auto");
    }

    @Override
    public void attachAndMountVolume(JcloudsSshMachineLocation machine, String volumeId, String ec2DeviceName, String osDeviceName, String mountPoint, String filesystemType) {
        attachVolume(machine, volumeId, ec2DeviceName);
        mountFilesystem(machine, osDeviceName, mountPoint, filesystemType);
    }

    @Override
    public void createFilesystem(JcloudsSshMachineLocation machine, String osDeviceName, String filesystemType) {
        LOG.debug("Creating filesystem: machine={}; osDeviceName={}; filesystemType={}", new Object[]{machine, osDeviceName, filesystemType});

        // NOTE: also adds an entry to fstab so the mount remains available after a reboot.
        Map<String, ?> flags = MutableMap.of("allocatePTY", true);

        int exitCode = machine.execCommands(flags, "Creating filesystem on EBS volume", ImmutableList.of(

                dontRequireTtyForSudo(),
                waitForFileCmd(osDeviceName, 60),
                sudo("/sbin/mkfs -t " + filesystemType + " " + osDeviceName)));

        if (exitCode != 0) {
            throw new RuntimeException(format("Failed to create file system. machine=%s; osDeviceName=%s; filesystemType=%s", machine, osDeviceName, filesystemType));
        }
    }

    @Override
    public void mountFilesystem(JcloudsSshMachineLocation machine, String osDeviceName, String mountPoint) {
        mountFilesystem(machine, osDeviceName, mountPoint, "auto");
    }

    @Override
    public void mountFilesystem(JcloudsSshMachineLocation machine, String osDeviceName, String mountPoint, String filesystemType) {
        LOG.debug("Mounting filesystem: machine={}; osDeviceName={}; mountPoint={}; filesystemType={}", new Object[]{machine, osDeviceName, mountPoint, filesystemType});

        // NOTE: also adds an entry to fstab so the mount remains available after a reboot.
        Map<String, ?> flags = MutableMap.of("allocatePTY", true);
        int exitCode = machine.execCommands(flags, "Mounting EBS volume", ImmutableList.of(
                dontRequireTtyForSudo(),
                "echo making dir",
                sudo(" mkdir -p -m 755 " + mountPoint),
                "echo updating fstab",
                waitForFileCmd(osDeviceName, 60),
                "echo \"" + osDeviceName + " " + mountPoint + " " + filesystemType + " noatime 0 0\" | " + sudo("tee -a /etc/fstab"),
                "echo mounting device",
                sudo("mount " + mountPoint),
                "echo device mounted"
        ));

        if (exitCode != 0) {
            throw new RuntimeException(format("Failed to mount file system. machine=%s osDeviceName=%s; mountPoint=%s; filesystemType=%s", machine, osDeviceName, mountPoint, filesystemType));
        }
    }

    @Override
    public void unmountFilesystem(JcloudsSshMachineLocation machine, String osDeviceName) {
        LOG.debug("Unmounting filesystem: machine={}; osDeviceName={}", new Object[]{machine, osDeviceName});
        String osDeviceNameEscaped = osDeviceName.replaceAll("/", "\\\\/");

        // NOTE: also strips out entry from fstab
        Map<String, ?> flags = MutableMap.of("allocatePTY", true);
        machine.execCommands(flags, "Unmounting EBS volume", ImmutableList.of(
                dontRequireTtyForSudo(),
                "echo unmounting " + osDeviceName,
                sudo("sed -i.bk '/" + osDeviceNameEscaped + "/d' /etc/fstab"),
                sudo("umount " + osDeviceName),
                "echo unmounted " + osDeviceName
        ));
    }

    // TODO Move to CommonCommands
    protected String waitForFileCmd(String file, int timeoutSecs) {
        return "found=false; " +
                "for i in {1.." + timeoutSecs + "}; do " +
                "if [ -a " + file + " ]; then " +
                "echo \"file " + file + " found\"; " +
                "found=true; " +
                "break; " +
                "else " +
                "echo \"file " + file + " does not exist (waiting)\"; " +
                "sleep 1; " +
                "fi; " +
                "done; " +
                "if [ \"$found\" == \"false\" ]; then " +
                "exit 1; " +
                "fi";
    }
}
