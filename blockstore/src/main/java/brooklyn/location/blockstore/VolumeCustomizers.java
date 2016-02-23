package brooklyn.location.blockstore;

import java.util.List;

import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsLocationCustomizer;
import org.apache.brooklyn.location.jclouds.JcloudsSshMachineLocation;

import brooklyn.location.blockstore.ec2.Ec2VolumeCustomizers;
import brooklyn.location.blockstore.gce.GoogleComputeEngineVolumeCustomizer;
import brooklyn.location.blockstore.softlayer.SoftlayerVolumeCustomizer;

public class VolumeCustomizers {

    private VolumeCustomizers() {}


    /**
     * Returns whether volume customizer is support for the given location.
     * 
     * @return True if {@link #newVolumesCustomizer(Location, List)} can create a {@link JcloudsLocationCustomizer}
     *         for the given location.
     */
    public static boolean isVolumeCustomizerSupportedForLocation(Location location) {
        if (location == null || !(location instanceof JcloudsLocation || location instanceof JcloudsSshMachineLocation))
            return false;
        String provider;
        if (location instanceof JcloudsLocation) {
            provider = JcloudsLocation.class.cast(location).getProvider();
        } else {
            provider = JcloudsSshMachineLocation.class.cast(location).getParent().getProvider();
        }
        return provider.equals("aws-ec2") ||
                provider.equals("google-compute-engine") ||
                provider.equals("softlayer");
    }


    /**
     * Returns a customizer suitable for the given location, which will create volume(s) 
     * for VMs being provisioned.
     * 
     * See {@link #isVolumeCustomizerSupportedForLocation(Location)} to check before hand if a
     * given location is supported.
     * 
     * @param location A {@link brooklyn.location.MachineLocation location} where volumes are to be created.
     * @return A customizer suitable for creating volumes when provisioning a VM.
     * @throws IllegalArgumentException If {@link #isVolumeCustomizerSupportedForLocation}
     *         returns false for the location argument.
     */
    public static JcloudsLocationCustomizer newVolumesCustomizer(Location location, List<Integer> capacities) {
        if (location == null || !isVolumeCustomizerSupportedForLocation(location)) {
            throw new IllegalArgumentException("Cannot handle volumes in location: " + location);
        }

        String provider;
        if (location instanceof JcloudsLocation) {
            provider = JcloudsLocation.class.cast(location).getProvider();
        } else {
            provider = JcloudsSshMachineLocation.class.cast(location).getParent().getProvider();
        }

        if (provider.equals("aws-ec2")) {
            return Ec2VolumeCustomizers.withNewVolumes(capacities);
        } else if (provider.equals("google-compute-engine")) {
            return GoogleComputeEngineVolumeCustomizer.withNewVolume(capacities);
        } else if (provider.equals("softlayer")) {
            return SoftlayerVolumeCustomizer.withNewVolume(capacities);
        } else {
            // TODO What is it for OpenStack? Something like:
            //      ((NovaTemplateOptions)templateOptions).diskConfig("something???seemswrong???");
            throw new IllegalArgumentException("Cannot handle volumes in location: " + location +
                    " (mismatch between isVolumeManagerSupportedForLocation and newVolumeManager)");
        }
    }


}
