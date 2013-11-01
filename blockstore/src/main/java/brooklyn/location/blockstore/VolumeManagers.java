package brooklyn.location.blockstore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.Location;
import brooklyn.location.blockstore.api.VolumeManager;
import brooklyn.location.blockstore.ec2.Ec2VolumeManager;
import brooklyn.location.blockstore.gce.GoogleComputeEngineVolumeManager;
import brooklyn.location.blockstore.openstack.OpenstackVolumeManager;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.location.jclouds.JcloudsSshMachineLocation;

public class VolumeManagers {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleComputeEngineVolumeManager.class);

    private VolumeManagers() {}

    /**
     * @return True if {@link #newVolumeManager(Location)} can create a {@link VolumeManager}
     *         for the given location.
     */
    public static boolean isVolumeManagerSupportedForLocation(Location location) {
        if (location == null || !(location instanceof JcloudsLocation || location instanceof JcloudsSshMachineLocation))
            return false;
        String provider;
        if (location instanceof JcloudsLocation) {
            provider = JcloudsLocation.class.cast(location).getProvider();
        } else {
            provider = JcloudsSshMachineLocation.class.cast(location).getParent().getProvider();
        }
        return provider.equals("aws-ec2") ||
                provider.startsWith("rackspace-") ||
                provider.startsWith("cloudservers-") ||
                provider.equals("google-compute-engine");
    }

    /**
     * Returns a volume manager suitable for the given location if the location's provider matches any of:
     * <ul>
     *     <li>aws-ec2</li>
     *     <li>rackspace-*</li>
     *     <li>cloudservers-*</li>
     *     <li>google-compute-engine</li>
     * </ul>
     * @param location A {@link brooklyn.location.MachineLocation location} where volumes are to be created.
     * @return A {@link VolumeManager} suitable for creating volumes in the given location.
     * @throws IllegalArgumentException If {@link #isVolumeManagerSupportedForLocation}
     *         returns false for the location argument.
     */
    public static VolumeManager newVolumeManager(Location location) {
        if (location == null || !isVolumeManagerSupportedForLocation(location)) {
            throw new IllegalArgumentException("Cannot handle volumes in location: " + location);
        }

        String provider;
        if (location instanceof JcloudsLocation) {
            provider = JcloudsLocation.class.cast(location).getProvider();
        } else {
            provider = JcloudsSshMachineLocation.class.cast(location).getParent().getProvider();
        }

        if (provider.equals("aws-ec2")) {
            return new Ec2VolumeManager();
        } else if (provider.startsWith("rackspace-") || provider.startsWith("cloudservers-")) {
            return new OpenstackVolumeManager();
        } else if (provider.equals("google-compute-engine")) {
            return new GoogleComputeEngineVolumeManager();
        } else {
            throw new IllegalArgumentException("Cannot handle volumes in location: " + location +
                    " (mismatch between isVolumeManagerSupportedForLocation and newVolumeManager)");
        }
    }


}
