package brooklyn.location.blockstore.gce;

import java.util.List;
import java.util.Set;

import org.jclouds.compute.ComputeService;
import org.jclouds.compute.options.TemplateOptions;
import org.jclouds.googlecomputeengine.compute.options.GoogleComputeEngineTemplateOptions;
import org.jclouds.googlecomputeengine.domain.InstanceTemplate.PersistentDisk;
import org.jclouds.googlecomputeengine.domain.InstanceTemplate.PersistentDisk.Mode;

import brooklyn.location.jclouds.BasicJcloudsLocationCustomizer;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.location.jclouds.JcloudsLocationCustomizer;
import brooklyn.util.collections.MutableSet;

public class GoogleComputeEngineVolumeCustomizer {

    // FIXME This is Alex's guess! Untested.
    // FIXME How to set capacities, rather than just number of disks?
    public static JcloudsLocationCustomizer withNewVolume(final List<Integer> capacities) {
        return new BasicJcloudsLocationCustomizer() {
            public void customize(JcloudsLocation location, ComputeService computeService, TemplateOptions templateOptions) {
                Set<PersistentDisk> disks = MutableSet.of();
                for (int i=0; i<capacities.size(); i++) {
                    disks.add(new PersistentDisk(Mode.READ_WRITE, null, "sd"+(char)('f'+i), true, true));
                }
                ((GoogleComputeEngineTemplateOptions)templateOptions).disks(disks);
            }
        };
    }
}
