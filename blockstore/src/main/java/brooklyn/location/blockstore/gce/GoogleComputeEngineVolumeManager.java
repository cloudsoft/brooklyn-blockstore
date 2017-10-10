package brooklyn.location.blockstore.gce;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsMachineLocation;
import org.apache.brooklyn.util.repeat.Repeater;
import org.jclouds.ContextBuilder;
import org.jclouds.encryption.bouncycastle.config.BouncyCastleCryptoModule;
import org.jclouds.googlecomputeengine.GoogleComputeEngineApi;
import org.jclouds.googlecomputeengine.domain.AttachDisk;
import org.jclouds.googlecomputeengine.domain.Disk;
import org.jclouds.googlecomputeengine.domain.Operation;
import org.jclouds.googlecomputeengine.features.DiskApi;
import org.jclouds.googlecomputeengine.features.InstanceApi;
import org.jclouds.googlecomputeengine.options.DiskCreationOptions;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.sshj.config.SshjSshClientModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Module;

import brooklyn.location.blockstore.AbstractVolumeManager;
import brooklyn.location.blockstore.BlockDeviceOptions;
import brooklyn.location.blockstore.api.AttachedBlockDevice;
import brooklyn.location.blockstore.api.BlockDevice;
import brooklyn.location.blockstore.api.MountedBlockDevice;

