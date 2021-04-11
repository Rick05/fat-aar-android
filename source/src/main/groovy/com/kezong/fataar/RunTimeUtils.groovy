package com.kezong.fataar

class RunTimeUtils {
    private final String SUFFIX = "-TOTAL"

    private HashMap<String, Long> durationMap = new HashMap<>()

    private RunTimeUtils() {
    }

    private static RunTimeUtils instance = new RunTimeUtils()

    public static RunTimeUtils getInstance() {
        return instance
    }

    public void start(String tag) {
        durationMap.put(tag, System.currentTimeMillis())
    }

    public void end(String tag) {
        this.end(tag, false)
    }

    public void end(String tag, boolean isCalTotal) {
        Long startTime = durationMap.get(tag)
        if (startTime != null) {
            long runTime = System.currentTimeMillis() - startTime
            if (isCalTotal) {
                Long time = durationMap.get(tag + SUFFIX)
                if (time == null) {
                    time = 0L
                }
                time += runTime
                durationMap.put(tag + SUFFIX, time)
                FatUtils.logAnytime("-----> ${tag + SUFFIX} executes for $time millis")
            }
            FatUtils.logAnytime("-----> $tag executes for $runTime millis")
        } else {
            throw new RuntimeException("miss call method start($tag)")
        }
    }

}