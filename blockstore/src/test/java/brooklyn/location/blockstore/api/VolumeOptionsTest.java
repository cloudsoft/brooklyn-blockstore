package brooklyn.location.blockstore.api;

import static org.testng.Assert.assertEquals;

import org.apache.brooklyn.test.Asserts;
import org.testng.annotations.Test;

import com.google.common.base.Joiner;

public class VolumeOptionsTest {

    @Test
    public void testFromJsonString() throws Exception {
        String json = Joiner.on("\n").join(
                "{",
                "  \"blockDevice\": {",
                "    \"deviceSuffix\": \"h\",",
                "    \"sizeInGb\": 4,",
                "    \"deleteOnTermination\": true,",
                "    \"tags\": {",
                "      \"brooklyn\": \"br-test-1\"",
                "    }",
                "  },",
                "  \"filesystem\": {",
                "    \"mountPoint\": \"/mount/brooklyn/h\",",
                "    \"filesystemType\": \"ext3\"",
                "  }",
                "}");

        VolumeOptions transformed = VolumeOptions.fromString(json);

        assertEquals(transformed.getBlockDeviceOptions().getSizeInGb(), 4);
        assertEquals(transformed.getBlockDeviceOptions().getDeviceSuffix(), 'h');
        assertEquals(transformed.getBlockDeviceOptions().deleteOnTermination(), true);
        assertEquals(transformed.getBlockDeviceOptions().getTags().get("brooklyn"), "br-test-1");

        assertEquals(transformed.getFilesystemOptions().getFilesystemType(), "ext3");
        assertEquals(transformed.getFilesystemOptions().getMountPoint(), "/mount/brooklyn/h");

        // Expect 4.0 to be treated like 4
        String parameterInputWithDoubleSizeInGB = Joiner.on("\n").join(
                "{",
                "  \"blockDevice\": {",
                "    \"deviceSuffix\": \"h\",",
                "    \"sizeInGb\": 4.0,",
                "    \"deleteOnTermination\": true,",
                "    \"tags\": {",
                "      \"brooklyn\": \"br-test-1\"",
                "    }",
                "  },",
                "  \"filesystem\": {",
                "    \"mountPoint\": \"/mount/brooklyn/h\",",
                "    \"filesystemType\": \"ext3\"",
                "  }",
                "}");

        transformed = VolumeOptions.fromString(parameterInputWithDoubleSizeInGB);

        assertEquals(transformed.getBlockDeviceOptions().getSizeInGb(), 4);
    }

    @Test
    public void testFromStringFailsWhenMissingSizeInGb() {
        String parameterInput = Joiner.on("\n").join(
                "{",
                "  \"blockDevice\": {",
                "    \"deviceSuffix\": \"h\",",
                "    \"deleteOnTermination\": true,",
                "    \"tags\": {",
                "      \"brooklyn\": \"br-test-1\"",
                "    }",
                "  },",
                "  \"filesystem\": {",
                "    \"mountPoint\": \"/mount/brooklyn/h\",",
                "    \"filesystemType\": \"ext3\"",
                "  }",
                "}");

        try {
            VolumeOptions.fromString(parameterInput);
            Asserts.shouldHaveFailedPreviously("\"blockDevice\" should contain value for \"sizeInGb\"");
        } catch (Exception e) {
            Asserts.expectedFailureOfType(e, IllegalArgumentException.class);
            Asserts.expectedFailureContains(e, "\"blockDevice\" should contain value for \"sizeInGb\"");
        }
    }
    
    @Test
    public void testFailsWhenNonIntegerSizeInGb() {
        String parameterInput = Joiner.on("\n").join(
                "{",
                "  \"blockDevice\": {",
                "    \"sizeInGb\": 1.7,",
                "    \"deviceSuffix\": \"h\",",
                "    \"deleteOnTermination\": true,",
                "    \"tags\": {",
                "      \"brooklyn\": \"br-test-1\"",
                "    }",
                "  },",
                "  \"filesystem\": {",
                "    \"mountPoint\": \"/mount/brooklyn/h\",",
                "    \"filesystemType\": \"ext3\"",
                "  }",
                "}");

        try {
            VolumeOptions.fromString(parameterInput);
            Asserts.shouldHaveFailedPreviously("Trying to set block device with not allowed sizeInGb value");
        } catch (Exception e) {
            Asserts.expectedFailureOfType(e, UnsupportedOperationException.class);
            Asserts.expectedFailureContains(e, "sizeInGb must have integer value.");
        }
    }
    
    @Test
    public void testFailsWhenInvalidSizeInGb() {
        String parameterInput = Joiner.on("\n").join(
                "{",
                "  \"blockDevice\": {",
                "    \"deviceSuffix\": \"h\",",
                "    \"sizeInGb\": \"dummy-value\",",
                "    \"deleteOnTermination\": true,",
                "    \"tags\": {",
                "    \t\"brooklyn\": \"br-test-1\"",
                "    }",
                "  },",
                "  \"filesystem\": {",
                "    \"mountPoint\": \"/mount/brooklyn/h\",",
                "    \"filesystemType\": \"ext3\"",
                "  }",
                "}");

        try {
            VolumeOptions.fromString(parameterInput);
            Asserts.shouldHaveFailedPreviously();
        } catch (Exception e) {
            Asserts.expectedFailureOfType(e, ClassCastException.class);
        }

    }
}
