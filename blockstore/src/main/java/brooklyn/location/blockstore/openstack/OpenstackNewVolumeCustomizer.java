package brooklyn.location.blockstore.openstack;

import brooklyn.location.blockstore.NewVolumeCustomizer;
import brooklyn.location.blockstore.api.VolumeManager;
import brooklyn.location.blockstore.api.VolumeOptions;

import java.util.List;
import java.util.Map;

/**
 * Customizer that can be used for attaching additional disk on provisioning time for OpenStack.<br><br>
 *
 * Below is shown an example:
 *
 * <pre>
 *   provisioning.properties:
 *     customizers:
 *     - $brooklyn:object:
 *         type: io.brooklyn.blockstore.brooklyn-blockstore:brooklyn.location.blockstore.openstack.OpenstackNewVolumeCustomizer
 *         brooklyn.config:
 *           volumes:
 *           - blockDevice:
 *               sizeInGb: 3
 *               deviceSuffix: 'b'
 *               deleteOnTermination: true
 *               tags:
 *                 brooklyn: br-example-test-1
 *            filesystem:
 *              mountPoint: /mount/brooklyn/b
 *              filesystemType: ext3
 * </pre>
 *
 * Important notice is that KVM is configured as the default hypervisor for OpenStack which means that the defined device name will be of type /dev/vd*.
 * This means that the device suffix must be set as the next letter in alphabetical order from the existing device names on the VM.
 */

public class OpenstackNewVolumeCustomizer extends NewVolumeCustomizer {

    public OpenstackNewVolumeCustomizer() {
        // for reflective creation (e.g. with $brooklyn:object)
    }

    public OpenstackNewVolumeCustomizer(List<VolumeOptions> volume) {
        super(volume);
    }

    protected VolumeManager getVolumeManager() {
        return new OpenstackVolumeManager();
    }
}
