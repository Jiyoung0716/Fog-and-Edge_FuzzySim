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
import java.io.PrintStream; // storing log.txt
import java.io.FileOutputStream; // storing log.txt



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
	
	static boolean CLOUD = true;
	
	// Setting Centroids for K-Means Similarity Clustering (e.g., Task Size Centroid)
	// [Paper Reference] Centroids for KMeans similarity clustering (Group A/B distinction)
	static double centroidA = 300;
	static double centroidB = 700;
	
	// static int numOfDepts = 2;
	static int numOfDepts = 4; //Fog 노드 수 증가
	static int numOfMobilesPerDept = 5;
	static double EEG_TRANSMISSION_TIME = 5;
	
	// Defined counting variable per Queues 
	// [Paper Reference] it helps to keep track three layer work (Motion / Face / Archive)
	static int q1Count = 0;
	static int q2Count = 0;
	static int q3Count = 0;
	
	// FCC(Fog Cluster Controller)
	// Define Group
	static List<String> fogGroupA = Arrays.asList("d-0", "d-1");
	static List<String> fogGroupB = Arrays.asList("d-2", "d-3");
	
	// FuzzyScore (classifyTask)
	private static double lastFuzzyScore = 0;
	
	/**
	 * [Paper Reference] Section IV.B – Fog Layer: K-Means + DQN Approach
	 * Selects a fog node for Q2 tasks using a K-Means-like method based on task size.
	 * Chooses the node with the lowest energy consumption within the selected group.
	 * Simplifies the paper’s DQN-based selection into a lightweight, energy-aware strategy.
	 */
	private static String selectFogNodeByKMeansSim(double taskSize) {
	    double distA = Math.abs(taskSize - centroidA);
	    double distB = Math.abs(taskSize - centroidB);

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
	    System.out.println(" Cluster Assignment: CCTV Cluster " + group + " (via KMeans - location-based)");
	    return bestFog;
	}

	
	/**
	 * [Paper Reference] Algorithm 2 - Offloading Decisions Using Fuzzy Logic Algorithm
	 * Fuzzy logic-based task classification function.
	 * Calculates a fuzzy score based on four input parameters:
	 * Task size, Delay tolerance, Computational demand, Communication demand
	 * Based on the score, it is classified into one of the three queues: Q1(local), Q2(Fog nodes), Q3(Cloud)
	 */
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
	
	/**
	 * [Paper Reference] Section IV.B - State-Aware Fog Node Selection
	 *  Returns the current energy consumption of a specific FogDevice.
	 * This method is used by selectFogNodeByKMeansSim() to identify the fog node
	 */
	private static double getDeviceEnergy(String deviceName) {
	    for (FogDevice device : fogDevices) {
	        if (device.getName().equals(deviceName)) {
	            return device.getEnergyConsumption();
	        }
	    }
	    return Double.MAX_VALUE;
	}

	/**
	 * [[Paper Reference] Section V – Performance Evaluation
	 * Initializes and runs the simulation environment for evaluating the proposed offloading strategy.
	 * Task routing decisions and classification logs are printed during runtime.
	 * @param args
	 */
	public static void main(String[] args) {
		
	    Log.printLine("===================== Starting FuzzySim ====================");
	    
	    String filename = CLOUD ? "result_centralized.txt" : "result_distributed.txt";
	    //PrintStream fileOut = null;
	    final PrintStream[] fileOut = new PrintStream[1]; // 배열로 선언하면 effectively final
	    try {
	    	final PrintStream originalOut = System.out;
	    	fileOut[0] = new PrintStream(new FileOutputStream(filename, false)); // false = 덮어쓰기
	        PrintStream multiOut = new PrintStream(new java.io.OutputStream() {
	            @Override
	            public void write(int b) {
	            	originalOut.write(b); // 콘솔에도 출력
	                fileOut[0].write(b);    // 파일에도 출력
	            }

	            @Override
	            public void flush() {
	            	originalOut.flush();
	                fileOut[0].flush();
	            }
	        });
	        System.setOut(multiOut); // 표준 출력 변경
	        System.setErr(multiOut); // 에러 출력도 동일하게 저장
	    } catch (Exception e) {
	        e.printStackTrace();
	        return;
	    }

	    // 예외 핸들링
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

	        // 루프 등록: Loop ID 1로 등록하고 이름과 구조 연결
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

	     // DISPLAY actuator 등록 (여기 추가)
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
	        // ✅ [2] 파일 출력 닫기
	        fileOut[0].close();


	    } catch (Exception e) {
	        e.printStackTrace();
	        Log.printLine("Unwanted errors happen");
	    }
	}

	/**
	 * [Paper Reference] Section III – Proposed System Architecture
	 * The structure follows the proposed model: Cloud → Proxy → Fog → Mobile
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
	/**
	 * [Paper Reference] Section IV.A – IoT Layer: Task Generation and Fuzzy Classification
	  * This method creates a mobile Fog device (m-x-x) representing an IoT node (e.g., smart camera),
	  * attaches a camera sensor and display actuator to it, and generates a simulated task with random attributes.
	  * The task is classified using a fuzzy logic-based method into one of three categories:
	  *   - Q1: Processed locally on the device (low demand, low delay)
	  *   - Q2: Offloaded to a fog node (moderate demand)
	  *   - Q3: Offloaded to the cloud (high demand or tolerant to delay)
	 */
	private static FogDevice addMobile(String id, int userId, String appId, int parentId, ModuleMapping moduleMapping){
		// Create the mobile fog device with limited resources
		FogDevice mobile = createFogDevice("m-"+id, 1000, 1000, 10000, 270, 3, 0, 87.53, 82.44);
		mobile.setParentId(parentId);
		
		// Attach a CAMERA_FRAME sensor (e.g., camera feed)
		Sensor taskSensor = new Sensor("s-"+id, "CAMERA_FRAME", userId, appId, new DeterministicDistribution(EEG_TRANSMISSION_TIME));
		sensors.add(taskSensor);
		taskSensor.setGatewayDeviceId(mobile.getId());
		taskSensor.setLatency(6.0);
		
		// Attach a DISPLAY actuator (e.g., screen output)
		Actuator display = new Actuator("a-"+id, userId, appId, "DISPLAY");
		actuators.add(display);
		display.setGatewayDeviceId(mobile.getId());
		display.setLatency(1.0);  // latency of connection between Display actuator and the parent Smartphone is 1 ms
		
		// 퍼지 분류 및 출력 추가 (Task 생성 및 분류 출력)
		// Randomly generate task parameters (to simulate heterogeneous IoT tasks)
		double taskSize = Math.random() * 1000;
		double taskDelay = Math.random() * 500;
		double compDemand = Math.random() * 100;   // 연산 요구량: 0~100
		double commDemand = Math.random() * 50;    // 통신 요구량: 0~50

		// 퍼지 분류(Q1/Q2/Q3) 결과에 따라, concentration_calculator라는 모듈을 어느 장치에 배치할지 결정
		// Classify task using fuzzy logic
		String classification = classifyTask(taskSize, taskDelay, compDemand, commDemand);
				
		// Human-readable task type for logging
		String taskName = classification.equals("Q1") ? "Motion Detection" :
	                 classification.equals("Q2") ? "Face Recognition" :
	                 "Video Archiving";

		System.out.println("* Camera m-" + id + " → Task: " + taskName + " (" + classification + ")");
		System.out.println("   └ Frame Size: " + String.format("%.1f", taskSize) + "KB | Delay: " + 
		    String.format("%.1f", taskDelay) + "ms | Comp Load: " + String.format("%.1f", compDemand) +
		    " | Bandwidth: " + String.format("%.1f", commDemand));
		
		// Module assignment based on classification
		if (classification.equals("Q1")) {
			// Q1 → Process locally on the mobile device
			q1Count++;
		    moduleMapping.addModuleToDevice("task_processor", "m-" + id); // 로컬 모바일 디바이스에서 처리 
		}
		else if (classification.equals("Q2")) {
			// Q2 → Offload to selected fog node using KMeans-like logic
			q2Count++;
			
			// KMeans 기반 그룹 선택 및 Fog 노드 선택
			String selectedFog = selectFogNodeByKMeansSim(taskSize);
			String group = (Math.abs(taskSize - centroidA) < Math.abs(taskSize - centroidB)) ? "A" : "B";
			moduleMapping.addModuleToDevice("task_processor", selectedFog);
		    
		    // FCC 사용을 추가한 로직
			// Logging FCC decision (group-based selection)
			System.out.println(" Routing: FCC assigned task from Camera m-" + id +
				    " to Fog Node " + selectedFog + " (via DQN-based decision in Group " + group + ")");
		}
		else {
			// Q3 → Offload to cloud (high demand / delay-tolerant)
			q3Count++;
		    moduleMapping.addModuleToDevice("task_processor", "cloud"); // Cloud에서 처리 
		}
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
	 * [Paper Reference] Corresponds to Algorithm 1 - Application Preparation
	 * 
	 * This method defines the structure of the IoT application by:
	 * - Adding application modules (vertices)
	 * - Defining data flow between modules (edges)
	 * - Specifying tuple transformation ratios (selectivity)
	 * - Registering loops for latency monitoring
	 */
	private static Application createApplication(String appId, int userId){

	    // Create an empty application (as a directed graph)
	    Application application = Application.createApplication(appId, userId);

	    /*
	     * 1. Add modules (vertices) to the application
	     * Modules represent logical units of computation.
	     */
	    application.addAppModule("client", 10);           // Initial processing module on the IoT device
	    application.addAppModule("task_processor", 10);   // Core processing module (e.g., Face Recognition, Motion Detection)
	    application.addAppModule("connector", 10);        // Handles periodic synchronization/status communication
	    application.addAppModule("DISPLAY", 1);           // Actuator module for user output

	    /*
	     * 2. Define data flow between modules (edges)
	     * Tuples carry data between modules; direction, size, bandwidth, and type are specified.
	     */
	    if (EEG_TRANSMISSION_TIME == 10)
	        application.addAppEdge("CAMERA_FRAME", "client", 2000, 500, "CAMERA_FRAME", Tuple.UP, AppEdge.SENSOR);
	    else
	        application.addAppEdge("CAMERA_FRAME", "client", 3000, 500, "CAMERA_FRAME", Tuple.UP, AppEdge.SENSOR);

	    application.addAppEdge("client", "task_processor", 3500, 500, "FACE_RECOGNITION", Tuple.UP, AppEdge.MODULE);
	    application.addAppEdge("task_processor", "connector", 100, 1000, 1000, "DEVICE_STATUS", Tuple.UP, AppEdge.MODULE);
	    application.addAppEdge("task_processor", "client", 14, 500, "ALERT_SIGNAL", Tuple.DOWN, AppEdge.MODULE);
	    application.addAppEdge("connector", "client", 100, 28, 1000, "TIME_SYNC", Tuple.DOWN, AppEdge.MODULE);
	    application.addAppEdge("client", "DISPLAY", 1000, 500, "LOCAL_FEEDBACK", Tuple.DOWN, AppEdge.ACTUATOR);
	    application.addAppEdge("client", "DISPLAY", 1000, 500, "SYSTEM_NOTIFICATION", Tuple.DOWN, AppEdge.ACTUATOR);

	    /*
	     * 3. Define tuple mapping (selectivity model)
	     * Specifies the transformation ratio from input to output tuples in each module.
	     */
	    application.addTupleMapping("client", "CAMERA_FRAME", "FACE_RECOGNITION", new FractionalSelectivity(0.9));
	    application.addTupleMapping("task_processor", "FACE_RECOGNITION", "ALERT_SIGNAL", new FractionalSelectivity(1.0));
	    application.addTupleMapping("client", "ALERT_SIGNAL", "LOCAL_FEEDBACK", new FractionalSelectivity(1.0));
	    application.addTupleMapping("client", "TIME_SYNC", "SYSTEM_NOTIFICATION", new FractionalSelectivity(1.0));

	    /*
	     * 4. Define application loop for latency monitoring
	     * Loop: CAMERA_FRAME → client → task_processor → client → DISPLAY
	     * Used to track end-to-end latency across this path.
	     */
	    final AppLoop loop1 = new AppLoop(new ArrayList<String>() {{
	        add("CAMERA_FRAME");
	        add("client");
	        add("task_processor");
	        add("client");
	        add("DISPLAY");
	    }});
	    List<AppLoop> loops = new ArrayList<AppLoop>() {{ add(loop1); }};
	    application.setLoops(loops);

	    return application;
	}


}
