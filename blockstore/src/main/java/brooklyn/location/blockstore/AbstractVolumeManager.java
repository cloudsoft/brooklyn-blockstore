package brooklyn.location.blockstore;

import static java.lang.String.format;
import static org.apache.brooklyn.util.ssh.BashCommands.dontRequireTtyForSudo;
import static org.apache.brooklyn.util.ssh.BashCommands.ifFileExistsElse0;
import static org.apache.brooklyn.util.ssh.BashCommands.installPackage;
import static org.apache.brooklyn.util.ssh.BashCommands.sudo;

import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;

import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsMachineLocation;
import org.apache.brooklyn.location.jclouds.JcloudsMachineNamer;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.ssh.BashCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import brooklyn.location.blockstore.api.AttachedBlockDevice;
import brooklyn.location.blockstore.api.BlockDevice;
import brooklyn.location.blockstore.api.MountedBlockDevice;
import brooklyn.location.blockstore.api.VolumeManager;
import brooklyn.location.blockstore.api.VolumeOptions;

public abstract class AbstractVolumeManager implements VolumeManager {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractVolumeManager.class);
    private static final String TMP_MOUNT_POINT = "/mnt";

    protected AbstractVolumeManager() {
    }

    protected abstract String getVolumeDeviceName(char deviceSuffix);
    protected abstract String getOSDeviceName(char deviceSuffix);

    @Override
    public void cleanOldMountPoints(JcloudsMachineLocation machine,
    		List<VolumeOptions> volumes) {

        if (!(machine instanceof SshMachineLocation)) {
            throw new IllegalStateException("Cannot clean root device machine "+machine+" of type "+machine.getClass().getName()+"; expected "+SshMachineLocation.class.getSimpleName());
        }
        
        // NOTE: also adds an entry to fstab so the mount remains available after a reboot.
        Map<String, ?> flags = MutableMap.of("allocatePTY", true);       
        
        for(VolumeOptions volume: volumes){
        
        	String mountPoint = volume.getFilesystemOptions().getMountPoint();
        	String osRootDeviceName = getOSDeviceName('a');        	
        	
        	int exitCode = ((SshMachineLocation)machine).execCommands(flags, "Creating filesystem on volume", ImmutableList.of(
                    dontRequireTtyForSudo(),
                    sudo(format("mount %s %s", osRootDeviceName, TMP_MOUNT_POINT)),
                    sudo(format("rm -rf %s/*", TMP_MOUNT_POINT+mountPoint)),
                    sudo(format("umount %s", TMP_MOUNT_POINT))));
                    
            if (exitCode != 0) {
                throw new RuntimeException(format("Failed to clean old mount point on root device. machine=%s; rootDevice=%s; mountPoint=%s",
                        machine, osRootDeviceName, mountPoint));
            }	
        	
        }          	
    	
    }
    
    @Override
    public MountedBlockDevice createAttachAndMountVolume(JcloudsMachineLocation machine, BlockDeviceOptions deviceOptions,
            FilesystemOptions filesystemOptions) {
        BlockDevice device = createBlockDevice(machine.getParent(), deviceOptions);
        AttachedBlockDevice attached = attachBlockDevice(machine, device, deviceOptions);
        createFilesystem(attached, filesystemOptions);
        return mountFilesystem(attached, filesystemOptions);
    }

    @Override
    public MountedBlockDevice attachAndMountVolume(JcloudsMachineLocation machine, BlockDevice device,
            BlockDeviceOptions options, FilesystemOptions filesystemOptions) {
        AttachedBlockDevice attached = attachBlockDevice(machine, device, options);
        return mountFilesystem(attached, filesystemOptions);
    }

    // TODO: Running `fdisk -l` after mkfs outputs: "Disk /dev/sdb doesn't contain a valid partition table"
    @Override
    public void createFilesystem(AttachedBlockDevice attachedDevice, FilesystemOptions filesystemOptions) {
        JcloudsMachineLocation machine = attachedDevice.getMachine();
        if (!(machine instanceof SshMachineLocation)) {
            throw new IllegalStateException("Cannot create filesystem for "+machine+" of type "+machine.getClass().getName()+"; expected "+SshMachineLocation.class.getSimpleName());
        }
        
        String osDeviceName = getOSDeviceName(attachedDevice.getDeviceSuffix());
        String filesystemType = filesystemOptions.getFilesystemType();
        LOG.debug("Creating filesystem: device={}; osDeviceName={}, config={}", new Object[]{attachedDevice, osDeviceName, filesystemOptions});

        // NOTE: also adds an entry to fstab so the mount remains available after a reboot.
        Map<String, ?> flags = MutableMap.of("allocatePTY", true);

        int exitCode = ((SshMachineLocation)machine).execCommands(flags, "Creating filesystem on volume", ImmutableList.of(
                dontRequireTtyForSudo(),
                waitForFileCmd(osDeviceName, 60),
                installPackage(ImmutableMap.of("yum", "e4fsprogs"), null),
                sudo("/sbin/mkfs -F -t " + filesystemType + " " + osDeviceName)));

        if (exitCode != 0) {
            throw new RuntimeException(format("Failed to create file system. machine=%s; osDeviceName=%s; filesystemType=%s",
                    machine, osDeviceName, filesystemType));
        }
    }

    @Override
    public MountedBlockDevice mountFilesystem(AttachedBlockDevice attachedDevice, FilesystemOptions options) {
        JcloudsMachineLocation machine = attachedDevice.getMachine();
        if (!(machine instanceof SshMachineLocation)) {
            throw new IllegalStateException("Cannot mount filesystem for "+machine+" of type "+machine.getClass().getName()+"; expected "+SshMachineLocation.class.getSimpleName());
        }
        
        LOG.debug("Mounting filesystem: device={}; options={}", attachedDevice, options);
        String osDeviceName = getOSDeviceName(attachedDevice.getDeviceSuffix());
        String mountPoint = options.getMountPoint();
        String filesystemType = options.getFilesystemType();

        // NOTE: also adds an entry to fstab so the mount remains available after a reboot.
        Map<String, ?> flags = MutableMap.of("allocatePTY", true);
        int exitCode = ((SshMachineLocation)machine).execCommands(flags, "Mounting EBS volume", ImmutableList.of(
                dontRequireTtyForSudo(),
                ifFileExistsElse0(
						mountPoint,"echo mount point still exists"),
                ifFileExistsElse0(mountPoint,
                		sudo(format("mount -t %1$s %2$s %3$s", filesystemType, osDeviceName,TMP_MOUNT_POINT))),
        		ifFileExistsElse0(
						mountPoint,"echo save existing files to temporary mount point"),
                ifFileExistsElse0(
						mountPoint,
						sudo(format("cp -a %1$s %2$s", mountPoint + "/*", TMP_MOUNT_POINT))),
				ifFileExistsElse0(
						mountPoint,"echo umount temporary point"),				
				ifFileExistsElse0(
						mountPoint,
						sudo(format("umount %s",TMP_MOUNT_POINT))),                
                "echo making dir",
                sudo("mkdir -p -m 755 " + mountPoint),
                "echo updating fstab",
                waitForFileCmd(osDeviceName, 60),
                "echo \"" + osDeviceName + " " + mountPoint + " " + filesystemType + " noatime 0 0\" | " + sudo("tee -a /etc/fstab")
        ));
        
        if (exitCode != 0) {
            throw new RuntimeException(format("Failed to mount file system. machine=%s; osDeviceName=%s; mountPoint=%s; filesystemType=%s",
                    attachedDevice.getMachine(), osDeviceName, mountPoint, filesystemType));
        }

        return attachedDevice.mountedAt(options.getMountPoint());
    }
    
    @Override
    public void restartMachine(JcloudsMachineLocation machine) {
    	 if (!(machine instanceof SshMachineLocation)) {
             throw new IllegalStateException("Cannot restart vm for "+machine+" of type "+machine.getClass().getName()+"; expected "+SshMachineLocation.class.getSimpleName());
         }
         
         LOG.debug("Restart machine: host={}", machine.getAddress().getHostAddress());
         
         Map<String, ?> flags = MutableMap.of("allocatePTY", true);
         int exitCode = ((SshMachineLocation)machine).execCommands(flags, "Restart VM", ImmutableList.of(
                 dontRequireTtyForSudo(),
                 sudo("shutdown -r now")
         ));
         
         if (exitCode != 0) {
             throw new RuntimeException(format("Failed to restart vm. machine=%s", machine));
         }
         
 		boolean flag = false;
 		do {
 			try {
 				Thread.sleep(15000); 				
 		         flag = ((SshMachineLocation)machine).execCommands(flags, "Check if restarted", ImmutableList.of(
 		                 dontRequireTtyForSudo(),
 		                 "uname -a"
 		         )) == 0;
 			} catch (InterruptedException e) {
 				LOG.error(
 						"machine-is-running-check fails by Thread.sleep()", e);
 			} catch (Exception e) {
 				LOG.debug("vm is not up wait another 15s", e);
 			}
 		} while (!flag);
 		
 		LOG.debug("machine is up and running: host={}", machine.getAddress().getHostAddress());
    	
    }

    @Override
    public AttachedBlockDevice unmountFilesystem(MountedBlockDevice mountedDevice) {
        JcloudsMachineLocation machine = mountedDevice.getMachine();
        if (!(machine instanceof SshMachineLocation)) {
            throw new IllegalStateException("Cannot unmount filesystem for "+machine+" of type "+machine.getClass().getName()+"; expected "+SshMachineLocation.class.getSimpleName());
        }
        
        LOG.debug("Unmounting filesystem: {}", mountedDevice);
        String osDeviceName = getOSDeviceName(mountedDevice.getDeviceSuffix());
        String osDeviceNameEscaped = osDeviceName.replaceAll("/", "\\\\/");

        // NOTE: also strips out entry from fstab
        Map<String, ?> flags = MutableMap.of("allocatePTY", true);
        ((SshMachineLocation)machine).execCommands(flags, "Unmounting EBS volume", ImmutableList.of(
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
            return "volume-" + new JcloudsMachineNamer().generateNewMachineUniqueName(location.getLocalConfigBag());
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
