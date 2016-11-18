package brooklyn.location.blockstore.vclouddirector15;

import brooklyn.location.blockstore.NewVolumeCustomizer;
import brooklyn.location.blockstore.api.VolumeManager;
import brooklyn.location.blockstore.api.VolumeOptions;

import java.util.List;

public class VcloudNewVolumeCustomizer extends NewVolumeCustomizer {
    @Override
    protected VolumeManager getVolumeManager() {
        return new VcloudVolumeManager();
    }

    public VcloudNewVolumeCustomizer(List<VolumeOptions> volumes) {
        super(volumes);
    }
}
