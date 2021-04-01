package com.kezong.fataar

class RunTimeUtils {

    HashMap<String, Long> durationMap = new HashMap<>()

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
        Long startTime = durationMap.get(tag)
        if (startTime != null) {
            FatUtils.logAnytime("-----> ${tag} executes for ${System.currentTimeMillis() - startTime} millis")
        } else {
            throw new RuntimeException("miss call method start(${tag})")
        }
    }

}