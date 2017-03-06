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
import org.apache.brooklyn.util.text.Identifiers;
import org.apache.brooklyn.util.text.StringShortener;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.time.Duration;
import org.jclouds.azurecompute.arm.AzureComputeApi;
import org.jclouds.azurecompute.arm.domain.DataDisk;
import org.jclouds.azurecompute.arm.domain.ResourceGroup;
import org.jclouds.azurecompute.arm.domain.StorageProfile;
import org.jclouds.azurecompute.arm.domain.StorageService;
import org.jclouds.azurecompute.arm.domain.VHD;
import org.jclouds.azurecompute.arm.domain.VirtualMachine;
import org.jclouds.azurecompute.arm.domain.VirtualMachineProperties;
import org.jclouds.azurecompute.arm.features.JobApi;
import org.jclouds.azurecompute.arm.features.ResourceGroupApi;
import org.jclouds.azurecompute.arm.features.StorageAccountApi;
import org.jclouds.azurecompute.arm.features.VirtualMachineApi;
import org.jclouds.azurecompute.arm.functions.ParseJobStatus;
import org.jclouds.azurecompute.arm.functions.ParseJobStatus.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

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
    
    // TODO:
    //  - What should the "lun" number be? Make this configurable?
    //  - waitForJobToComplete: make timeouts configurable?
    //  - OSDeviceName: was "/dev/sdc" during testing; in contrast AWS has "/dev/xvdc".
    //    Is this dependent on the image? Do we need to make the prefix configurable?

    private static final Logger LOG = LoggerFactory.getLogger(AzureArmVolumeManager.class);

    @SuppressWarnings("unused")
    private static final String PROVIDER = "azurecompute-arm";
    
    private static final String DEVICE_PREFIX = "/dev/sd";
    private static final String OS_DEVICE_PREFIX = "/dev/sd";

    private static final Duration TIMEOUT = Duration.minutes(2);
    
    @Override
    public MountedBlockDevice createAttachAndMountVolume(JcloudsMachineLocation machine, BlockDeviceOptions deviceOptions,
            FilesystemOptions filesystemOptions) {
        AttachedBlockDevice attached = createAndAttachBlockDevice(machine, deviceOptions);
        createFilesystem(attached, filesystemOptions);
        return mountFilesystem(attached, filesystemOptions);
    }

    @Override
    public void deleteBlockDevice(BlockDevice blockDevice) {
        LOG.info("Deleting device: {}", blockDevice);
        
        AzureArmBlockDevice azureBlockDevice = (AzureArmBlockDevice) blockDevice;

        String resourceGroupName = azureBlockDevice.getResourceGroupName();
        String storageAccountName = azureBlockDevice.getStorageAccountName();
        AzureComputeApi api = getApi(blockDevice.getLocation());
        deleteStorageAccount(api, resourceGroupName, storageAccountName);
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

    @VisibleForTesting
    protected AzureComputeApi getApi(JcloudsLocation location) {
        return location.getComputeService().getContext().unwrapApi(AzureComputeApi.class);
    }
    
    private AttachedBlockDevice createAndAttachBlockDevice(JcloudsMachineLocation machine, BlockDeviceOptions options) {
        JcloudsLocation location = machine.getParent();
        String region = getRegionName(location);
        
        String machineId = machine.getJcloudsId();
        String unqualifiedMachineId = machineId.contains("/") ? machineId.substring(machineId.indexOf("/")) : machineId;
        String storageAccountName = newStorageAccountName(unqualifiedMachineId);

        final AzureComputeApi api = getApi(location);
        Optional<String> resourceGroupName = tryFindResourceGroupName(api, unqualifiedMachineId, region);
        if (!resourceGroupName.isPresent()) {
            throw new IllegalStateException("Cannot create disk; VM "+unqualifiedMachineId+" not found in any resource group, machine "+machine+" in "+location);
        }
        
        LOG.info("Creating and attaching device: location={}; machine={}; options={}; machineId={}; resourceGroupName={}", 
                new Object[] {location, machine, options, machineId, resourceGroupName.get()});

        VirtualMachineApi vmApi = api.getVirtualMachineApi(resourceGroupName.get());
        VirtualMachine vm = vmApi.get(unqualifiedMachineId);
        if (vm == null) {
            throw new IllegalStateException("Cannot create disk; VM "+unqualifiedMachineId+" not found in "+location+", resource group "+resourceGroupName+", for "+machine);
        }
        
        int numExistingDisks = coundDataDisks(vm);
        int lun = numExistingDisks; // starts from 0, so if have one disk already then next will be "1"  

        StorageService storageService = createStorageAccount(api, resourceGroupName.get(), storageAccountName, region);
        DataDisk dataDisk = addDisk(vmApi, vm, storageService, options.getSizeInGb(), lun);
        
        BlockDevice blockDevice = new AzureArmBlockDevice(location, dataDisk, resourceGroupName.get(), storageAccountName);
        return blockDevice.attachedTo(machine, getVolumeDeviceName(options.getDeviceSuffix()));
    }

    private int coundDataDisks(VirtualMachine vm) {
        VirtualMachineProperties properties = vm.properties();
        StorageProfile storageProfile = properties.storageProfile();
        List<DataDisk> dataDisks = (storageProfile != null) ? storageProfile.dataDisks() : null;
        return (dataDisks != null) ? dataDisks.size() : 0;
    }
    
    private String newStorageAccountName(String unqualifiedMachineId) {
        // e.g. error message from azure:
        //      "... is not a valid storage account name. Storage account name must be between 3 and 24 characters in length and use numbers and lower-case letters only."
        StringShortener shortener = Strings.shortener()
                .setAllowedCharacters(Identifiers.LOWER_CASE_ALPHA+Identifiers.NUMERIC)
                .append("machineId", unqualifiedMachineId)
                .append("timeStamp", Long.toString(System.currentTimeMillis() / 1000L, Character.MAX_RADIX))
                .canTruncate("machineId", 16)
                .canTruncate("timeStamp", 8);

        return shortener.getStringOfMaxLength(24);
    }

    private StorageService createStorageAccount(AzureComputeApi api, String resourceGroupName, String storageAccountName, String location) {
        StorageAccountApi storageAccountApi = api.getStorageAccountApi(resourceGroupName);
        URI uri = storageAccountApi.create(
                storageAccountName, 
                location, 
                ImmutableMap.of("property_name", "property_value"), 
                ImmutableMap.of("accountType", StorageService.AccountType.Standard_LRS.toString()));
        if (uri != null) {
           boolean success = waitForJobToComplete(api, uri, TIMEOUT);
           if (!success) {
               throw new IllegalStateException("Timeout after " + TIMEOUT + " creating Storage Account '" + storageAccountName + "' (" + uri + ") in resource group '" + resourceGroupName + "'");
           }
        } else {
            throw new IllegalStateException("Failed to create Storage Account '" + storageAccountName + "' in resource group '" + resourceGroupName + "'");
        }
        return storageAccountApi.get(storageAccountName);
    }

    private void deleteStorageAccount(AzureComputeApi api, String resourceGroupName, String storageAccountName) {
        StorageAccountApi storageAccountApi = api.getStorageAccountApi(resourceGroupName);
        boolean deleted = storageAccountApi.delete(storageAccountName);
        if (!deleted) {
            throw new IllegalStateException("Storage account " + storageAccountName + "' could not be deleted");
        }
    }

    private DataDisk addDisk(VirtualMachineApi vmApi, VirtualMachine vm, StorageService storageService, int diskSizeGB, int lun) {
        String vmName = vm.name();
        VirtualMachineProperties oldProperties = vm.properties();
        StorageProfile oldStorageProfile = oldProperties.storageProfile();
        List<DataDisk> oldDataDisks = oldStorageProfile.dataDisks();

        String blobEndpoint = storageService.storageServiceProperties().primaryEndpoints().get("blob");
        VHD vhd = VHD.create(blobEndpoint + "vhds/" + vmName + "new-data-disk.vhd");
        DataDisk newDataDisk = DataDisk.create(vmName + "new-data-disk", Integer.toString(diskSizeGB), lun, vhd, "Empty");

        ImmutableList<DataDisk> newDataDisks = ImmutableList.<DataDisk> builder().addAll(oldDataDisks).add(newDataDisk).build();
        StorageProfile newStorageProfile = oldStorageProfile.toBuilder().dataDisks(newDataDisks).build();
        VirtualMachineProperties newProperties = oldProperties.toBuilder().storageProfile(newStorageProfile).build();

        VirtualMachine newVm = vm.toBuilder().properties(newProperties).build();
        
        // FIXME Might we need to wait for a job? Or does the PUT call block?
        vm = vmApi.create(vmName, newVm.location(), newVm.properties(), newVm.tags(), newVm.plan());
        
        // Check the VM definitely contains the new data-disk
        return checkHasDataDisk(vm, newDataDisk.name());
    }
    
    private DataDisk checkHasDataDisk(VirtualMachine vm, String diskName) {
        for (DataDisk dataDisk : vm.properties().storageProfile().dataDisks()) {
            if (diskName.equals(dataDisk.name())) {
                return dataDisk;
            }
        }
        throw new IllegalStateException("VM "+vm+" does not include disk "+diskName);
    }
    
    private String getRegionName(JcloudsLocation location) {
        return location.getRegion();
    }

    private Optional<String> tryFindResourceGroupName(AzureComputeApi api, String vmName, String region) {
        String defaultResourceGroupName = "jclouds-" + region;
        if (resourceGroupContainsVm(api, defaultResourceGroupName, vmName)) {
            return Optional.of(defaultResourceGroupName);
        }
        
        ResourceGroupApi rgApi = api.getResourceGroupApi();
        for (ResourceGroup resourceGroup : rgApi.list()) {
            if (region != null && !region.equals(resourceGroup.location())) {
                continue; // only look in resource groups for this region
            }
            if (resourceGroupContainsVm(api, resourceGroup.name(), vmName)) {
                return Optional.of(resourceGroup.name());
            }
        }
        
        return Optional.absent();
    }
    
    private boolean resourceGroupContainsVm(AzureComputeApi api, String resourceGroupName, String vmName) {
        VirtualMachineApi vmApi = api.getVirtualMachineApi(resourceGroupName);
        return vmApi.get(vmName) != null;
    }

    private boolean waitForJobToComplete(final AzureComputeApi api, final URI uri, Duration waitTime) {
        checkNotNull(uri, "uri must not be null");
        final JobApi jobApi = api.getJobApi();
        final AtomicReference<JobStatus> latest = new AtomicReference<JobStatus>();
        
        boolean done = Repeater.create("Waiting job for URI to completee: " + uri)
                .every(1, TimeUnit.SECONDS)
                .limitTimeTo(waitTime)
                .until(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        JobStatus currentJobStatus = jobApi.jobStatus(uri);
                        latest.set(currentJobStatus);
                        return currentJobStatus == ParseJobStatus.JobStatus.DONE;
                    }
                })
                .run();
        return done;
    }

    private static class AzureArmBlockDevice implements BlockDevice {

        private static final Logger LOG = LoggerFactory.getLogger(AzureArmBlockDevice.class);

        private final JcloudsLocation location;
        private final DataDisk disk;
        private final String resourceGroupName;
        private final String storageAccountName;
        
        private AzureArmBlockDevice(JcloudsLocation location, DataDisk disk, String resourceGroupName, String storageAccountName) {
            this.location = checkNotNull(location, "location");
            this.disk = checkNotNull(disk, "disk");
            this.resourceGroupName = checkNotNull(resourceGroupName, "resourceGroupName");
            this.storageAccountName = checkNotNull(storageAccountName, "storageAccountName");
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

        public String getResourceGroupName() {
            return resourceGroupName;
        }
        
        public String getStorageAccountName() {
            return storageAccountName;
        }
        
        @Override
        public AzureArmAttachedBlockDevice attachedTo(JcloudsMachineLocation machine, String deviceName) {
            if (!machine.getParent().equals(location)) {
                LOG.warn("Attaching device to machine in different location to its creation: id={}, location={}, machine={}",
                        new Object[]{getId(), location, machine});
            }
            return new AzureArmAttachedBlockDevice(machine, disk, resourceGroupName, storageAccountName, deviceName);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("id", getId())
                    .add("resourceGroupName", resourceGroupName)
                    .add("storageAccountName", storageAccountName)
                    .add("location", location)
                    .toString();
        }
    }

    private static class AzureArmAttachedBlockDevice extends AzureArmBlockDevice implements AttachedBlockDevice {

        private final JcloudsMachineLocation machine;
        private final String deviceName;

        private AzureArmAttachedBlockDevice(JcloudsMachineLocation machine, DataDisk disk, 
                String resourceGroupName, String storageAccountName, String deviceName) {
            super(machine.getParent(), disk, resourceGroupName, storageAccountName);
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

        private AzureArmMountedBlockDevice(AzureArmAttachedBlockDevice device, String mountPoint) {
            super(device.getMachine(), device.getDisk(), device.getResourceGroupName(), 
                    device.getStorageAccountName(), device.getDeviceName());
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
