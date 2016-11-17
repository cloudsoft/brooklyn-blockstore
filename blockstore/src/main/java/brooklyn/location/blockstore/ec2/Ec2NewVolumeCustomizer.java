package brooklyn.location.blockstore.ec2;

import brooklyn.location.blockstore.NewVolumeCustomizer;
import brooklyn.location.blockstore.api.VolumeManager;

import java.util.Map;

/**
 * Creates a location customizer that:
 * <ul>
 * <li>configures the EC2 availability zone</li>
 * <li>attaches the specified (existing) volume to the newly-provisioned EC2 instance</li>
 * <li>mounts the filesystem under the requested path</li>
 * </ul>
 *
 * Can be used for attaching additional disk on provisioning time for AWS.
 * Below is shown an example:
 *
 * <pre>
 *   provisioning.properties:
 *     customizers:
 *     - $brooklyn:object:
 *         type: io.brooklyn.blockstore.brooklyn-blockstore:brooklyn.location.blockstore.ec2.Ec2NewVolumeCustomizer
 *         brooklyn.config:
 *           volumes:
 *           - blockDevice:
 *               sizeInGb: 3
 *               deviceSuffix: 'h'
 *               deleteOnTermination: true
 *               tags:
 *                 brooklyn: br-example-test-1
 *            filesystem:
 *              mountPoint: /mount/brooklyn/h
 *              filesystemType: ext3
 * </pre>
 */
public class Ec2NewVolumeCustomizer extends NewVolumeCustomizer {

    public Ec2NewVolumeCustomizer() {
        // for reflective creation (e.g. with $brooklyn:object)
    }

    public Ec2NewVolumeCustomizer(Map<?, ?> volume) {
        super(volume);
    }

    @Override
    protected VolumeManager getVolumeManager() {
        return new Ec2VolumeManager();
    }
}
