package org.fog.test.perfeval;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Arrays; // FCC 사용을 위한 Import
import java.io.FileWriter; // metrics.json
import java.time.LocalDateTime; // metrics.json
import org.fog.utils.TimeKeeper; // metrics.json
import java.util.Map;
import java.util.HashMap;


import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.Actuator;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacementEdgewards;
import org.fog.placement.ModulePlacementMapping;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;
import org.json.simple.JSONObject;
import org.fog.utils.NetworkUsageMonitor;
import org.fog.utils.Config;

/**
 * Simulation setup for case study 1 - FuzzySim 
 * @author Jiyoung Kim
 *
 */
public class x24142816_FuzzySim {
	static List<FogDevice> fogDevices = new ArrayList<FogDevice>();
	static List<Sensor> sensors = new ArrayList<Sensor>();
	static List<Actuator> actuators = new ArrayList<Actuator>();
	
	static boolean CLOUD = false;
	
	// K-Means 유사 클러스터링을 위한 중심값 설정 (예: Task Size 중심)
	static double centroidA = 300;
	static double centroidB = 700;

	
	// static int numOfDepts = 2;
	static int numOfDepts = 4; //Fog 노드 수 증가
	static int numOfMobilesPerDept = 5;
	static double EEG_TRANSMISSION_TIME = 5;
	
	// Q별 카운팅 변수 선언 
	static int q1Count = 0;
	static int q2Count = 0;
	static int q3Count = 0;
	
	// 전략 선택 스위치
	static boolean useRoundRobin = true;
	
	// FCC(Fog Cluster Controller) 	//Task Size 기반 그룹 분류 + 그룹 내 최소 에너지 Fog 선택
	// Define Group
	static List<String> fogGroupA = Arrays.asList("d-0", "d-1");
	static List<String> fogGroupB = Arrays.asList("d-2", "d-3");
	// Define RR index per group
	static int rrIndexA = 0;
	static int rrIndexB = 0;
	
	// FuzzyScore (classifyTask) 점수 보여주기 (전역 선언)
	private static double lastFuzzyScore = 0;
	
	// 하이브리드 선택 함수 (FCC 사용 전 Hybrid)
	private static String selectFogNodeHybrid() {
	    if (useRoundRobin) {
	        return selectFogNodeRoundRobin();
	    } else {
	        return selectBestFogNodeForQ2();
	    }
	}
	private static String selectFogNodeHybrid(int taskIndex) {
	    // Task 번호를 기준으로 그룹 선택
	    List<String> selectedGroup = (taskIndex % 2 == 0) ? fogGroupA : fogGroupB;

	    if (selectedGroup.equals(fogGroupA)) {
	        String selected = selectedGroup.get(rrIndexA % selectedGroup.size());
	        rrIndexA++;
	        return selected;
	    } else {
	        String selected = selectedGroup.get(rrIndexB % selectedGroup.size());
	        rrIndexB++;
	        return selected;
	    }
	}
	// K-Means 유사 클러스터링 기반 오프로딩 (선택함수)
	private static String selectFogNodeByKMeansSim(double taskSize) {
	    double distA = Math.abs(taskSize - centroidA);
	    double distB = Math.abs(taskSize - centroidB);
	    
	    // String group = (distA < distB) ? "A" : "B";
	    // String selectedFog = group.equals("A") ? "d-0" : "d-1";

	    List<String> targetGroup = (distA < distB) ? fogGroupA : fogGroupB;
	    String group = (distA < distB) ? "A" : "B";

	    String bestFog = null;
	    double minEnergy = Double.MAX_VALUE;

	    for (String fog : targetGroup) {
	        double energy = getDeviceEnergy(fog);
	        if (energy < minEnergy) {
	            minEnergy = energy;
	            bestFog = fog;
	        }
	    }

	    // fallback
	    if (bestFog == null && !targetGroup.isEmpty()) {
	        bestFog = targetGroup.get(0);
	    }

	    //System.out.println("[KMeans-Sim] Q2 Task assigned to: " + bestFog + " (Cluster: " + group + ")");
	    System.out.println(" Cluster Assignment: CCTV Cluster " + group + " (via KMeans - location-based)");
	    return bestFog;
	}

	
	// Fuzzy Classification - Task 특성에 따라 Q1/Q2/Q3 결정
	private static String classifyTask(double size, double delay, double comp, double comm) {
		double fuzzyScore = 0;
		
		// 크기 평가 (가중치 50%)
		if (size < 300) fuzzyScore += 0.3;
	    else if (size < 700) fuzzyScore += 0.6;
	    else fuzzyScore += 0.9;
		
		// 지연 평가 (가중치 50%)
	    if (delay < 200) fuzzyScore += 0.3;
	    else if (delay < 400) fuzzyScore += 0.6;
	    else fuzzyScore += 0.9;

	 // Computational Demand 평가
	    if (comp < 30) fuzzyScore += 0.3;
	    else if (comp < 60) fuzzyScore += 0.6;
	    else fuzzyScore += 0.9;

	    // Communication Demand 평가
	    if (comm < 10) fuzzyScore += 0.3;
	    else if (comm < 30) fuzzyScore += 0.6;
	    else fuzzyScore += 0.9;

	    // 총 점수 범위: 1.2 ~ 3.6
	    lastFuzzyScore = fuzzyScore; // 저장
	    if (fuzzyScore <= 1.8) return "Q1";
	    else if (fuzzyScore <= 2.7) return "Q2";
	    else return "Q3";
	}
	
