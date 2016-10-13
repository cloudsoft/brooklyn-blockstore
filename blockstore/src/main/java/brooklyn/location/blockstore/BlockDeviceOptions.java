package brooklyn.location.blockstore;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;

import com.google.common.base.Objects;
import com.google.common.collect.Maps;

public class BlockDeviceOptions {

    private String name;
    private String zone;
    private Map<String, String> tags = Maps.newHashMap();
    private Number sizeInGb;
    private char deviceSuffix = 'h';
    private boolean deleteOnTermination;

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
            if (map.get("sizeInGb") instanceof Double && (Double) map.get("sizeInGb") % 1 != 0) {
                throw new UnsupportedOperationException("Trying to set block device with not allowed sizeInGb value "
                        + map.get("sizeInGb") + "; sizeInGb must have integer value.");
            }
            result.sizeInGb = (Number) checkNotNull(map.get("sizeInGb"), "sizeInGb");
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
        return result;
    }
    
    public static BlockDeviceOptions copy(BlockDeviceOptions other) {
    	return new BlockDeviceOptions()
    			.name(other.name)
    			.zone(other.zone)
    			.tags(other.tags)
    			.sizeInGb(other.sizeInGb)
    			.deviceSuffix(other.deviceSuffix)
    			.deleteOnTermination(other.deleteOnTermination);
    }
    
    public String getName() {
        return name;
    }

    public BlockDeviceOptions name(String name) {
        this.name = name;
        return this;
    }

    public BlockDeviceOptions zone(String zone) {
        this.zone = zone;
        return this;
    }

    public BlockDeviceOptions tags(Map<String, String> tags) {
        if (tags != null) this.tags.putAll(tags);
        return this;
    }

    public BlockDeviceOptions sizeInGb(Number sizeInGb) {
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

    public String getZone() {
        return zone;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public boolean hasTags() {
        return getTags() != null && !getTags().isEmpty();
    }

    public Number getSizeInGb() {
        return sizeInGb;
    }

    public char getDeviceSuffix() {
        return deviceSuffix;
    }

    public boolean deleteOnTermination() {
        return deleteOnTermination;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("name", name)
                .add("zone", zone)
                .add("tags", tags)
                .add("sizeInGb", sizeInGb)
                .add("deviceSuffix", deviceSuffix)
                .add("deleteOnTermination", deleteOnTermination)
                .toString();
    }
}
