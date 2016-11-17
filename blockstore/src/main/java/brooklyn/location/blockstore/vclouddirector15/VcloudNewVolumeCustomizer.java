package brooklyn.location.blockstore.vclouddirector15;

import brooklyn.location.blockstore.BlockDeviceOptions;
import brooklyn.location.blockstore.FilesystemOptions;
import brooklyn.location.blockstore.NewVolumeCustomizer;
import brooklyn.location.blockstore.api.MountedBlockDevice;
import brooklyn.location.blockstore.api.VolumeManager;

import java.util.Map;

public class VcloudNewVolumeCustomizer extends NewVolumeCustomizer {
    private Map<BlockDeviceOptions, FilesystemOptions> volumes;
    private final static VolumeManager volumeManager = new VcloudVolumeManager();

    private MountedBlockDevice mountedBlockDevice;

    @Override
    protected VolumeManager getVolumeManager() {
        return volumeManager;
    }

    public VcloudNewVolumeCustomizer(Map<BlockDeviceOptions, FilesystemOptions> volumes) {
        this.volumes = volumes;
        this.mountedBlockDevice = null;
    }

    @Override
    public Map<BlockDeviceOptions, FilesystemOptions> getVolumes() {
        return volumes;
    }

    @Override
    public MountedBlockDevice getMountedBlockDevice() {
        return mountedBlockDevice;
    }
}