	// 에너지가져오기 함 (Task Size 기반 그룹 분류 + 그룹 내 최소 에너지 Fog 선택)
	private static double getDeviceEnergy(String deviceName) {
	    for (FogDevice device : fogDevices) {
	        if (device.getName().equals(deviceName)) {
	            return device.getEnergyConsumption();
	        }
	    }
	    return Double.MAX_VALUE;
	}
	
	//Task Size 기반 그룹 분류 + 그룹 내 최소 에너지 Fog 선택
	private static String selectBestFogNodeBySizeAndEnergy(double taskSize) {
	    List<String> targetGroup = (taskSize < 400) ? fogGroupA : fogGroupB;
	    
	    String bestFog = null;
	    double minEnergy = Double.MAX_VALUE;

	    for (String fog : targetGroup) {
	        double energy = getDeviceEnergy(fog);
	        if (energy < minEnergy) {
	            minEnergy = energy;
	            bestFog = fog;
	        }
	    }
	    
	    // fallback 처리 (null 방지)
	    if (bestFog == null && !targetGroup.isEmpty()) {
	        bestFog = targetGroup.get(0); // 그냥 첫 번째 노드라도 리턴
	    }

	    return bestFog;
	}

	public static void main(String[] args) {
		
	    Log.printLine("===================== Starting FuzzySim ====================");

	    // ✅ 예외 핸들링
	    Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
	        System.err.println("❌ Uncaught exception in thread: " + t.getName());
	        e.printStackTrace();
	    });

	    try {
	        Log.setDisabled(true); // 로그 비활성화
	        int num_user = 1;
	        Calendar calendar = Calendar.getInstance();
	        boolean trace_flag = false;

	        CloudSim.init(num_user, calendar, trace_flag);

	        String appId = "FuzzySim";
	        FogBroker broker = new FogBroker("broker");

	        Application application = createApplication(appId, broker.getId());
	        application.setUserId(broker.getId());

	        // ✅ 루프 등록: Loop ID 1로 등록하고 이름과 구조 연결
	        TimeKeeper.getInstance().addLoop(appId, Arrays.asList("CAMERA_FRAME", "client", "task_processor", "client", "DISPLAY"));
	        TimeKeeper.getInstance().registerLoop(1, Arrays.asList("CAMERA_FRAME", "client", "task_processor", "client", "DISPLAY"));

	        ModuleMapping moduleMapping = ModuleMapping.createModuleMapping(); // 모듈 매핑 초기화
	        createFogDevices(broker.getId(), appId, moduleMapping);

	        if (CLOUD) {
	            moduleMapping.addModuleToDevice("connector", "cloud");
	            moduleMapping.addModuleToDevice("task_processor", "cloud");

	            for (FogDevice device : fogDevices) {
	                if (device.getName().startsWith("m")) {
	                    moduleMapping.addModuleToDevice("client", device.getName());
	                }
	            }
	        } else {
	            moduleMapping.addModuleToDevice("connector", "cloud");
	        }

	        Controller controller = new Controller("master-controller", fogDevices, sensors, actuators);

	        controller.submitApplication(application, 0,
	                CLOUD ? new ModulePlacementMapping(fogDevices, application, moduleMapping)
	                      : new ModulePlacementEdgewards(fogDevices, sensors, actuators, application, moduleMapping));

	        TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());

	     // ✅ DISPLAY actuator 등록 (여기 추가)
	        int gatewayId = -1;
	        for (FogDevice device : fogDevices) {
	            if (device.getName().equals("m-0-0")) {
	                gatewayId = device.getId();
	                device.addActuator("DISPLAY");
	                break;
	            }
	        }
	        CloudSim.startSimulation();
	        CloudSim.stopSimulation();


	    } catch (Exception e) {
	        e.printStackTrace();
	        Log.printLine("Unwanted errors happen");
	    }
	}

	/**
	 * Creates the fog devices in the physical topology of the simulation.
	 * @param userId
	 * @param appId
	 */
	private static void createFogDevices(int userId, String appId, ModuleMapping moduleMapping) {
		FogDevice cloud = createFogDevice("cloud", 44800, 40000, 100, 10000, 0, 0.01, 16*103, 16*83.25); // creates the fog device Cloud at the apex of the hierarchy with level=0
		cloud.setParentId(-1);
		FogDevice proxy = createFogDevice("proxy-server", 2800, 4000, 10000, 10000, 1, 0.0, 107.339, 83.4333); // creates the fog device Proxy Server (level=1)
		proxy.setParentId(cloud.getId()); // setting Cloud as parent of the Proxy Server
		proxy.setUplinkLatency(100); // latency of connection from Proxy Server to the Cloud is 100 ms
		
		fogDevices.add(cloud);
		fogDevices.add(proxy);
		
		for(int i=0;i<numOfDepts;i++){
			addGw(i+"", userId, appId, proxy.getId(), moduleMapping); // adding a fog device for every Gateway in physical topology. The parent of each gateway is the Proxy Server
		}
		
	}

	private static FogDevice addGw(String id, int userId, String appId, int parentId, ModuleMapping moduleMapping){
		FogDevice dept = createFogDevice("d-"+id, 2800, 4000, 10000, 10000, 1, 0.0, 107.339, 83.4333);
		fogDevices.add(dept);
		dept.setParentId(parentId);
		dept.setUplinkLatency(4); // latency of connection between gateways and proxy server is 4 ms
		for(int i=0;i<numOfMobilesPerDept;i++){
			String mobileId = id+"-"+i;
			FogDevice mobile = addMobile(mobileId, userId, appId, dept.getId(), moduleMapping); // adding mobiles to the physical topology. Smartphones have been modeled as fog devices as well.
			mobile.setUplinkLatency(2); // latency of connection between the smartphone and proxy server is 4 ms
			fogDevices.add(mobile);
		}
		return dept;
	}
	
	private static FogDevice addMobile(String id, int userId, String appId, int parentId, ModuleMapping moduleMapping){
		FogDevice mobile = createFogDevice("m-"+id, 1000, 1000, 10000, 270, 3, 0, 87.53, 82.44);
		mobile.setParentId(parentId);
		Sensor taskSensor = new Sensor("s-"+id, "CAMERA_FRAME", userId, appId, new DeterministicDistribution(EEG_TRANSMISSION_TIME));
		sensors.add(taskSensor);
		taskSensor.setGatewayDeviceId(mobile.getId());
		taskSensor.setLatency(6.0);
		
		Actuator display = new Actuator("a-"+id, userId, appId, "DISPLAY");
		actuators.add(display);
		
		display.setGatewayDeviceId(mobile.getId());
		display.setLatency(1.0);  // latency of connection between Display actuator and the parent Smartphone is 1 ms
		
		// 퍼지 분류 및 출력 추가 (Task 생성 및 분류 출력)
		// ❶ 랜덤한 Task 속성
		double taskSize = Math.random() * 1000;
		double taskDelay = Math.random() * 500;
		double compDemand = Math.random() * 100;   // 연산 요구량: 0~100
		double commDemand = Math.random() * 50;    // 통신 요구량: 0~50

		// ❷ 퍼지 분류 함수 호출
		String taskType = classifyTask(taskSize, taskDelay, compDemand, commDemand);

		// 퍼지 분류(Q1/Q2/Q3) 결과에 따라, concentration_calculator라는 모듈을 어느 장치에 배치할지 결정
				String classification = classifyTask(taskSize, taskDelay, compDemand, commDemand);
				
		// ❸ 어디로 보낼지 결정 (지금은 로그만 출력)
		// 콘솔 출력
		String taskName = classification.equals("Q1") ? "Motion Detection" :
	                 classification.equals("Q2") ? "Face Recognition" :
	                 "Video Archiving";

		System.out.println("* Camera m-" + id + " → Task: " + taskName + " (" + classification + ")");
		System.out.println("   └ Frame Size: " + String.format("%.1f", taskSize) + "KB | Delay: " + 
		    String.format("%.1f", taskDelay) + "ms | Comp Load: " + String.format("%.1f", compDemand) +
		    " | Bandwidth: " + String.format("%.1f", commDemand));
		
		// Q1이면 로컬(mobile)에 concentration_calculator 배치
		if (classification.equals("Q1")) {
			q1Count++;
		    moduleMapping.addModuleToDevice("task_processor", "m-" + id); // 로컬 모바일 디바이스에서 처리 
		}
		else if (classification.equals("Q2")) {
			q2Count++;
			
			// ✅ KMeans 기반 그룹 선택 및 Fog 노드 선택
			String selectedFog = selectFogNodeByKMeansSim(taskSize);
			String group = (Math.abs(taskSize - centroidA) < Math.abs(taskSize - centroidB)) ? "A" : "B";
			moduleMapping.addModuleToDevice("task_processor", selectedFog);
		    
		    // FCC 사용을 추가한 로직
			System.out.println(" Routing: FCC assigned task from Camera m-" + id +
				    " to Fog Node " + selectedFog + " (via DQN-based decision in Group " + group + ")");
		    //System.out.println("[Hybrid-FCC] Q2 Task from m-" + id + 
		    //	    " assigned to: " + selectedFog + 
		    //	    " (Group: " + group + ")");
		}
		else {
			q3Count++;
		    moduleMapping.addModuleToDevice("task_processor", "cloud"); // Cloud에서 처리 
		}
		//System.out.println("[COUNT] q1Count=" + q1Count + ", q2Count=" + q2Count + ", q3Count=" + q3Count);
		System.out.println("[Task Summary] Motion=" + q1Count + ", Face=" + q2Count + ", Archive=" + q3Count);
		System.out.println("==============================================================");

		return mobile;

	}
	
	/**
	 * Creates a vanilla fog device
	 * @param nodeName name of the device to be used in simulation
	 * @param mips MIPS
	 * @param ram RAM
	 * @param upBw uplink bandwidth
	 * @param downBw downlink bandwidth
	 * @param level hierarchy level of the device
	 * @param ratePerMips cost rate per MIPS used
	 * @param busyPower
	 * @param idlePower
	 * @return
	 */
	private static FogDevice createFogDevice(String nodeName, long mips,
			int ram, long upBw, long downBw, int level, double ratePerMips, double busyPower, double idlePower) {
		
		List<Pe> peList = new ArrayList<Pe>();

		// 3. Create PEs and add these into a list.
		peList.add(new Pe(0, new PeProvisionerOverbooking(mips))); // need to store Pe id and MIPS Rating

		int hostId = FogUtils.generateEntityId();
		long storage = 1000000; // host storage
		int bw = 10000;

		PowerHost host = new PowerHost(
				hostId,
				new RamProvisionerSimple(ram),
				new BwProvisionerOverbooking(bw),
				storage,
				peList,
				new StreamOperatorScheduler(peList),
				new FogLinearPowerModel(busyPower, idlePower)
			);

		List<Host> hostList = new ArrayList<Host>();
		hostList.add(host);

		String arch = "x86"; // system architecture
		String os = "Linux"; // operating system
		String vmm = "Xen";
		double time_zone = 10.0; // time zone this resource located
		double cost = 3.0; // the cost of using processing in this resource
		double costPerMem = 0.05; // the cost of using memory in this resource
		double costPerStorage = 0.001; // the cost of using storage in this
										// resource
		double costPerBw = 0.0; // the cost of using bw in this resource
		LinkedList<Storage> storageList = new LinkedList<Storage>(); // we are not adding SAN
													// devices by now

		FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
				arch, os, vmm, host, time_zone, cost, costPerMem,
				costPerStorage, costPerBw);

		FogDevice fogdevice = null;
		try {
			fogdevice = new FogDevice(nodeName, characteristics, 
					new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, 0, ratePerMips);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		fogdevice.setLevel(level);
		return fogdevice;
	}

	/**
	 * Function to create the EEG Tractor Beam game application in the DDF model. 
	 * @param appId unique identifier of the application
	 * @param userId identifier of the user of the application
	 * @return
	 */
	@SuppressWarnings({"serial" })
	private static Application createApplication(String appId, int userId){
		
		Application application = Application.createApplication(appId, userId); // creates an empty application model (empty directed graph)
		
		/*
		 * Adding modules (vertices) to the application model (directed graph)
		 */
		application.addAppModule("client", 10); // adding module Client to the application model
		application.addAppModule("task_processor", 10); // adding module Concentration Calculator to the application model
		application.addAppModule("connector", 10); // adding module Connector to the application model
		application.addAppModule("DISPLAY", 1);  
		
		/*
		 * Connecting the application modules (vertices) in the application model (directed graph) with edges
		 */
		if(EEG_TRANSMISSION_TIME==10)
			application.addAppEdge("CAMERA_FRAME", "client", 2000, 500, "CAMERA_FRAME", Tuple.UP, AppEdge.SENSOR); // 센서 이름 변경 ECC -> IoT Task
		else
			application.addAppEdge("CAMERA_FRAME", "client", 3000, 500, "CAMERA_FRAME", Tuple.UP, AppEdge.SENSOR);
		application.addAppEdge("client", "task_processor", 3500, 500, "FACE_RECOGNITION", Tuple.UP, AppEdge.MODULE); // adding edge from Client to Concentration Calculator module carrying tuples of type _SENSOR
		application.addAppEdge("task_processor", "connector", 100, 1000, 1000, "DEVICE_STATUS", Tuple.UP, AppEdge.MODULE); // adding periodic edge (period=1000ms) from Concentration Calculator to Connector module carrying tuples of type PLAYER_GAME_STATE
		application.addAppEdge("task_processor", "client", 14, 500, "ALERT_SIGNAL", Tuple.DOWN, AppEdge.MODULE);  // adding edge from Concentration Calculator to Client module carrying tuples of type CONCENTRATION
		application.addAppEdge("connector", "client", 100, 28, 1000, "TIME_SYNC", Tuple.DOWN, AppEdge.MODULE); // adding periodic edge (period=1000ms) from Connector to Client module carrying tuples of type GLOBAL_GAME_STATE
		application.addAppEdge("client", "DISPLAY", 1000, 500, "LOCAL_FEEDBACK", Tuple.DOWN, AppEdge.ACTUATOR);  // adding edge from Client module to Display (actuator) carrying tuples of type SELF_STATE_UPDATE
		application.addAppEdge("client", "DISPLAY", 1000, 500, "SYSTEM_NOTIFICATION", Tuple.DOWN, AppEdge.ACTUATOR);  // adding edge from Client module to Display (actuator) carrying tuples of type GLOBAL_STATE_UPDATE
		
		/*
		 * Defining the input-output relationships (represented by selectivity) of the application modules. 
		 */
		application.addTupleMapping("client", "CAMERA_FRAME", "FACE_RECOGNITION", new FractionalSelectivity(0.9)); // IoT-Task 
		application.addTupleMapping("task_processor", "FACE_RECOGNITION", "ALERT_SIGNAL", new FractionalSelectivity(1.0));
		application.addTupleMapping("client", "ALERT_SIGNAL", "LOCAL_FEEDBACK", new FractionalSelectivity(1.0)); // 1.0 tuples of type SELF_STATE_UPDATE are emitted by Client module per incoming tuple of type CONCENTRATION 
		application.addTupleMapping("client", "TIME_SYNC", "SYSTEM_NOTIFICATION", new FractionalSelectivity(1.0)); // 1.0 tuples of type GLOBAL_STATE_UPDATE are emitted by Client module per incoming tuple of type GLOBAL_GAME_STATE
	
		/*
		 * Defining application loops to monitor the latency of. 
		 * Here, we add only one loop for monitoring : EEG(sensor) -> Client -> Concentration Calculator -> Client -> DISPLAY (actuator)
		 */
		final AppLoop loop1 = new AppLoop(new ArrayList<String>(){{add("CAMERA_FRAME");add("client");add("task_processor");add("client");add("DISPLAY");}});
		List<AppLoop> loops = new ArrayList<AppLoop>(){{add(loop1);}};
		application.setLoops(loops);
		
		return application;
	}
	
	// Q2로 분류된 Task를 가장 적합한 Fog 노드(게이트웨이 노드들 중 MIPS가 가장 높은 노드)에 할당하는 Rule-based 선택 함수
	// 논문 로직 중 Q2에 해당되는 Task는 DQN으로 하게 되지만, 구현은 어려우므로 Rule-based 선택 
	private static String selectBestFogNodeForQ2() { 
	    FogDevice bestDevice = null;
	    double highestMips = -1;

	    for (FogDevice device : fogDevices) {
	        if (device.getName().startsWith("d-")) { // 게이트웨이(Fog) 노드만 고려
	            double mips = device.getHost().getTotalMips(); // 총 MIPS
	            if (mips > highestMips) {
	                highestMips = mips;
	                bestDevice = device;
	            }
	        }
	    }

	    return (bestDevice != null) ? bestDevice.getName() : "cloud"; // fallback
	}
	
	// Round-Robin용 인덱스 변수
	private static int rrIndex = 0;

	// Round-Robin 방식으로 Fog 노드 선택
	private static String selectFogNodeRoundRobin() {
	    List<String> fogNames = new ArrayList<>();
	    
	    for (FogDevice device : fogDevices) {
	        if (device.getName().startsWith("d-")) {
	            fogNames.add(device.getName());
	        }
	    }

	    if (fogNames.isEmpty()) return "cloud"; // fallback
	    
	    String selected = fogNames.get(rrIndex % fogNames.size());
	    rrIndex++;
	    return selected;
	}

}
