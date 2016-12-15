package brooklyn.location.blockstore.openstack;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.util.text.Identifiers;

import java.util.Map;

public class OpenStackLocationConfig {


    public static final String PROVIDER = "openstack-nova";
    public static final String LOCATION_SPEC = PROVIDER;
    public static final String NAMED_LOCATION = "OpenStackVolumeManagerLiveTest" + Identifiers.makeRandomId(4);
    public static final String IMAGE_NAME_REGEX = "CentOS 7";

    public static final String BROOKLYN_PROPERTIES_JCLOUDS_PREFIX = "brooklyn.location.jclouds.";

    private Map<?,?> configMap;

    public OpenStackLocationConfig() {
        setConfigMap();
    }

    public static void addBrooklynProperties(BrooklynProperties properties) {
        Object endpoint = properties.get(BROOKLYN_PROPERTIES_JCLOUDS_PREFIX+"openstack-nova.endpoint");
        Object identity = properties.get(BROOKLYN_PROPERTIES_JCLOUDS_PREFIX+"openstack-cinder.identity");
        Object credential = properties.get(BROOKLYN_PROPERTIES_JCLOUDS_PREFIX+"openstack-cinder.credential");
        Object autoGenerateKeypairs = properties.get(BROOKLYN_PROPERTIES_JCLOUDS_PREFIX+"openstack-nova.auto-generate-keypairs");
        Object keyPair = properties.get(BROOKLYN_PROPERTIES_JCLOUDS_PREFIX+"openstack-nova.keyPair");
        Object privateKeyFile = properties.get(BROOKLYN_PROPERTIES_JCLOUDS_PREFIX+"openstack-nova.loginUser.privateKeyFile");
        Object keystoneCredentialType = properties.get(BROOKLYN_PROPERTIES_JCLOUDS_PREFIX+"openstack-nova.jclouds.keystone.credential-type");

        properties.put("brooklyn.location.named."+NAMED_LOCATION, PROVIDER+":"+endpoint);
        properties.put("brooklyn.location.named."+NAMED_LOCATION+".identity", identity);
        properties.put("brooklyn.location.named."+NAMED_LOCATION+".credential", credential);
        properties.put("brooklyn.location.named."+NAMED_LOCATION+".region", "RegionOne");
        properties.put("brooklyn.location.named."+NAMED_LOCATION+".jclouds.openstack-nova.auto-generate-keypairs", autoGenerateKeypairs);
        properties.put("brooklyn.location.named."+NAMED_LOCATION+".keyPair", keyPair);
        properties.put("brooklyn.location.named."+NAMED_LOCATION+".loginUser.privateKeyFile", privateKeyFile);
        properties.put("brooklyn.location.named."+NAMED_LOCATION+".credential-type", keystoneCredentialType);
    }

    private void setConfigMap() {
        configMap = ImmutableMap.builder()
                .put(JcloudsLocation.IMAGE_NAME_REGEX, IMAGE_NAME_REGEX)
                .put("generate.hostname", true)
                .put("loginUser", "centos")
                .put("user", "amp")
                .put("securityGroups", "VPN_local")
                .put("auto-generate-keypairs", true)
                .put("privateKeyFile", "~/.ssh/openstack.pem")
                .put("templateOptions", ImmutableMap.of(
                        "networks", ImmutableList.of("426bb8f6-c8c7-4f84-ad3c-19f66b28a288")
                ))
                .put("cloudMachineNamer", "org.apache.brooklyn.core.location.cloud.names.CustomMachineNamer")
                .put("minRam", "2000")
                .put("custom.machine.namer.machine", "QA-xxxx")
                .build();
    }

    public Map<?,?> getConfigMap() {
        return configMap;
    }
}
