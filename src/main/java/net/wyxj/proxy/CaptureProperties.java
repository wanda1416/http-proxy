package net.wyxj.proxy;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for how captured request/response transactions are written to disk.
 * Bound from {@code capture.*}, e.g. {@code --capture.dir=./capture --capture.max-body=1048576}.
 */
@ConfigurationProperties(prefix = "capture")
public class CaptureProperties {

    /** Directory that each transaction file is written into. */
    private String dir = "./capture";

    /**
     * Maximum body size (in bytes) that is inlined into the transaction text file.
     * Larger or non-text bodies are written to a side-car {@code .bin} file instead.
     */
    private long maxBody = 1024 * 1024;

    public String getDir() {
        return dir;
    }

    public void setDir(String dir) {
        this.dir = dir;
    }

    public long getMaxBody() {
        return maxBody;
    }

    public void setMaxBody(long maxBody) {
        this.maxBody = maxBody;
    }
}
