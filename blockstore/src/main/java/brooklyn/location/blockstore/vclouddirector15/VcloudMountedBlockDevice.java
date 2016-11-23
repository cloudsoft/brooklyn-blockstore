package brooklyn.location.blockstore.vclouddirector15;

import brooklyn.location.blockstore.api.MountedBlockDevice;

public class VcloudMountedBlockDevice extends VcloudBlockDevice implements MountedBlockDevice {
    private String mountPoint;

    public VcloudMountedBlockDevice(VcloudBlockDevice vcloudBlockDevice, String mountPoint) {
        super(vcloudBlockDevice);
        this.mountPoint = mountPoint;
    }

    @Override
    public String getMountPoint() {
        return mountPoint;
    }
}
