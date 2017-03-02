package brooklyn.location.blockstore;

import brooklyn.location.blockstore.api.VolumeManager;
import brooklyn.location.blockstore.ec2.Ec2VolumeManager;
import brooklyn.location.blockstore.openstack.OpenstackVolumeManager;
import brooklyn.location.blockstore.vclouddirector15.VcloudVolumeManager;
import org.apache.brooklyn.core.location.LocationConfigKeys;
import org.apache.brooklyn.location.jclouds.JcloudsMachineLocation;

import static brooklyn.location.blockstore.VolumeManagers.AWS_EC2;
import static brooklyn.location.blockstore.VolumeManagers.OPENSTACK_NOVA;
import static brooklyn.location.blockstore.VolumeManagers.VCLOUD_DIRECTOR;

/**
 * Creates a VolumeManager instance.
 */
public class VolumeManagerFactory {
    public static VolumeManager getVolumeManager(JcloudsMachineLocation machine) {
        return getVolumeManager(machine, null);
    }
    public static VolumeManager getVolumeManager(JcloudsMachineLocation machine, String provider) {
        if (provider == null) {
            provider = machine.getParent().getProvider();
        }

        switch (provider) {
            case AWS_EC2:
                return new Ec2VolumeManager();
            case OPENSTACK_NOVA:
                return new OpenstackVolumeManager();
            case VCLOUD_DIRECTOR:
                return new VcloudVolumeManager();
            default:
                throw new UnsupportedOperationException("Tried to attach volume for a cloud "
                        + provider + " which is not supported for adding disks. Caller entity " + machine.config().get(LocationConfigKeys.CALLER_CONTEXT));
        }
    }

}
