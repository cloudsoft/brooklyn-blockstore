package brooklyn.location.blockstore.gce;

import java.util.List;

import org.apache.brooklyn.location.jclouds.BasicJcloudsLocationCustomizer;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsLocationCustomizer;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.options.TemplateOptions;

public class GoogleComputeEngineVolumeCustomizer {

    // FIXME This is Alex's guess! Untested.
    // FIXME How to set capacities, rather than just number of disks?
    // FIXME There is no GoogleComputeEngineTemplateOptions.disks!
    public static JcloudsLocationCustomizer withNewVolume(final List<Integer> capacities) {
        return new BasicJcloudsLocationCustomizer() {
            @Override
            public void customize(JcloudsLocation location, ComputeService computeService, TemplateOptions templateOptions) {
                throw new UnsupportedOperationException("Unsupported for GCE");
//                Set<PersistentDisk> disks = MutableSet.of();
//                for (int i=0; i<capacities.size(); i++) {
//                    disks.add(new PersistentDisk(Mode.READ_WRITE, null, "sd"+(char)('f'+i), true, true));
//                }
//                ((GoogleComputeEngineTemplateOptions)templateOptions).disks(disks);
            }
        };
    }
}
