package brooklyn.location.blockstore.api;

import brooklyn.location.jclouds.JcloudsSshMachineLocation;

public interface AttachedBlockDevice extends BlockDevice {

    /**
     * @return The name of the device on the machine. For example, "/dev/sdb".
     */
    public String getDeviceName();

    /**
     * @return The suffix that was given in configuration when creating the device.
     * @see brooklyn.location.blockstore.BlockDeviceOptions#deviceSuffix
     */
    public char getDeviceSuffix();

    /** @return The machine to which this block device is attached */
    public JcloudsSshMachineLocation getMachine();

    // TODO: Bit confusing that this doesn't actually mount the device.
    /**
     * Creates an instance of {@link MountedBlockDevice} with mount point set to that given.
     * Intended for use internally. Does <b>not</b> mount the device.
     */
    public MountedBlockDevice mountedAt(String mountPoint);

}
