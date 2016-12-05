package brooklyn.location.blockstore;

import brooklyn.location.blockstore.vclouddirector15.VcloudVolumeManager;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsMachineLocation;

import brooklyn.location.blockstore.api.VolumeManager;
import brooklyn.location.blockstore.ec2.Ec2VolumeManager;
import brooklyn.location.blockstore.gce.GoogleComputeEngineVolumeManager;
import brooklyn.location.blockstore.openstack.OpenstackVolumeManager;
import brooklyn.location.blockstore.rackspace.RackspaceVolumeManager;

public class VolumeManagers {
    public static final String AWS_EC2 = "aws-ec2";
    public static final String OPENSTACK_NOVA = "openstack-nova";
    public static final String VCLOUD_DIRECTOR = "vcloud-director";
    public static final String GOOGLE_COMPUTE_ENGINE = "google-compute-engine";
    public static final String SOFTLAYER = "softlayer";

    private VolumeManagers() {}

    /**
     * @return True if {@link #newVolumeManager(Location)} can create a {@link VolumeManager}
     *         for the given location.
     */
    public static boolean isVolumeManagerSupportedForLocation(Location location) {
        if (location == null || !(location instanceof JcloudsLocation || location instanceof JcloudsMachineLocation))
            return false;
        String provider;
        if (location instanceof JcloudsLocation) {
            provider = JcloudsLocation.class.cast(location).getProvider();
        } else {
            provider = JcloudsMachineLocation.class.cast(location).getParent().getProvider();
        }
        return provider.equals(AWS_EC2) ||
                provider.equals(OPENSTACK_NOVA) ||
                provider.startsWith("rackspace-") ||
                provider.startsWith("cloudservers-") ||
                provider.equals(GOOGLE_COMPUTE_ENGINE) ||
                provider.equals(VCLOUD_DIRECTOR);
    }

    /**
     * Returns a {@link VolumeManager} suitable for the given location, for creating volumes.
     * 
     * See {@link #isVolumeManagerSupportedForLocation(Location)} to check before hand if a
     * given location is supported.
     * 
     * @param location A {@link org.apache.brooklyn.api.location.MachineLocation location} where volumes are to be created.
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
            jcloudsLocation = JcloudsMachineLocation.class.cast(location).getParent();
        }
        String provider = jcloudsLocation.getProvider();

        if (provider.equals(AWS_EC2)) {
            return new Ec2VolumeManager();
        } else if (provider.startsWith("rackspace-") || provider.startsWith("cloudservers-")) {
            return new RackspaceVolumeManager();
        } else if (provider.equals(GOOGLE_COMPUTE_ENGINE)) {
            return new GoogleComputeEngineVolumeManager();
        } else if (provider.startsWith(OPENSTACK_NOVA)) {
            return new OpenstackVolumeManager();
        } else if (provider.equals(VCLOUD_DIRECTOR)) {
            return new VcloudVolumeManager();
        } else {
            throw new IllegalArgumentException("Cannot handle volumes in location: " + location +
                    " (mismatch between isVolumeManagerSupportedForLocation and newVolumeManager)");
        }
    }
}
