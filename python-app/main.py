import random
import time
from datetime import datetime, timezone
from azure.iot.device import IoTHubDeviceClient, Message

# Azure IoT Hub connection string
CONNECTION_STRING = "HostName=x24142816-iothub.azure-devices.net;DeviceId=myDevice1;SharedAccessKey=URef7ee6mJxrgguwok03O6Ao+hoixR9Gj2xcQwxhtUc="

# Message format to be sent to IoT Hub
MSG_TXT = '''{{
  "timestamp": "{timestamp}",
  "camera_id": "{camera_id}",
  "task_type": "{task_type}",
  "q_class": "{q_class}",
  "frame_size_kb": {frame_size_kb},
  "delay_ms": {delay_ms},
  "comp_load": {comp_load},
  "bandwidth": {bandwidth},
  "cluster": "{cluster}",
  "routing_target": "{routing_target}"
}}'''

# Initialize IoT Hub client
def iothub_client_init():
    return IoTHubDeviceClient.create_from_connection_string(CONNECTION_STRING)

# Simulate a task using fuzzy logic classification
def simulate_fuzzy_task():
    camera_id = random.choice([f"m-{i}-{j}" for i in range(4) for j in range(5)])
    timestamp = datetime.now(timezone.utc).isoformat()

    frame_size_kb = round(random.uniform(100, 900), 1)
    delay_ms = round(random.uniform(10, 500), 1)
    comp_load = round(random.uniform(1, 100), 1)
    bandwidth = round(random.uniform(1, 50), 1)

    # Calculate fuzzy score based on task attributes
    fuzzy_score = (
        (0.3 if frame_size_kb < 300 else 0.6 if frame_size_kb < 700 else 0.9) +
        (0.3 if delay_ms < 200 else 0.6 if delay_ms < 400 else 0.9) +
        (0.3 if comp_load < 30 else 0.6 if comp_load < 60 else 0.9) +
        (0.3 if bandwidth < 10 else 0.6 if bandwidth < 30 else 0.9)
    )

    # Classify Q-type and determine routing target
    if fuzzy_score <= 1.8:
        q_class = "Q1"
        task_type = "Motion Detection"
        cluster = "-"
        routing = "Local"

    elif fuzzy_score <= 2.7:
        q_class = "Q2"
        task_type = "Face Recognition"
        
        # Select cluster A or B based on proximity to task size centroids
        if abs(frame_size_kb - 300) < abs(frame_size_kb - 700):
            cluster = "A"
            routing = random.choice(["d-0", "d-1"])  # Cluster A
        else:
            cluster = "B"
            routing = random.choice(["d-2", "d-3"])  # Cluster B

    else:
        q_class = "Q3"
        task_type = "Video Archiving"
        cluster = "-"
        routing = "Cloud"

    return {
        "timestamp": timestamp,
        "camera_id": camera_id,
        "task_type": task_type,
        "q_class": q_class,
        "frame_size_kb": frame_size_kb,
        "delay_ms": delay_ms,
        "comp_load": comp_load,
        "bandwidth": bandwidth,
        "cluster": cluster,
        "routing_target": routing
    }

# Run task simulation and continuously send messages to Azure IoT Hub
def run_simulation():
    client = iothub_client_init()
    print("🚀 FuzzySim IoT Device started (sending D2C messages...)")

    try:
        while True:
            task = simulate_fuzzy_task()
            msg = MSG_TXT.format(**task)
            message = Message(msg)

            # Add custom property for alert if Q3 (archiving)
            if task["q_class"] == "Q3":
                message.custom_properties["alert"] = "true"

            print("📡 Sending message:\n", msg)
            client.send_message(message)
            print("✅ Message sent!\n")
            time.sleep(3)

    except KeyboardInterrupt:
        print("🛑 Simulation stopped.")

if __name__ == '__main__':
    run_simulation()
