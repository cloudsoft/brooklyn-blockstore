package brooklyn.location.blockstore.softlayer;

import java.util.List;

import org.jclouds.compute.ComputeService;
import org.jclouds.compute.options.TemplateOptions;
import org.jclouds.softlayer.compute.options.SoftLayerTemplateOptions;

import brooklyn.location.jclouds.BasicJcloudsLocationCustomizer;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.location.jclouds.JcloudsLocationCustomizer;

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
