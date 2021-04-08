package com.kezong.fataar

class RunTimeUtils {

    HashMap<String, Long> durationMap = new HashMap<>()
    final String suffix = "-TOTAL"

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
                Long time = durationMap.get(tag + suffix)
                if (time == null) time = 0L
                long totalTime = time + runTime
                durationMap.put(tag + suffix, totalTime)
                FatUtils.logAnytime("-----> ${tag + suffix} executes for millis")
            }
            FatUtils.logAnytime("-----> ${tag} executes for ${runTime} millis")
        } else {
            throw new RuntimeException("miss call method start(${tag})")
        }
    }

}