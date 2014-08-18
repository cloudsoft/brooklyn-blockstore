package brooklyn.location.blockstore;

import java.util.Map;

import com.google.common.base.Objects;
import com.google.common.collect.Maps;

public class BlockDeviceOptions {

    private String name;
    private String zone;
    private Map<String, String> tags = Maps.newHashMap();
    private int sizeInGb;
    private char deviceSuffix = 'h';
    private boolean deleteOnTermination;

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
        this.tags = tags;
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

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("zone", zone)
                .add("sizeInGb", sizeInGb)
                .add("deviceSuffix", deviceSuffix)
                .add("deleteOnTermination", deleteOnTermination)
                .add("tags", tags)
                .toString();
    }

}
