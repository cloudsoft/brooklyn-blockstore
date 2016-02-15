package brooklyn.location.blockstore.api;

import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsSshMachineLocation;

public interface BlockDevice {

    /**
     * @return This device's ID
     */
    public String getId();

    /**
     * @return The location that contains this device
     */
    public JcloudsLocation getLocation();

    // TODO: Bit confusing that this doesn't actually attach the device.
    /**
     * Creates an instance of {@link AttachedBlockDevice} with device name set to that given.
     * Intended for use internally. Does <b>not</b> attach the device to the given machine.
     */
    AttachedBlockDevice attachedTo(JcloudsSshMachineLocation machine, String deviceName);
}
