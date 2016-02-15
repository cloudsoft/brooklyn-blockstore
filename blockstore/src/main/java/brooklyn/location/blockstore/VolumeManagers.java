package brooklyn.location.blockstore;

import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsSshMachineLocation;

import brooklyn.location.blockstore.api.VolumeManager;
import brooklyn.location.blockstore.ec2.Ec2VolumeManager;
import brooklyn.location.blockstore.gce.GoogleComputeEngineVolumeManager;
import brooklyn.location.blockstore.openstack.OpenstackVolumeManager;
import brooklyn.location.blockstore.rackspace.RackspaceVolumeManager;

public class VolumeManagers {

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
                provider.equals("openstack-nova") ||
                provider.startsWith("rackspace-") ||
                provider.startsWith("cloudservers-") ||
                provider.equals("google-compute-engine");
    }

    /**
     * Returns a {@link VolumeManager} suitable for the given location, for creating volumes.
     * 
     * See {@link #isVolumeManagerSupportedForLocation(Location)} to check before hand if a
     * given location is supported.
     * 
     * @param location A {@link brooklyn.location.MachineLocation location} where volumes are to be created.
     * @return A {@link VolumeManager} suitable for creating volumes in the given location.
     * @throws IllegalArgumentException If {@link #isVolumeManagerSupportedForLocation}
     *         returns false for the location argument.
     */
    public static VolumeManager newVolumeManager(Location location) {
        // TODO Add SoftLayer support
        
        if (location == null || !isVolumeManagerSupportedForLocation(location)) {
            throw new IllegalArgumentException("Cannot handle volumes in location: " + location);
        }

        JcloudsLocation jcloudsLocation;
        if (location instanceof JcloudsLocation) {
            jcloudsLocation = JcloudsLocation.class.cast(location);
        } else {
            jcloudsLocation = JcloudsSshMachineLocation.class.cast(location).getParent();
        }
        String provider = jcloudsLocation.getProvider();

        if (provider.equals("aws-ec2")) {
            return new Ec2VolumeManager();
        } else if (provider.startsWith("rackspace-") || provider.startsWith("cloudservers-")) {
            return new RackspaceVolumeManager();
        } else if (provider.equals("google-compute-engine")) {
            return new GoogleComputeEngineVolumeManager();
        } else if (provider.startsWith("openstack-nova")) {
            return new OpenstackVolumeManager();
        } else {
            throw new IllegalArgumentException("Cannot handle volumes in location: " + location +
                    " (mismatch between isVolumeManagerSupportedForLocation and newVolumeManager)");
        }
    }


}
