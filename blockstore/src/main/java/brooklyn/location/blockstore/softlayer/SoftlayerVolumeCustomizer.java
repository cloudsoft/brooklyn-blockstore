package brooklyn.location.blockstore.softlayer;

import java.util.List;

import org.apache.brooklyn.location.jclouds.BasicJcloudsLocationCustomizer;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsLocationCustomizer;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.options.TemplateOptions;
import org.jclouds.softlayer.compute.options.SoftLayerTemplateOptions;

public class SoftlayerVolumeCustomizer {

    public static JcloudsLocationCustomizer withNewVolume(final List<Integer> capacities) {
        return new BasicJcloudsLocationCustomizer() {
            public void customize(JcloudsLocation location, ComputeService computeService, TemplateOptions templateOptions) {
                ((SoftLayerTemplateOptions)templateOptions).diskType("SAN");
                ((SoftLayerTemplateOptions)templateOptions).blockDevices(capacities);
            }
        };
    }
}
