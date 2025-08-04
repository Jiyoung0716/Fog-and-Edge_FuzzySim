from azure.eventhub import EventHubConsumerClient
import json
import threading

# Azure IoT Hub → Event Hub-compatible connection string
EVENT_HUB_CONNECTION_STR = (
    "Endpoint=sb://ihsuprodlnres005dednamespace.servicebus.windows.net/;"
    "SharedAccessKeyName=iothubowner;"
    "SharedAccessKey=bKwhwXtlO5+rYaaUmIvLGZo+P4Pf+U0axAIoTK7FUEg=;"
    "EntityPath=iothub-ehub-x24142816-56818494-29bf6dd14e"
)

# Default consumer group name for Event Hub
CONSUMER_GROUP = "$Default"

# Shared list to store the latest messages (used in Streamlit)
latest_messages = []

# Callback when a new event/message is received
def on_event(partition_context, event):
    try:
        payload = json.loads(event.body_as_str())
        payload["timestamp"] = event.enqueued_time.isoformat()

        # Remove cluster/routing info if not Q2
        if payload.get("q_class") != "Q2":
            payload["cluster"] = ""
            payload["routing_target"] = ""

        latest_messages.append(payload)

        print(f"📥 Message received: {payload}")

        # Keep only the last 100 messages
        if len(latest_messages) > 100:
            latest_messages.pop(0)

    except Exception as e:
        print("❌ Error parsing message:", e)

# Start background thread for listening to Event Hub messages
def start_listener():
    print("🚀 Starting Azure Event Hub listener...")

    client = EventHubConsumerClient.from_connection_string(
        conn_str=EVENT_HUB_CONNECTION_STR,
        consumer_group=CONSUMER_GROUP
    )

    def run():
        with client:
            print("🔄 Listening from all partitions...")
            client.receive(
                on_event=on_event,
                starting_position="@latest"  # Only receive new messages
            )

    # Start listening in background thread (non-blocking)
    threading.Thread(target=run, daemon=True).start()
