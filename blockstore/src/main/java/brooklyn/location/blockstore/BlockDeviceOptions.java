package brooklyn.location.blockstore;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;

import org.apache.brooklyn.util.core.flags.TypeCoercions;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Maps;
import org.apache.brooklyn.util.guava.Maybe;

public class BlockDeviceOptions {

    private String name;
    private String zone;
    private Map<String, String> tags = Maps.newHashMap();
    private int sizeInGb;
    private char deviceSuffix = 'h';
    private boolean deleteOnTermination;
    private Maybe<String> volumeType = Maybe.absent();

    // For more convenient yaml input
    public static BlockDeviceOptions fromMap(Map<String, ?> map) {
        BlockDeviceOptions result = new BlockDeviceOptions();
        result.name = (String) map.get("name");
        result.zone = (String) map.get("zone");
        if (map.containsKey("tags")) {
            for (Map.Entry<?, ?> entry : ((Map<?,?>)map.get("tags")).entrySet()) {
                result.tags.put((String)entry.getKey(), (String)entry.getValue());
            }
        }
        if (map.containsKey("sizeInGb")) {
            checkNotNull(map.get("sizeInGb"), "sizeInGb should not be null");
            if (map.get("sizeInGb") instanceof Double && Math.abs((Double) map.get("sizeInGb") - ((Double) map.get("sizeInGb")).intValue()) >= 0.01
                    || map.get("sizeInGb") instanceof Float && Math.abs((Float) map.get("sizeInGb") - ((Float) map.get("sizeInGb")).intValue()) >= 0.01) {
                throw new UnsupportedOperationException("Trying to set block device with not allowed sizeInGb value "
                        + map.get("sizeInGb") + "; sizeInGb must have integer value.");
            } else if (map.get("sizeInGb") instanceof Double) {
                result.sizeInGb = ((Double) map.get("sizeInGb")).intValue();
            } else if (map.get("sizeInGb") instanceof Float) {
                result.sizeInGb = ((Float) map.get("sizeInGb")).intValue();
            } else if (map.get("sizeInGb") instanceof Integer){
                result.sizeInGb = (Integer)map.get("sizeInGb");
            } else {
                result.sizeInGb = TypeCoercions.coerce(map.get("sizeInGb"), Integer.class);
            }
            checkArgument(result.sizeInGb > 0, "sizeInGb should be grater than zero"); 
        } else {
            throw new IllegalArgumentException("Tried to create volume with not appropriate parameters "
                        + map + "; \"blockDevice\" should contain value for \"sizeInGb\"");
        }
        if (map.containsKey("deviceSuffix")) {
            Object val = checkNotNull(map.get("deviceSuffix"), "deviceSuffix");
            if (val instanceof Character) {
                result.deviceSuffix = (Character) val;
            } else if (val instanceof String && ((String)val).length() == 1) {
                result.deviceSuffix = ((String)val).charAt(0);
            } else {
                throw new IllegalArgumentException("Invalid deviceSuffix '"+val+"' in "+map);
            }
        }
        if (map.containsKey("deleteOnTermination")) {
            result.deleteOnTermination = (Boolean) checkNotNull(map.get("deleteOnTermination"), "deleteOnTermination");
        }
        if (map.containsKey("volumeType")) {
            result.volumeType = Maybe.of(checkNotNull(map.get("volumeType"), "volumeType").toString());
        }
        return result;
    }
    
    public static BlockDeviceOptions copy(BlockDeviceOptions other) {
    	return new BlockDeviceOptions()
    			.name(other.name)
    			.zone(other.zone)
    			.tags(other.tags)
    			.sizeInGb(other.sizeInGb)
    			.deviceSuffix(other.deviceSuffix)
                .deleteOnTermination(other.deleteOnTermination)
                .volumeType(other.volumeType);
    }
    
    public String getName() {
        return name;
    }

    public BlockDeviceOptions name(String name) {
        this.name = name;
        return this;
    }

    /**
     * @param zone Availability zone for the disk.
     * @deprecated This is not obtainable from YAML
     *             and will be overridden to use the same Availability zone as the machine location.
     */
    @Deprecated
    public BlockDeviceOptions zone(String zone) {
        this.zone = zone;
        return this;
    }

    public BlockDeviceOptions tags(Map<String, String> tags) {
        if (tags != null) this.tags.putAll(tags);
        return this;
    }

    public BlockDeviceOptions sizeInGb(int sizeInGb) {
        this.sizeInGb = sizeInGb;
        return this;
    }

    public BlockDeviceOptions deviceSuffix(char suffix) {
        this.deviceSuffix = suffix;
        return this;
    }

    public BlockDeviceOptions deleteOnTermination(boolean deleteOnTermination) {
        this.deleteOnTermination = deleteOnTermination;
        return this;
    }

    public BlockDeviceOptions volumeType(String volumeType) {
        this.volumeType = Maybe.of(checkNotNull(volumeType, "volumeType"));
        return this;
    }

    public BlockDeviceOptions volumeType(Maybe<String> volumeType) {
        this.volumeType = checkNotNull(volumeType, "volumeType");
        return this;
    }

    public String getZone() {
        return zone;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public boolean hasTags() {
        return getTags() != null && !getTags().isEmpty();
    }

    public int getSizeInGb() {
        return sizeInGb;
    }

    public char getDeviceSuffix() {
        return deviceSuffix;
    }

    public boolean deleteOnTermination() {
        return deleteOnTermination;
    }

    public Maybe<String> getVolumeType() {
        return volumeType;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", name)
                .add("zone", zone)
                .add("tags", tags)
                .add("sizeInGb", sizeInGb)
                .add("deviceSuffix", deviceSuffix)
                .add("deleteOnTermination", deleteOnTermination)
                .add("volumeType", volumeType)
                .toString();
    }
}
