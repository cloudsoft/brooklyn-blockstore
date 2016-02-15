package brooklyn.location.blockstore.api;

import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsSshMachineLocation;

import brooklyn.location.blockstore.BlockDeviceOptions;
import brooklyn.location.blockstore.FilesystemOptions;

/**
 * Customization hooks to ensure that any volume instances provisioned via a corresponding jclouds location become associated
 * with an EBS volume (either an existing volume, specified by ID, or newly created).
 */
public interface VolumeManager {

    /**
     * Creates a new volume in the given availability zone.
     * 
     * @param location Location where the volume should be created
     * @param options Configuration for the new volume
     */
    public BlockDevice createBlockDevice(JcloudsLocation location, BlockDeviceOptions options);

    /**
     * Attaches the given volume to the given VM.
     *
     * @param machine The VM where the volume should be attached
     * @param blockDevice The device that should be attached to machine
     * @param options Configuration for the device, e.g. the device's name and suffix
     */
    public AttachedBlockDevice attachBlockDevice(JcloudsSshMachineLocation machine, BlockDevice blockDevice, BlockDeviceOptions options);

    /**
     * Attaches the given volume to the given VM, and mounts it.
     * 
     * @param machine The VM where the volume should be attached
     * @param blockDevice The device that should be attached to machine
     * @param blockDeviceOptions Configuration for attaching the device, e.g. the device's name and suffix
     * @param filesystemOptions Configuration for mounting the device, e.g. the device's mount point
     */
    public MountedBlockDevice attachAndMountVolume(JcloudsSshMachineLocation machine, BlockDevice blockDevice,
        BlockDeviceOptions blockDeviceOptions, FilesystemOptions filesystemOptions);

    /**
     * Detaches the given volume from the given VM. The filesystem should first be cleanly unmounted.
     * 
     * @param attachedBlockDevice A device attached to a machine
     */
    public BlockDevice detachBlockDevice(AttachedBlockDevice attachedBlockDevice);

    /**
     * Deletes the given volume. The volume should first be detached from any VMs.
     * 
     * @param blockDevice A device that was created in a location
     */
    public void deleteBlockDevice(BlockDevice blockDevice);

    /**
     * Creates a filesystem for an attached volume.
     * 
     * @param attachedDevice A device that has been attached to a machine
     * @param options Configuration of filesystem type and mount point
     */
    public void createFilesystem(AttachedBlockDevice attachedDevice, FilesystemOptions options);

    /**
     * Mounts the given device.
     * @param attachedDevice A device that has been attached to a machine
     * @param options Configuration of filesystem type and mount point
     */
    public MountedBlockDevice mountFilesystem(AttachedBlockDevice attachedDevice, FilesystemOptions options);

    /**
     * Unmounts the given device.
     */
    public AttachedBlockDevice unmountFilesystem(MountedBlockDevice mountedDevice);

    /**
     * Creates a new volume in the same availability zone as the given machine, and attaches and mounts it.
     *
     * @param machine The VM where the volume should be attached and mounted
     * @param blockDeviceOptions Configuration for attaching the device, e.g. the device's name and suffix
     * @param filesystemOptions Configuration for mounting the device, e.g. the device's mount point
     */
    public MountedBlockDevice createAttachAndMountVolume(JcloudsSshMachineLocation machine,
            BlockDeviceOptions blockDeviceOptions, FilesystemOptions filesystemOptions);

    /**
     * Unmounts the given device and detaches the volume from the given VM.
     */
    public BlockDevice unmountFilesystemAndDetachVolume(MountedBlockDevice mountedDevice);

}
