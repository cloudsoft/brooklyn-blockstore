package brooklyn.location.blockstore.effectors;

import brooklyn.location.blockstore.NewVolumeCustomizer;
import brooklyn.location.blockstore.api.MountedBlockDevice;
import brooklyn.location.blockstore.api.VolumeOptions;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeToken;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.effector.AddEffector;
import org.apache.brooklyn.core.effector.EffectorBody;
import org.apache.brooklyn.core.effector.EffectorTasks;
import org.apache.brooklyn.core.effector.Effectors;
import org.apache.brooklyn.location.jclouds.JcloudsMachineLocation;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Effector for attaching disks during runtime.
 * To attach the effector you should apply the following initializer with different type reference than the one for the non-karaf version - notice that Bundle-SymbolicName is added as a prefix:
 * <pre>
 *    brooklyn.initializers:
 *     - type: brooklyn.location.blockstore.effectors.ExtraHddBodyEffector
 * </pre>
 *
 * The expected effector argument value is json map applicable to Ec2NewVolumeCustomizer's fileds.<br>
 * For example:
 * <pre>
 *    {
 *      "blockDevice": {
 *        "sizeInGb": 3,
 *        "deviceSuffix": 'h',
 *        "deleteOnTermination": false,
 *        "tags": {
 *          "brooklyn": "br-example-test-1"
 *        }
 *      },
 *      "filesystem": {
 *        "mountPoint": "/mount/brooklyn/h",
 *        "filesystemType": "ext3"
 *      }
 *    }
 * </pre>
 *
 * Important notice is that KVM is configured as the default hypervisor for OpenStack which means that the defined device name will be of type /dev/vd*.
 * This means that the device suffix must be set as the next letter in alphabetical order from the existing device names on the VM.
 * In other words, "deviceSuffix" have to be set to 'b', 'c' and etc. depending on the already available device names.
 *
 */
public class ExtraHddBodyEffector extends AddEffector {

    private static final Logger LOG = LoggerFactory.getLogger(ExtraHddBodyEffector.class);

    static ConfigKey<VolumeOptions> VOLUME = ConfigKeys.newConfigKey(
            new TypeToken<VolumeOptions>() {}, "volume",
            "Map of location customizer fields.");

    public static final String EXTRA_HDD_EFFECTOR_NAME = "addExtraHdd";

    public ExtraHddBodyEffector() {
        super(newEffectorBuilder().build());
    }

    public static Effectors.EffectorBuilder newEffectorBuilder() {
        ConfigBag bag = ConfigBag.newInstance();
        bag.put(EFFECTOR_NAME, EXTRA_HDD_EFFECTOR_NAME);

        Effectors.EffectorBuilder eff = AddEffector.newEffectorBuilder(MountedBlockDevice.class, bag)
                .parameter(VOLUME)
                .description("An effector to add extra hdd to provisioned vm")
                .impl(new Body());

        return eff;
    }

    @Override
    public void apply(EntityLocal entity) {
        super.apply(entity);
    }

    public static class Body extends EffectorBody {

        @Override
        public MountedBlockDevice call(ConfigBag parameters) {
            Preconditions.checkNotNull(parameters.get(VOLUME), VOLUME.getName() + " is required");
            VolumeOptions volumeOptions = parameters.get(VOLUME);

            JcloudsMachineLocation machine = EffectorTasks.getMachine(entity(), JcloudsMachineLocation.class);

            LOG.info("Invoking effector " + EXTRA_HDD_EFFECTOR_NAME + " with location customizer fields " + volumeOptions);

            NewVolumeCustomizer customizer = getCustomizerForCloud(ImmutableList.of(volumeOptions));
            return customizer.createAndAttachDisk(machine, volumeOptions);
        }

        private NewVolumeCustomizer getCustomizerForCloud(List<VolumeOptions> locationCustomizerFields) {
            return new NewVolumeCustomizer(locationCustomizerFields);
        }
    }
}
