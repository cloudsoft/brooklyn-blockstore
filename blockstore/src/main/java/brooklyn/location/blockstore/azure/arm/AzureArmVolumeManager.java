package brooklyn.location.blockstore.azure.arm;

import static com.google.common.base.Preconditions.checkNotNull;

import java.net.URI;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsMachineLocation;
import org.apache.brooklyn.util.repeat.Repeater;
import org.jclouds.azurecompute.arm.AzureComputeApi;
import org.jclouds.azurecompute.arm.compute.options.AzureTemplateOptions;
import org.jclouds.azurecompute.arm.domain.DataDisk;
import org.jclouds.azurecompute.arm.domain.ResourceGroup;
import org.jclouds.azurecompute.arm.domain.StorageProfile;
import org.jclouds.azurecompute.arm.domain.VHD;
import org.jclouds.azurecompute.arm.domain.VirtualMachine;
import org.jclouds.azurecompute.arm.features.ResourceGroupApi;
import org.jclouds.azurecompute.arm.features.StorageAccountApi;
import org.jclouds.azurecompute.arm.features.VirtualMachineApi;
import org.jclouds.azurecompute.arm.functions.ParseJobStatus;
import org.jclouds.azurecompute.arm.functions.ParseJobStatus.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;

import brooklyn.location.blockstore.AbstractVolumeManager;
import brooklyn.location.blockstore.BlockDeviceOptions;
import brooklyn.location.blockstore.FilesystemOptions;
import brooklyn.location.blockstore.api.AttachedBlockDevice;
import brooklyn.location.blockstore.api.BlockDevice;
import brooklyn.location.blockstore.api.MountedBlockDevice;

public class AzureArmVolumeManager extends AbstractVolumeManager {
    
    // Azure "unmanaged disks" don't support separate creation and attachment phases.
    // Therefore we override methods like createAttachAndMountVolume. If/when we switch
    // to using azure's DiskApi then we can refactor this code again. However, that is
    // not yet supported in jclouds (v2.0.1).
    
    private static final Logger LOG = LoggerFactory.getLogger(AzureArmVolumeManager.class);

    private static final String PROVIDER = "azurecompute-arm";
    
    private static final String DEVICE_PREFIX = "/dev/sd";
    private static final String OS_DEVICE_PREFIX = "/dev/xvd";

    @Override
    public MountedBlockDevice createAttachAndMountVolume(JcloudsMachineLocation machine, BlockDeviceOptions deviceOptions,
            FilesystemOptions filesystemOptions) {
        AttachedBlockDevice attached = createAndAttachBlockDevice(machine, deviceOptions);
        createFilesystem(attached, filesystemOptions);
        return mountFilesystem(attached, filesystemOptions);
    }

    @Override
    public MountedBlockDevice attachAndMountVolume(JcloudsMachineLocation machine, BlockDevice device,
            BlockDeviceOptions options, FilesystemOptions filesystemOptions) {
        throw new UnsupportedOperationException("Cannot attach pre-existing block-device using 'unmanaged disk' api: machine="+machine+"; device="+device);
    }

    @Override
    public BlockDevice unmountFilesystemAndDetachVolume(MountedBlockDevice device) {
        throw new UnsupportedOperationException("Cannot detach block-device using 'unmanaged disk' api: device="+device);
    }

    @Override
    public BlockDevice createBlockDevice(JcloudsLocation location, BlockDeviceOptions options) {
        throw new UnsupportedOperationException("Cannot create block-device using 'unmanaged disk' api, without attaching to VM: location="+location+"; options="+options);
    }

    @Override
    public AttachedBlockDevice attachBlockDevice(JcloudsMachineLocation machine, BlockDevice device,
            BlockDeviceOptions options) {
        throw new UnsupportedOperationException("Cannot attach pre-existing block-device using 'unmanaged disk' api: machine="+machine+"; device="+device);
    }
    
    @Override
    public BlockDevice detachBlockDevice(AttachedBlockDevice device) {
        throw new UnsupportedOperationException("Cannot deattach pre-existing block-device using 'unmanaged disk' api: device="+device);
    }

    @Override
    protected String getVolumeDeviceName(char deviceSuffix) {
        return DEVICE_PREFIX + deviceSuffix;
    }

    @Override
    protected String getOSDeviceName(char deviceSuffix) {
        return OS_DEVICE_PREFIX + deviceSuffix;
    }

