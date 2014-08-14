package brooklyn.location.blockstore;

import static brooklyn.util.ssh.BashCommands.dontRequireTtyForSudo;
import static brooklyn.util.ssh.BashCommands.sudo;
import static java.lang.String.format;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import brooklyn.location.blockstore.api.AttachedBlockDevice;
import brooklyn.location.blockstore.api.BlockDevice;
import brooklyn.location.blockstore.api.MountedBlockDevice;
import brooklyn.location.blockstore.api.VolumeManager;
import brooklyn.location.cloud.CloudMachineNamer;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.location.jclouds.JcloudsSshMachineLocation;
import brooklyn.util.collections.MutableMap;

public abstract class AbstractVolumeManager implements VolumeManager {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractVolumeManager.class);

    protected AbstractVolumeManager() {
    }

    protected abstract String getVolumeDeviceName(char deviceSuffix);
    protected abstract String getOSDeviceName(char deviceSuffix);

    @Override
    public MountedBlockDevice createAttachAndMountVolume(JcloudsSshMachineLocation machine, BlockDeviceOptions deviceOptions,
            FilesystemOptions filesystemOptions) {
        BlockDevice device = createBlockDevice(machine.getParent(), deviceOptions);
        AttachedBlockDevice attached = attachBlockDevice(machine, device, deviceOptions);
        createFilesystem(attached, filesystemOptions);
        return mountFilesystem(attached, filesystemOptions);
    }

    @Override
    public MountedBlockDevice attachAndMountVolume(JcloudsSshMachineLocation machine, BlockDevice device,
            BlockDeviceOptions options, FilesystemOptions filesystemOptions) {
        AttachedBlockDevice attached = attachBlockDevice(machine, device, options);
        return mountFilesystem(attached, filesystemOptions);
    }

    // TODO: Running `fdisk -l` after mkfs outputs: "Disk /dev/sdb doesn't contain a valid partition table"
    @Override
    public void createFilesystem(AttachedBlockDevice attachedDevice, FilesystemOptions filesystemOptions) {
        String osDeviceName = getOSDeviceName(attachedDevice.getDeviceSuffix());
        String filesystemType = filesystemOptions.getFilesystemType();
        LOG.debug("Creating filesystem: device={}; osDeviceName={}, config={}", new Object[]{attachedDevice, osDeviceName, filesystemOptions});

        // NOTE: also adds an entry to fstab so the mount remains available after a reboot.
        Map<String, ?> flags = MutableMap.of("allocatePTY", true);

        int exitCode = attachedDevice.getMachine().execCommands(flags, "Creating filesystem on volume", ImmutableList.of(
                dontRequireTtyForSudo(),
                waitForFileCmd(osDeviceName, 60),
                sudo("/sbin/mkfs -F -t " + filesystemType + " " + osDeviceName)));

        if (exitCode != 0) {
            throw new RuntimeException(format("Failed to create file system. machine=%s; osDeviceName=%s; filesystemType=%s",
                    attachedDevice.getMachine(), osDeviceName, filesystemType));
        }
    }

    @Override
    public MountedBlockDevice mountFilesystem(AttachedBlockDevice attachedDevice, FilesystemOptions options) {
        LOG.debug("Mounting filesystem: device={}; options={}", attachedDevice, options);
        String osDeviceName = getOSDeviceName(attachedDevice.getDeviceSuffix());
        String mountPoint = options.getMountPoint();
        String filesystemType = options.getFilesystemType();

        // NOTE: also adds an entry to fstab so the mount remains available after a reboot.
        Map<String, ?> flags = MutableMap.of("allocatePTY", true);
        int exitCode = attachedDevice.getMachine().execCommands(flags, "Mounting EBS volume", ImmutableList.of(
                dontRequireTtyForSudo(),
                "echo making dir",
                sudo("mkdir -p -m 755 " + mountPoint),
                "echo updating fstab",
                waitForFileCmd(osDeviceName, 60),
                "echo \"" + osDeviceName + " " + mountPoint + " " + filesystemType + " noatime 0 0\" | " + sudo("tee -a /etc/fstab"),
                "echo mounting device",
                sudo("mount " + mountPoint),
                "echo device mounted"
        ));

        if (exitCode != 0) {
            throw new RuntimeException(format("Failed to mount file system. machine=%s; osDeviceName=%s; mountPoint=%s; filesystemType=%s",
                    attachedDevice.getMachine(), osDeviceName, mountPoint, filesystemType));
        }

        return attachedDevice.mountedAt(options.getMountPoint());
    }

    @Override
    public AttachedBlockDevice unmountFilesystem(MountedBlockDevice mountedDevice) {
        LOG.debug("Unmounting filesystem: {}", mountedDevice);
        String osDeviceName = getOSDeviceName(mountedDevice.getDeviceSuffix());
        String osDeviceNameEscaped = osDeviceName.replaceAll("/", "\\\\/");

        // NOTE: also strips out entry from fstab
        Map<String, ?> flags = MutableMap.of("allocatePTY", true);
        mountedDevice.getMachine().execCommands(flags, "Unmounting EBS volume", ImmutableList.of(
                dontRequireTtyForSudo(),
                "echo unmounting " + osDeviceName,
                sudo("sed -i.bk '/" + osDeviceNameEscaped + "/d' /etc/fstab"),
                sudo("umount " + osDeviceName),
                "echo unmounted " + osDeviceName
        ));
        return mountedDevice;
    }

    @Override
    public BlockDevice unmountFilesystemAndDetachVolume(MountedBlockDevice mountedDevice) {
        unmountFilesystem(mountedDevice);
        return detachBlockDevice(mountedDevice);
    }

    protected String getOrMakeName(JcloudsLocation location, BlockDeviceOptions options) {
        if (!Strings.isNullOrEmpty(options.getName())) {
            return options.getName();
        } else {
            return "volume-" + new CloudMachineNamer(location.getLocalConfigBag()).generateNewMachineUniqueName();
        }
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
