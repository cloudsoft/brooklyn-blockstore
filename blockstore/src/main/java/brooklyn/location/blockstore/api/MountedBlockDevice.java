package brooklyn.location.blockstore.api;

public interface MountedBlockDevice extends AttachedBlockDevice {

    /**
     * @return The path on which this volume has been mounted.
     */
    public String getMountPoint();

}
