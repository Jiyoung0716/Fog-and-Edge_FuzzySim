package org.fog.utils;

import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.FogDevice;

public class NetworkUsageMonitor {

    private static double networkUsage = 0.0;

    public static void sendingTuple(int deviceId, double latency, double tupleNwSize) {
    	networkUsage += tupleNwSize;
    }

    public static void sendingModule(int deviceId, double latency, long moduleSize) {
    	networkUsage += moduleSize;
    }

    public static double getNetworkUsage() {
        return networkUsage;
    }
    public static void reset() {
        networkUsage = 0.0;
    }
}
