package brooklyn.location.blockstore.azure.arm;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsMachineLocation;
import org.apache.brooklyn.util.repeat.Repeater;
import org.jclouds.ContextBuilder;
import org.jclouds.azurecompute.arm.AzureComputeApi;
import org.jclouds.azurecompute.arm.AzureComputeProviderMetadata;
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
import org.jclouds.encryption.bouncycastle.config.BouncyCastleCryptoModule;
import org.jclouds.googlecomputeengine.domain.Disk;
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

public class AzureArmVolumeManager extends AbstractVolumeManager {
    private static final Logger LOG = LoggerFactory.getLogger(AzureArmVolumeManager.class);

    private static final String PROVIDER = "azurecompute-arm";
    
    private static final String DEVICE_PREFIX = "/dev/sd";
    private static final String OS_DEVICE_PREFIX = "/dev/xvd";

    @Override
    protected String getVolumeDeviceName(char deviceSuffix) {
        return DEVICE_PREFIX + deviceSuffix;
    }

    @Override
    protected String getOSDeviceName(char deviceSuffix) {
        return OS_DEVICE_PREFIX + deviceSuffix;
    }

    @Override
    public BlockDevice createBlockDevice(JcloudsLocation location, BlockDeviceOptions options) {
        LOG.info("Creating device: location={}; options={}", location, options);

        final AzureComputeApi azureArmComputeApi = getAzureArmApi(location);

        AzureTemplateOptions templateOptions = location.getComputeService().templateOptions().as(AzureTemplateOptions.class);
        VHD vhd = VHD.create(templateOptions.getBlob() + "vhds/" + location.getId() + "data.vhd");
        DataDisk dataDisk = DataDisk.create(location.getId() + "data", Integer.toString(options.getSizeInGb()), 0, vhd, "Empty");
        waitForJobToComplete(azureArmComputeApi, 2 * 60,  URI.create(vhd.uri()));

        LOG.info("Created device: location={}, device={}", location, dataDisk);
        return new AzureArmBlockDevice(location, dataDisk);
    }

    @Override
    public AttachedBlockDevice attachBlockDevice(JcloudsMachineLocation machine, BlockDevice blockDevice,
            BlockDeviceOptions options) {
        checkArgument(blockDevice instanceof AzureArmBlockDevice, "AzureArm volume manager cannot handle device: %s", blockDevice);
        DataDisk disk = AzureArmBlockDevice.class.cast(blockDevice).getDisk();
        LOG.info("Attaching device: machine={}; device={}; options={}", new Object[]{machine, blockDevice, options});
        
        AzureComputeApi azureArmComputeApi = getAzureArmApi(machine.getParent());
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
    public BlockDevice detachBlockDevice(AttachedBlockDevice attachedBlockDevice) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void deleteBlockDevice(BlockDevice blockDevice) {
        // TODO Auto-generated method stub
        
    }
    
    private AzureComputeApi getAzureArmApi(JcloudsLocation location) {
        String identity = location.getIdentity();
        String credential = location.getCredential();
        Iterable<Module> modules = ImmutableSet.<Module> of(
                new SshjSshClientModule(),
                new SLF4JLoggingModule(),
                new BouncyCastleCryptoModule());

        AzureComputeProviderMetadata pm = AzureComputeProviderMetadata.builder().build();
        return ContextBuilder.newBuilder(pm)
              .credentials(identity, credential)
              .modules(modules)
              .build();
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
