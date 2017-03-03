package brooklyn.location.blockstore.vclouddirector15;

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
    private String osDeviceName;

    public VcloudBlockDevice(RasdItem rasdItem, JcloudsMachineLocation jcloudsMachineLocation, Vm vm, String osDeviceName) {
        this.rasdItem = rasdItem;
        this.jcloudsMachineLocation = jcloudsMachineLocation;
        this.vm = vm;
        this.osDeviceName = osDeviceName;
    }

    protected VcloudBlockDevice(VcloudBlockDevice vcloudBlockDevice) {
        this.rasdItem = vcloudBlockDevice.rasdItem;
        this.jcloudsMachineLocation = vcloudBlockDevice.jcloudsMachineLocation;
        this.vm = vcloudBlockDevice.vm;
        this.osDeviceName = vcloudBlockDevice.osDeviceName;
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
        return osDeviceName;
    }

    /**
     * Better criteria is for device suffix to filter all should count all disks and put the latter as the new suffix.
     */
    @Override
    public char getDeviceSuffix() {
        return getDeviceName().charAt(getDeviceName().length()-1);
    }

    @Override
    public JcloudsMachineLocation getMachine() {
        return jcloudsMachineLocation;
    }

    @Override
    public MountedBlockDevice mountedAt(String mountPoint) {
        return new VcloudMountedBlockDevice(this, mountPoint);
    }
}
