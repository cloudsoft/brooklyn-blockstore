package brooklyn.location.blockstore.effectors;

import brooklyn.location.blockstore.BlockDeviceOptions;
import brooklyn.location.blockstore.FilesystemOptions;
import brooklyn.location.blockstore.api.MountedBlockDevice;
import brooklyn.location.blockstore.ec2.Ec2VolumeCustomizers;
import brooklyn.location.blockstore.openstack.OpenstackNewVolumeCustomizer;
import com.google.common.base.Preconditions;
import com.google.common.reflect.TypeToken;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.effector.AddEffector;
import org.apache.brooklyn.core.effector.EffectorBody;
import org.apache.brooklyn.core.effector.EffectorTasks;
import org.apache.brooklyn.core.effector.Effectors;
import org.apache.brooklyn.location.jclouds.JcloudsLocationCustomizer;
import org.apache.brooklyn.location.jclouds.JcloudsMachineLocation;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.core.flags.TypeCoercions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Effector for attaching disks during runtime.
 * To attach the effector you should apply the following initializer with different type reference than the one for the non-karaf version - notice that Bundle-SymbolicName is added as a prefix:
 * <pre>
 *    brooklyn.initializers:
 *     - type: io.cloudsoft.amp.vm-customization:io.cloudsoft.amp.vmcustomization.ExtraHddBodyEffector
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
 *          "brooklyn": "br-example-val-test-1"
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

    static ConfigKey<Map<?,?>> LOCATION_CUSTOMIZER_FIELDS = ConfigKeys.newConfigKey(new TypeToken<Map<?,?>>() {}, "location.customizer.fields",
            "Map of location customizer fields.", MutableMap.of());

    public static final String EXTRA_HDD_EFFECTOR_NAME = "addExtraHdd";
    public static final String AWS_CLOUD = "aws-ec2";
    public static final String OPENSTACK_NOVA = "openstack-nova";
    public static final String VCLOUD_DIRECTOR = "vcloud-director";

    public ExtraHddBodyEffector() {
        super(newEffectorBuilder().build());
    }

    public static Effectors.EffectorBuilder newEffectorBuilder() {
        ConfigBag bag = ConfigBag.newInstance();
        bag.put(EFFECTOR_NAME, EXTRA_HDD_EFFECTOR_NAME);

        Effectors.EffectorBuilder eff = AddEffector.newEffectorBuilder(MountedBlockDevice.class, bag)
                .parameter(LOCATION_CUSTOMIZER_FIELDS)
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
            Preconditions.checkNotNull(parameters.get(LOCATION_CUSTOMIZER_FIELDS), LOCATION_CUSTOMIZER_FIELDS.getName() + " is required");
            Map<?, ?> locationCustomizerFieldsMap = TypeCoercions.coerce(parameters.get(LOCATION_CUSTOMIZER_FIELDS), Map.class);

            JcloudsMachineLocation machine = EffectorTasks.getMachine(entity(), JcloudsMachineLocation.class);
            String provider = machine.getParent().getProvider();

            LOG.info("Invoking effector addExtraHdd for cloud "+ provider + " on entity " + entity());

            Map<BlockDeviceOptions, FilesystemOptions> locationCustomizerFields = transformMapToLocationCustomizerFields(locationCustomizerFieldsMap);
            LOG.info("Invoking effector " + EXTRA_HDD_EFFECTOR_NAME + " with location customizer fields " + locationCustomizerFields);

            JcloudsLocationCustomizer customizer = getCustomizerForCloud(provider, locationCustomizerFields);
            customizer.customize(machine.getParent(), machine.getParent().getComputeService(), machine);

            return getMountedBlockDevice(customizer);
        }

        public static Map<BlockDeviceOptions, FilesystemOptions> transformMapToLocationCustomizerFields(Map<?, ?> map) {
            if (map.containsKey("blockDevice") && map.containsKey("filesystem")) {
                BlockDeviceOptions blockDeviceOptions =  BlockDeviceOptions.fromMap((Map<String, ?>) map.get("blockDevice"));
                if (blockDeviceOptions.getSizeInGb() == 0) {
                    throw new IllegalArgumentException("Invoked addExtraHdd effector with not appropriate parameters "
                            + map + "; \"blockDevice\" should contain value for \"sizeInGb\"");
                }
                FilesystemOptions filesystemOptions = FilesystemOptions.fromMap((Map<String, ?>) map.get("filesystem"));
                Map<BlockDeviceOptions, FilesystemOptions> locationCustomizerFields = MutableMap.of(
                        blockDeviceOptions,
                        filesystemOptions
                );

                return locationCustomizerFields;
            } else {
                throw new IllegalArgumentException("Invoked addExtraHdd effector with not appropriate parameters. " +
                        "Expected parameter of type { \"blockDevice\": {}, \"filesystem\": {} }, but found " + map);
            }
        }

        private JcloudsLocationCustomizer getCustomizerForCloud(String provider, Map<BlockDeviceOptions, FilesystemOptions> locationCustomizerFields) {
            JcloudsLocationCustomizer customizer;

            switch (provider) {
                case AWS_CLOUD:
                    customizer = new Ec2VolumeCustomizers.NewVolumeCustomizer(locationCustomizerFields);
                    return customizer;

                case OPENSTACK_NOVA:
                    customizer = new OpenstackNewVolumeCustomizer(locationCustomizerFields);
                    return customizer;

                default:
                    throw new UnsupportedOperationException("Tried to invoke addExtraHdd effector on entity " +  entity() + " for cloud "
                            + provider + " which does not support adding disks from an effector.");

            }
        }

        private MountedBlockDevice getMountedBlockDevice(JcloudsLocationCustomizer customizer) {
            if (customizer instanceof Ec2VolumeCustomizers.NewVolumeCustomizer) {
                return ((Ec2VolumeCustomizers.NewVolumeCustomizer) customizer).getMountedBlockDevice();
            } else if (customizer instanceof OpenstackNewVolumeCustomizer) {
                return ((OpenstackNewVolumeCustomizer) customizer).getMountedBlockDevice();
            } else {
                throw new UnsupportedOperationException("Tried to call method getMountedBlockDevice() from custozier "
                        +  customizer + " which doesn't have such method");
            }
        }
    }
}
