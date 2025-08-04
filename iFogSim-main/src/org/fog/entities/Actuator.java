package org.fog.entities;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.utils.FogEvents;
import org.fog.utils.GeoLocation;
import org.fog.utils.Logger;
import org.fog.utils.TimeKeeper;

public class Actuator extends SimEntity{

	private int gatewayDeviceId;
	private double latency;
	private GeoLocation geoLocation;
	private String appId;
	private int userId;
	private String actuatorType;
	private Application app;
	
	public Actuator(String name, int userId, String appId, int gatewayDeviceId, double latency, GeoLocation geoLocation, String actuatorType, String srcModuleName) {
		super(name);
		this.setAppId(appId);
		this.gatewayDeviceId = gatewayDeviceId;
		this.geoLocation = geoLocation;
		setUserId(userId);
		setActuatorType(actuatorType);
		setLatency(latency);
	}
	
	public Actuator(String name, int userId, String appId, String actuatorType) {
		super(name);
		this.setAppId(appId);
		setUserId(userId);
		setActuatorType(actuatorType);
	}

	@Override
	public void startEntity() {
		sendNow(gatewayDeviceId, FogEvents.ACTUATOR_JOINED, getLatency());
	}

	@Override
	public void processEvent(SimEvent ev) {
		switch(ev.getTag()){
		case FogEvents.TUPLE_ARRIVAL:
			processTupleArrival(ev);
			break;
		}		
	}

	private void processTupleArrival(SimEvent ev) {
	    Tuple tuple = (Tuple) ev.getData();
	    Logger.debug(getName(), "Received tuple " + tuple.getCloudletId() + " on " + tuple.getDestModuleName());
	    
	    String srcModule = tuple.getSrcModuleName();
	    String destModule = tuple.getDestModuleName();
	    int tupleId = tuple.getActualTupleId();
	    Application app = getApp();

	    for (AppLoop loop : app.getLoops()) {
	        if (loop.hasEdge(srcModule, destModule) && loop.isEndModule(destModule)) {
	            Double startTime = TimeKeeper.getInstance().getEmitTimes().get(tupleId);
	            if (startTime == null) {
	                //System.out.println("⚠️ [DEBUG] DISPLAY 도착했지만 emitTime 없음 / tupleId=" + tupleId);
	                break;
	            }

	            double endTime = CloudSim.clock();
	            double delay = endTime - startTime;

	            // emitTimes에서 제거
	            TimeKeeper.getInstance().getEmitTimes().remove(tupleId);

	            // 기존 평균 및 횟수
	            int loopId = loop.getLoopId();
	            double currentAverage = TimeKeeper.getInstance().getLoopIdToCurrentAverage().getOrDefault(loopId, 0.0);
	            int currentCount = TimeKeeper.getInstance().getLoopIdToCurrentNum().getOrDefault(loopId, 0);

	            // 새로운 평균 계산
	            double newAverage = (currentAverage * currentCount + delay) / (currentCount + 1);

	            // 저장
	            TimeKeeper.getInstance().getLoopIdToCurrentAverage().put(loopId, newAverage);
	            TimeKeeper.getInstance().getLoopIdToCurrentNum().put(loopId, currentCount + 1);

	            //System.out.println("✅ [DEBUG] DISPLAY까지 도달한 Tuple의 Delay 기록됨 → loopId=" + loopId + ", delay=" + delay);
	            break;
	        }
	    }
	}


	@Override
	public void shutdownEntity() {
		
	}

	public int getGatewayDeviceId() {
		return gatewayDeviceId;
	}

	public void setGatewayDeviceId(int gatewayDeviceId) {
		this.gatewayDeviceId = gatewayDeviceId;
	}

	public GeoLocation getGeoLocation() {
		return geoLocation;
	}

	public void setGeoLocation(GeoLocation geoLocation) {
		this.geoLocation = geoLocation;
	}

	public int getUserId() {
		return userId;
	}

	public void setUserId(int userId) {
		this.userId = userId;
	}

	public String getAppId() {
		return appId;
	}

	public void setAppId(String appId) {
		this.appId = appId;
	}

	public String getActuatorType() {
		return actuatorType;
	}

	public void setActuatorType(String actuatorType) {
		this.actuatorType = actuatorType;
	}

	public Application getApp() {
		return app;
	}

	public void setApp(Application app) {
		this.app = app;
	}

	public double getLatency() {
		return latency;
	}

	public void setLatency(double latency) {
		this.latency = latency;
	}

}
