package brooklyn.location.blockstore.vclouddirector15;

import brooklyn.location.blockstore.AbstractVolumeManager;
import brooklyn.location.blockstore.BlockDeviceOptions;
import brooklyn.location.blockstore.FilesystemOptions;
import brooklyn.location.blockstore.api.AttachedBlockDevice;
import brooklyn.location.blockstore.api.BlockDevice;
import brooklyn.location.blockstore.api.MountedBlockDevice;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsMachineLocation;
import org.apache.brooklyn.util.repeat.Repeater;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.util.Predicates2;
import org.jclouds.vcloud.director.v1_5.VCloudDirectorApi;
import org.jclouds.vcloud.director.v1_5.domain.RasdItemsList;
import org.jclouds.vcloud.director.v1_5.domain.Task;
import org.jclouds.vcloud.director.v1_5.domain.Vm;
import org.jclouds.vcloud.director.v1_5.domain.dmtf.RasdItem;
import org.jclouds.vcloud.director.v1_5.domain.dmtf.cim.CimString;
import org.jclouds.vcloud.director.v1_5.features.TaskApi;
import org.jclouds.vcloud.director.v1_5.features.VmApi;
import org.jclouds.vcloud.director.v1_5.functions.AddScsiLogicSASBus;
import org.jclouds.vcloud.director.v1_5.functions.NewScsiLogicSASDisk;
import org.jclouds.vcloud.director.v1_5.predicates.TaskSuccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.xml.namespace.QName;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class VcloudVolumeManager extends AbstractVolumeManager {
    private static final String VCLOUD_DISKS_ARE_BOUND_TO_VM_MSG = "In Vcloud Director each disk is bound to the VM. Disks will be deleted on VM termination.";
    private static final Logger LOG = LoggerFactory.getLogger(VcloudVolumeManager.class);
    public static final long EDIT_VM_TIMEOUT_MS = 600000L;
    public static final String OS_DEVICE_PREFIX = "/dev/sd";

    @Override
    protected String getVolumeDeviceName(char deviceSuffix) {
        return null;
    }

    @Override
    protected String getOSDeviceName(char deviceSuffix) {
        return OS_DEVICE_PREFIX + deviceSuffix;
    }

    @Override
    public BlockDevice createBlockDevice(JcloudsLocation jcloudsLocation, BlockDeviceOptions options) {
        throw new IllegalStateException("This method shouldn't be called for Vcloud Director.");
    }

    @Override
    public MountedBlockDevice createAttachAndMountVolume(JcloudsMachineLocation machine, BlockDeviceOptions deviceOptions,
                                                         FilesystemOptions filesystemOptions) {
        BlockDevice device = createBlockDevice(machine, deviceOptions);
        AttachedBlockDevice attached = attachBlockDevice(machine, device, deviceOptions);
        createFilesystem(attached, filesystemOptions);
        return mountFilesystem(attached, filesystemOptions);
    }

    public BlockDevice createBlockDevice(JcloudsMachineLocation jcloudsMachineLocation, BlockDeviceOptions options) {
        Optional<NodeMetadata> vcloudNodeMetadata = jcloudsMachineLocation.getOptionalNode();
        VCloudDirectorApi vCloudDirectorApi = jcloudsMachineLocation.getParent().getComputeService().getContext().unwrapApi(VCloudDirectorApi.class);
        VmApi vmApi = vCloudDirectorApi.getVmApi();
        TaskApi taskApi = vCloudDirectorApi.getTaskApi();
        Vm vm = Vm.builder().id(vcloudNodeMetadata.get().getId()).build();
        RasdItemsList virtualHardwareSectionDisks = vmApi.getVirtualHardwareSectionDisks(vm.getId());

        if (!Iterables.tryFind(virtualHardwareSectionDisks, NewScsiLogicSASDisk.SCSI_LSILOGICSAS_PREDICATE).isPresent()) {
            virtualHardwareSectionDisks = new AddScsiLogicSASBus().addScsiLogicSASBus(virtualHardwareSectionDisks);
        }

        RasdItem nextDisk = new NewScsiLogicSASDisk().apply(virtualHardwareSectionDisks);

        CimString newDiskHostResource = new CimString(Iterables.getOnlyElement(nextDisk.getHostResources()));
        Preconditions.checkNotNull(newDiskHostResource, "HostResource for the existing disk should not be null");
        newDiskHostResource.getOtherAttributes().put(new QName("http://www.vmware.com/vcloud/v1.5", "capacity"), "" + (options.getSizeInGb() * 1024));
        RasdItem newDiskToBeCreated = RasdItem.builder()
                .fromRasdItem(nextDisk) // The same AddressOnParent (SCSI Controller)
                .hostResources(ImmutableList.of(newDiskHostResource)) // NB! Use hostResources to override hostResources from newDisk
                .build();
        virtualHardwareSectionDisks.add(newDiskToBeCreated);
        Task task = vmApi.editVirtualHardwareSectionDisks(vm.getId(), virtualHardwareSectionDisks);
        Predicates2.retry(
                new TaskSuccess(taskApi),
                Predicates2.DEFAULT_PERIOD * 5L,
                Predicates2.DEFAULT_MAX_PERIOD * 5L,
                EDIT_VM_TIMEOUT_MS).apply(task);
        String osDeviceName = getOSDeviceName(options.getDeviceSuffix());
        VcloudBlockDevice vcloudBlockDevice = new VcloudBlockDevice(newDiskToBeCreated, jcloudsMachineLocation, vm, osDeviceName);

        // Extra check for which seems to be necessary.
        waitForVolumeToBeAvailable(vcloudBlockDevice);
        return vcloudBlockDevice;
    }

    // In Vcloud Director, Hard Disk is bound to the VM
    @Override
    public AttachedBlockDevice attachBlockDevice(JcloudsMachineLocation machine, BlockDevice blockDevice, BlockDeviceOptions options) {
        return (VcloudBlockDevice)blockDevice;
    }

    // In Vcloud Director, Hard Disk is bound to the VM
    @Override
    public BlockDevice detachBlockDevice(AttachedBlockDevice attachedBlockDevice) {
        LOG.info("Detach block device called. It will be still visible to the VM. " + VCLOUD_DISKS_ARE_BOUND_TO_VM_MSG);
        return attachedBlockDevice;
    }

    @Override
    public void deleteBlockDevice(BlockDevice blockDevice) {
        LOG.info("delete Block device queried. " + VCLOUD_DISKS_ARE_BOUND_TO_VM_MSG);
    }

    protected void waitForVolumeToBeAvailable(final VcloudBlockDevice device) {
        boolean available = Repeater.create("waiting for volume available:" + device)
                .every(1, TimeUnit.SECONDS)
                .limitTimeTo(120, TimeUnit.SECONDS)
                .until(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        Optional<RasdItem> volume = describeVolume(device);
                        return volume.isPresent();
                    }})
                .run();

        if (!available) {
            LOG.error("Volume {} still not available. Last known was: {}; continuing", device, null);
        }
    }

    public static Optional<RasdItem> describeVolume(final VcloudBlockDevice device) {
        final VCloudDirectorApi vmApi = device.getMachine().getParent().getComputeService().getContext().unwrapApi(VCloudDirectorApi.class);
        RasdItemsList disks = vmApi.getVmApi().getVirtualHardwareSectionDisks(device.getVm().getId());
        Optional<RasdItem> rasdItemOptional = Iterables.tryFind(disks, new Predicate<RasdItem>() {
            @Override public boolean apply(@Nullable RasdItem input) {
                return RasdItem.ResourceType.DISK_DRIVE.equals(input.getResourceType()) && device.getId().equals(input.getInstanceID());
            }
        });
        return rasdItemOptional;
    }
}
