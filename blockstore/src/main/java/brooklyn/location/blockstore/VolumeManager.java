package brooklyn.location.blockstore;

import java.util.Map;

import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.location.jclouds.JcloudsSshMachineLocation;

/**
 * Customization hooks to ensure that any volume instances provisioned via a corresponding jclouds location become associated
 * with an EBS volume (either an existing volume, specified by ID, or newly created).
 */
public interface VolumeManager {

    /**
     * Creates a new volume in the same availability zone as the given machine, and attaches + mounts it.
     * 
     * @param machine The VM where the volume should be attached+mounted
     * @param volumeDeviceName The name of the attached device (e.g. "/dev/sdh")
     * @param osDeviceName The name of the device (e.g. "/dev/xvdh")
     * @param mountPoint Fully qualified path of where the volume should be mounted
     * @param filesystemType File system type (see `mkfs -t`; e.g. "ext3") 
     * @param sizeInGib Size of the volume to be created
     * @param tags Meta-data to be associated with the created volume
     * @return The volume id
     */
    public String createAttachAndMountVolume(JcloudsSshMachineLocation machine,
            final String volumeDeviceName, final String osDeviceName, final String mountPoint, final String filesystemType,
            final int sizeInGib, Map<String,String> tags);

    /**
     * Creates a new volume in the given availability zone.
     * 
     * @param location Location where the volume should be created
     * @param availabilityZone The zone (within the location) where the volume should be created 
     * @param sizeInGib Size of the volume to be created
     * @param tags Meta-data to be associated with the created volume
     * @return The volume id
     */
    public String createVolume(JcloudsLocation location, String availabilityZone, int sizeInGib, Map<String,String> tags);

    /**
     * Attaches the given volume to the given VM.
     * 
     * @param machine The VM where the volume should be attached
     * @param volumeId The volume id
     * @param volumeDeviceName The name of the attached device (e.g. "/dev/sdh")
     */
    public void attachVolume(JcloudsSshMachineLocation machine, String volumeId, String volumeDeviceName);

    public void attachAndMountVolume(JcloudsSshMachineLocation machine, String volumeId, String volumeDeviceName, String osDeviceName, String mountPoint);

    /**
     * Attaches the given volume to the given VM, and mounts it.
     * 
     * @param machine The VM where the volume should be attached
     * @param volumeId The volume id
     * @param volumeDeviceName The name of the attached device (e.g. "/dev/sdh")
     * @param osDeviceName The name of the device (e.g. "/dev/xvdh")
     * @param mountPoint Fully qualified path of where the volume should be mounted
     * @param filesystemType File system type (see `mkfs -t`; e.g. "ext3") 
     */
    public void attachAndMountVolume(JcloudsSshMachineLocation machine, String volumeId, String volumeDeviceName, String osDeviceName, String mountPoint, String filesystemType);

    /**
     * Detaches the given volume from the given VM. The filesystem should first be cleanly unmounted.
     * 
     * @param machine The VM where the volume should be detached from
     * @param volumeId The volume id
     * @param volumeDeviceName The name of the attached device (e.g. "/dev/sdh")
     */
    public void detachVolume(JcloudsSshMachineLocation machine, final String volumeId, String volumeDeviceName);

    /**
     * Deletes the given volume. The volume should first be detached from any VMs.
     * 
     * @param location Location where the volume exists
     * @param volumeId The volume id
     */
    public void deleteVolume(JcloudsLocation location, String volumeId);

    /**
     * Creates a filesystem for an attached volume.
     * 
     * @param machine The VM where the volume is attached
     * @param osDeviceName The name of the device (e.g. "/dev/xvdh")
     * @param filesystemType File system type (see `mkfs -t`; e.g. "ext3") 
     */
    public void createFilesystem(JcloudsSshMachineLocation machine, String osDeviceName, String filesystemType);

    /**
     * @see {link {@link #mountFilesystem(JcloudsSshMachineLocation, String, String, String)}, but where filesystemType is "auto"
     */
    public void mountFilesystem(JcloudsSshMachineLocation machine, String osDeviceName, String mountPoint);
    
    /**
     * Mounts the given device.
     * 
     * @param machine The VM where the volume is attached
     * @param osDeviceName The name of the device (e.g. "/dev/xvdh")
     * @param mountPoint Fully qualified path of where the volume should be mounted
     * @param filesystemType File system type (see `mkfs -t`; e.g. "ext3") 
     */
    public void mountFilesystem(JcloudsSshMachineLocation machine, String osDeviceName, String mountPoint, String filesystemType);

    /**
     * Unmounts the given device.
     * 
     * @param machine The VM where the volume is attached
     * @param osDeviceName The name of the device (e.g. "/dev/xvdh")
     */
    public void unmountFilesystem(JcloudsSshMachineLocation machine, String osDeviceName);
}