    protected AttachedBlockDevice createAndAttachBlockDevice(JcloudsMachineLocation machine, BlockDeviceOptions options) {
        JcloudsLocation location = machine.getParent();
        final AzureComputeApi azureArmComputeApi = getApi(location);

        LOG.info("Creating device: location={}; machine={}; options={}", new Object[] {location, machine, options});
        AzureTemplateOptions templateOptions = location.getComputeService().templateOptions().as(AzureTemplateOptions.class);
        VHD vhd = VHD.create(templateOptions.getBlob() + "vhds/" + location.getId() + "data.vhd");
        DataDisk dataDisk = DataDisk.create(location.getId() + "data", Integer.toString(options.getSizeInGb()), 0, vhd, "Empty");
        waitForJobToComplete(azureArmComputeApi, 2 * 60,  URI.create(vhd.uri()));
        
        BlockDevice blockDevice= new AzureArmBlockDevice(location, dataDisk);

        LOG.info("Attaching device: machine={}; device={}; options={}", new Object[] {machine, blockDevice, options});
        DataDisk disk = AzureArmBlockDevice.class.cast(blockDevice).getDisk();
        
        ResourceGroupApi rgApi = azureArmComputeApi.getResourceGroupApi();
        ResourceGroup resourceGroup = rgApi.get(machine.getId());
        VirtualMachineApi vmApi = azureArmComputeApi.getVirtualMachineApi(resourceGroup.name());
        
        StorageAccountApi storageApi = azureArmComputeApi.getStorageAccountApi(resourceGroup.name());
        VirtualMachine vm = vmApi.get(machine.getId());

        StorageProfile storageProfile = vm.properties().storageProfile();
        List<DataDisk> dataDisks = vm.properties().storageProfile().dataDisks();

        dataDisks.add(disk);
        StorageProfile.create(storageProfile.imageReference(), storageProfile.osDisk(), dataDisks);
        return blockDevice.attachedTo(machine, getVolumeDeviceName(options.getDeviceSuffix()));
    }

    @Override
    public void deleteBlockDevice(BlockDevice blockDevice) {
        // TODO Auto-generated method stub
        
    }
    
    protected AzureComputeApi getApi(JcloudsLocation location) {
        return location.getComputeService().getContext().unwrapApi(AzureComputeApi.class);
    }
    
    private JobStatus waitForJobToComplete(final AzureComputeApi api, final int waitTimeInSecs, final URI uri) {

        checkNotNull(uri, "uri must not be null");
        final AtomicReference<JobStatus> latest = new AtomicReference<JobStatus>();
        boolean done = Repeater.create("Waiting job for URI to completee: " + uri.toString())
                .every(1, TimeUnit.SECONDS)
                .limitTimeTo(waitTimeInSecs, TimeUnit.SECONDS)
                .until(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        JobStatus currentJobStatus = api.getJobApi().jobStatus(uri);
                        latest.set(currentJobStatus);
                        return currentJobStatus == ParseJobStatus.JobStatus.DONE;
                    }
                })
                .run();
        if (done) {
            return latest.get();
        } else {
            LOG.error("Job for URI {} still not DONE after timeout. Trying to continue. Last poll found: {}", uri.toString(), latest.get());
            return latest.get();
        }
    }

    private static class AzureArmBlockDevice implements BlockDevice {

        private static final Logger LOG = LoggerFactory.getLogger(AzureArmBlockDevice.class);

        private final JcloudsLocation location;
        private final DataDisk disk;

        private AzureArmBlockDevice(JcloudsLocation location, DataDisk disk) {
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

        public DataDisk getDisk() {
            return disk;
        }

        @Override
        public AzureArmAttachedBlockDevice attachedTo(JcloudsMachineLocation machine, String deviceName) {
            if (!machine.getParent().equals(location)) {
                LOG.warn("Attaching device to machine in different location to its creation: id={}, location={}, machine={}",
                        new Object[]{getId(), location, machine});
            }
            return new AzureArmAttachedBlockDevice(machine, disk, deviceName);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("id", getId())
                    .add("location", location)
                    .toString();
        }
    }

    private static class AzureArmAttachedBlockDevice extends AzureArmBlockDevice implements AttachedBlockDevice {

        private final JcloudsMachineLocation machine;
        private final String deviceName;

        private AzureArmAttachedBlockDevice(JcloudsMachineLocation machine, DataDisk disk, String deviceName) {
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
            return new AzureArmMountedBlockDevice(this, mountPoint);
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

    private static class AzureArmMountedBlockDevice extends AzureArmAttachedBlockDevice implements MountedBlockDevice {
        private final String mountPoint;

        private AzureArmMountedBlockDevice(AzureArmAttachedBlockDevice attachedDevice, String mountPoint) {
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
