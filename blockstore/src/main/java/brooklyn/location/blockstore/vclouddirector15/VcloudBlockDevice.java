package brooklyn.location.blockstore.vclouddirector15;

import brooklyn.location.blockstore.Devices;
import brooklyn.location.blockstore.api.AttachedBlockDevice;
import brooklyn.location.blockstore.api.MountedBlockDevice;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsMachineLocation;
import org.jclouds.vcloud.director.v1_5.domain.Vm;
import org.jclouds.vcloud.director.v1_5.domain.dmtf.RasdItem;

public class VcloudBlockDevice implements AttachedBlockDevice {
    private RasdItem rasdItem;
    private JcloudsMachineLocation jcloudsMachineLocation;
    private Vm vm;

    public VcloudBlockDevice(RasdItem rasdItem, JcloudsMachineLocation jcloudsMachineLocation, Vm vm) {
        this.rasdItem = rasdItem;
        this.jcloudsMachineLocation = jcloudsMachineLocation;
        this.vm = vm;
    }

    /**
     * @return Vm for testing purposes.
     */
    public Vm getVm() {
        return vm;
    }

    @Override
    public String getId() {
        return rasdItem.getInstanceID();
    }

    @Override
    public JcloudsLocation getLocation() {
        return jcloudsMachineLocation.getParent();
    }

    @Override
    public AttachedBlockDevice attachedTo(JcloudsMachineLocation machine, String deviceName) {
        return this;
    }

    @Override
    public String toString() {
        return rasdItem.toString();
    }

    @Override
    public String getDeviceName() {
        return "/dev/sd" + getDeviceSuffix();
    }

    @Override
    public char getDeviceSuffix() {
        return ((char)('a' + Integer.parseInt(rasdItem.getAddressOnParent())));
    }

    @Override
    public JcloudsMachineLocation getMachine() {
        return jcloudsMachineLocation;
    }

    @Override
    public MountedBlockDevice mountedAt(String mountPoint) {
        return new Devices.MountedBlockDeviceImpl(this, mountPoint);
    }
}
