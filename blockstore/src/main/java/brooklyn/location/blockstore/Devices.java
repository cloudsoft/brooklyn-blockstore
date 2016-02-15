package brooklyn.location.blockstore;

import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsSshMachineLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects;

import brooklyn.location.blockstore.api.AttachedBlockDevice;
import brooklyn.location.blockstore.api.BlockDevice;
import brooklyn.location.blockstore.api.MountedBlockDevice;

public class Devices {

    private Devices() {}

    public static BlockDevice newBlockDevice(JcloudsLocation location, String id) {
        return new BlockDeviceImpl(location, id);
    }

    private static class BlockDeviceImpl implements BlockDevice {

        private static final Logger LOG = LoggerFactory.getLogger(BlockDeviceImpl.class);

        private final JcloudsLocation location;
        private final String id;

        private BlockDeviceImpl(JcloudsLocation location, String id) {
            this.location = location;
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public JcloudsLocation getLocation() {
            return location;
        }

        @Override
        public AttachedBlockDeviceImpl attachedTo(JcloudsSshMachineLocation machine, String deviceName) {
            if (!machine.getParent().equals(location)) {
                LOG.warn("Attaching device to machine in different location to its creation: id={}, location={}, machine={}",
                        new Object[]{id, location, machine});
            }
            return new AttachedBlockDeviceImpl(machine, getId(), deviceName);
        }

        @Override
        public String toString() {
            return Objects.toStringHelper(this)
                    .add("id", id)
                    .add("location", location)
                    .toString();
        }
    }

    private static class AttachedBlockDeviceImpl extends BlockDeviceImpl implements AttachedBlockDevice {

        private final JcloudsSshMachineLocation machine;
        private final String deviceName;

        private AttachedBlockDeviceImpl(JcloudsSshMachineLocation machine, String id, String deviceName) {
            super(machine.getParent(), id);
            this.machine = machine;
            this.deviceName = deviceName;
        }

        @Override
        public String getDeviceName() {
            return deviceName;
        }

        @Override
        public char getDeviceSuffix() {
            return getDeviceName().charAt(getDeviceName().length()-1);
        }

        @Override
        public JcloudsSshMachineLocation getMachine() {
            return machine;
        }

        @Override
        public MountedBlockDevice mountedAt(String mountPoint) {
            return new MountedBlockDeviceImpl(this, mountPoint);
        }

        @Override
        public String toString() {
            return Objects.toStringHelper(this)
                    .add("id", getId())
                    .add("machine", getMachine())
                    .add("deviceName", getDeviceName())
                    .toString();
        }

    }

    private static class MountedBlockDeviceImpl extends AttachedBlockDeviceImpl implements MountedBlockDevice {
        private final String mountPoint;

        private MountedBlockDeviceImpl(AttachedBlockDevice attachedDevice, String mountPoint) {
            super(attachedDevice.getMachine(), attachedDevice.getId(), attachedDevice.getDeviceName());
            this.mountPoint = mountPoint;
        }

        @Override
        public String getMountPoint() {
            return mountPoint;
        }

        @Override
        public String toString() {
            return Objects.toStringHelper(this)
                    .add("id", getId())
                    .add("machine", getMachine())
                    .add("deviceName", getDeviceName())
                    .add("mountPoint", getMountPoint())
                    .toString();
        }

    }

}