public class GoogleComputeEngineVolumeManager extends AbstractVolumeManager {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleComputeEngineVolumeManager.class);
    private static final String PROVIDER = "google-compute-engine";
    private static final String DEVICE_PREFIX = "/dev/disk/by-id/google-";

    @Override
    protected String getVolumeDeviceName(char deviceSuffix) {
        return DEVICE_PREFIX + deviceSuffix;
    }

    @Override
    protected String getOSDeviceName(char deviceSuffix) {
        return getVolumeDeviceName(deviceSuffix);
    }

    @Override
    public BlockDevice createBlockDevice(JcloudsLocation location, BlockDeviceOptions options) {
        LOG.info("Creating device: location={}; options={}", location, options);

        GoogleComputeEngineApi computeApi = getGoogleComputeEngineApi(location);
        DiskApi diskApi = computeApi.disksInZone(options.getZone());
        String name = getOrMakeName(location, options);

        DiskCreationOptions diskOptions = new DiskCreationOptions.Builder()
		        .sizeGb(options.getSizeInGb())
		        .build();
        Operation operation = diskApi.create(name, diskOptions);
        waitForOperationToBeDone(computeApi, operation);

        Disk created = diskApi.get(name);
        LOG.info("Created device: location={}, device={}", location, created);
        return new GCEBlockDevice(location, created);
    }

    @Override
    public AttachedBlockDevice attachBlockDevice(JcloudsMachineLocation machine, BlockDevice device, BlockDeviceOptions options) {
        checkArgument(device instanceof GCEBlockDevice, "GCE volume manager cannot handle device: %s", device);
        Disk disk = GCEBlockDevice.class.cast(device).getDisk();
        LOG.info("Attaching device: machine={}; device={}; options={}", new Object[]{machine, device, options});

        JcloudsLocation location = machine.getParent();
        GoogleComputeEngineApi computeApi = getGoogleComputeEngineApi(location);
        String zone = getZoneFromDisk(disk);
        InstanceApi instanceApi = computeApi.instancesInZone(zone);

        Operation operation = instanceApi.attachDisk(machine.getNode().getName(), AttachDisk.existingDisk(disk.selfLink()));

        waitForOperationToBeDone(computeApi, operation);
        return device.attachedTo(machine, getVolumeDeviceName(options.getDeviceSuffix()));

        // FIXME Previous code shown below - is this AttachDisk.existingDisk functionally equivalent?!
        // How do we force it to have options.getDeviceSuffix()?
//        new AttachDiskOption()
//                .source(disk.selfLink())
//                .type(AttachDisk.Type.PERSISTENT)
//                .mode(AttachDisk.Mode.READ_WRITE)
//                .deviceName(String.valueOf(options.getDeviceSuffix())));
    }

    @Override
    public BlockDevice detachBlockDevice(AttachedBlockDevice device) {
        checkArgument(device instanceof GCEBlockDevice, "GCE volume manager cannot handle device: %s", device);
        Disk disk = GCEBlockDevice.class.cast(device).getDisk();
        LOG.info("Detaching device: {}", device);

        GoogleComputeEngineApi computeApi = getGoogleComputeEngineApi(device.getLocation());
        String zone = getZoneFromDisk(disk);
        InstanceApi instanceApi = computeApi.instancesInZone(zone);

		Operation operation = instanceApi.detachDisk(
                device.getMachine().getNode().getName(), 
                String.valueOf(device.getDeviceSuffix()));
        waitForOperationToBeDone(computeApi, operation);

        return new GCEBlockDevice(device.getLocation(), disk);
    }

    @Override
    public void deleteBlockDevice(BlockDevice device) {
        checkArgument(device instanceof GCEBlockDevice, "GCE volume manager cannot handle device: %s", device);
        Disk disk = GCEBlockDevice.class.cast(device).getDisk();
        LOG.info("Deleting device: {}", device);

        GoogleComputeEngineApi computeApi = getGoogleComputeEngineApi(device.getLocation());
        String zone = getZoneFromDisk(disk);
        DiskApi diskApi = computeApi.disksInZone(zone);

		Operation operation = diskApi.delete(device.getId());
        waitForOperationToBeDone(computeApi, operation);
    }

    /**
     * Describes the given volume. Or returns null if it is not found.
     */
    public Disk describeVolume(BlockDevice device) {
        checkArgument(device instanceof GCEBlockDevice, "GCE volume manager cannot handle device: %s", device);
        Disk disk = GCEBlockDevice.class.cast(device).getDisk();
        if (LOG.isDebugEnabled())
            LOG.debug("Describing device: {}", device);
        GoogleComputeEngineApi computeApi = getGoogleComputeEngineApi(device.getLocation());
        String zone = getZoneFromDisk(disk);
        DiskApi diskApi = computeApi.disksInZone(zone);
		return diskApi.get(device.getId());
    }

    private GoogleComputeEngineApi getGoogleComputeEngineApi(JcloudsLocation location) {
        String identity = location.getIdentity();
        String credential = location.getCredential();
        Iterable<Module> modules = ImmutableSet.<Module> of(
                new SshjSshClientModule(),
                new SLF4JLoggingModule(),
                new BouncyCastleCryptoModule());

        return ContextBuilder.newBuilder(PROVIDER)
              .credentials(identity, credential)
              .modules(modules)
              .buildApi(GoogleComputeEngineApi.class);
    }

    private String getZoneFromDisk(Disk disk) {
        // extracts from URL like https://www.googleapis.com/compute/v1beta15/projects/jclouds-gce/zones/europe-west1-a
        String zonePath = disk.zone().getPath();
        return zonePath.substring(zonePath.lastIndexOf('/')+1);
    }

    private Operation waitForOperationToBeDone(final GoogleComputeEngineApi api, final Operation operation) {

        checkNotNull(operation, "operation should not be null");
        final AtomicReference<Operation> latest = new AtomicReference<Operation>(operation);
        boolean done = Repeater.create("Waiting for operation to be done: " + operation.name())
                .every(1, TimeUnit.SECONDS)
                .limitTimeTo(60, TimeUnit.SECONDS)
                .until(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                    	Operation current = api.operations().get(operation.selfLink());
                        latest.set(current);
                        return current.status() == Operation.Status.DONE;
                    }
                })
                .run();
        if (done) {
            return latest.get();
        } else {
            LOG.error("Operation {} still incomplete after timeout. Trying to continue. Last poll found: {}", operation.name(), latest.get());
            return latest.get();
        }
    }

    // GCE-specific classes used rather than those in Devices to keep track of the Disk object through a Volume's life
    private static class GCEBlockDevice implements BlockDevice {

        private static final Logger LOG = LoggerFactory.getLogger(GCEBlockDevice.class);

        private final JcloudsLocation location;
        private final Disk disk;

        private GCEBlockDevice(JcloudsLocation location, Disk disk) {
            this.location = checkNotNull(location, "location");
            this.disk = checkNotNull(disk, "disk");
        }

        @Override
        public String getId() {
            return disk.name();
        }

        @Override
        public JcloudsLocation getLocation() {
            return location;
        }

        public Disk getDisk() {
            return disk;
        }

        @Override
        public GCEAttachedBlockDevice attachedTo(JcloudsMachineLocation machine, String deviceName) {
            if (!machine.getParent().equals(location)) {
                LOG.warn("Attaching device to machine in different location to its creation: id={}, location={}, machine={}",
                        new Object[]{getId(), location, machine});
            }
            return new GCEAttachedBlockDevice(machine, disk, deviceName);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("id", getId())
                    .add("location", location)
                    .toString();
        }
    }

    private static class GCEAttachedBlockDevice extends GCEBlockDevice implements AttachedBlockDevice {

        private final JcloudsMachineLocation machine;
        private final String deviceName;

        private GCEAttachedBlockDevice(JcloudsMachineLocation machine, Disk disk, String deviceName) {
            super(machine.getParent(), disk);
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
        public JcloudsMachineLocation getMachine() {
            return machine;
        }

        @Override
        public MountedBlockDevice mountedAt(String mountPoint) {
            return new GCEMountedBlockDevice(this, mountPoint);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("id", getId())
                    .add("machine", getMachine())
                    .add("deviceName", getDeviceName())
                    .toString();
        }

    }

    private static class GCEMountedBlockDevice extends GCEAttachedBlockDevice implements MountedBlockDevice {
        private final String mountPoint;

        private GCEMountedBlockDevice(GCEAttachedBlockDevice attachedDevice, String mountPoint) {
            super(attachedDevice.getMachine(), attachedDevice.getDisk(), attachedDevice.getDeviceName());
            this.mountPoint = mountPoint;
        }

        @Override
        public String getMountPoint() {
            return mountPoint;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("id", getId())
                    .add("machine", getMachine())
                    .add("deviceName", getDeviceName())
                    .add("mountPoint", getMountPoint())
                    .toString();
        }

    }
}
